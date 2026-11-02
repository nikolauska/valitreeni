package app.fittracker.update

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.net.toUri
import app.fittracker.BuildConfig
import app.fittracker.data.SettingsStore
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release) : UpdateState
    /** Android only lets an app install APKs after the user allows it once in system settings. */
    data class NeedsPermission(val release: Release) : UpdateState
    data class Downloading(val release: Release, val percent: Int) : UpdateState
    /** Android wants the user to confirm; the UI starts [intent] because only a visible screen may open it. */
    data class Confirm(val release: Release, val intent: Intent) : UpdateState
    data class Installing(val release: Release) : UpdateState
    data class Failed(val release: Release?, val reason: FailReason) : UpdateState
}

enum class FailReason { OFFLINE, DOWNLOAD, DAMAGED, INSTALL }

private const val TIMEOUT_MS = 15_000
private const val POLL_MS = 300L
private const val APK_MIME = "application/vnd.android.package-archive"

class Updater(private val context: Context, private val settings: SettingsStore, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private var checkedOnOpen = false

    /**
     * Runs from the activity, not Application.onCreate: reminder alarms also start the process,
     * and those must not go online. Once per process keeps it to roughly once per day of use.
     */
    fun checkOnOpen() {
        if (checkedOnOpen) return
        checkedOnOpen = true
        removeOldDownloads()
        scope.launch { if (settings.current().autoUpdateCheck) check(quiet = true) }
    }

    fun checkNow() {
        scope.launch { check(quiet = false) }
    }

    private suspend fun check(quiet: Boolean) {
        if (busy()) return
        _state.value = UpdateState.Checking
        _state.value = try {
            val latest = fetchLatest()
            if (latest != null && latest.versionCode > BuildConfig.VERSION_CODE) UpdateState.Available(latest) else UpdateState.UpToDate
        } catch (_: IOException) {
            // An automatic check while offline is not worth a warning; the next app start tries again.
            if (quiet) UpdateState.Idle else UpdateState.Failed(null, FailReason.OFFLINE)
        } catch (_: JSONException) {
            if (quiet) UpdateState.Idle else UpdateState.Failed(null, FailReason.OFFLINE)
        }
    }

    private fun busy() = _state.value.let { it is UpdateState.Downloading || it is UpdateState.Confirm || it is UpdateState.Installing }

    private suspend fun fetchLatest(): Release? = withContext(Dispatchers.IO) {
        val conn = URL(BuildConfig.UPDATE_URL).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            when (conn.responseCode) {
                // No release published yet.
                HttpURLConnection.HTTP_NOT_FOUND -> null
                HttpURLConnection.HTTP_OK -> parseRelease(conn.inputStream.bufferedReader().use { it.readText() }, allowHttp = BuildConfig.DEBUG)
                else -> throw IOException("HTTP ${conn.responseCode}")
            }
        } finally {
            conn.disconnect()
        }
    }

    fun install(release: Release) {
        if (busy()) return
        if (!context.packageManager.canRequestPackageInstalls()) {
            _state.value = UpdateState.NeedsPermission(release)
            return
        }
        _state.value = UpdateState.Downloading(release, 0)
        scope.launch(Dispatchers.IO) { _state.value = download(release) }
    }

    /** The user came back from the "install unknown apps" setting; carry on if they allowed it. */
    fun resumeAfterPermission() {
        val s = _state.value as? UpdateState.NeedsPermission ?: return
        if (context.packageManager.canRequestPackageInstalls()) install(s.release)
    }

    /**
     * Android's DownloadManager fetches the file and Android's own installer installs it, the way a browser
     * download would. Streaming the APK into a PackageInstaller session from inside the app is the pattern
     * malware droppers use, and Play Protect judged the app harmful while it did that.
     */
    private suspend fun download(release: Release): UpdateState {
        val downloads = context.getSystemService(DownloadManager::class.java)
        val name = "valitreeni-${release.versionName}.apk"
        removeOldDownloads()
        val id = downloads.enqueue(
            DownloadManager.Request(release.apkUrl.toUri())
                .setTitle(name)
                .setMimeType(APK_MIME)
                // The app's own folder needs no storage permission and isn't shown in the user's Downloads.
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, name),
        )
        while (true) {
            val (status, done, total) = downloads.query(DownloadManager.Query().setFilterById(id)).use { c ->
                if (!c.moveToFirst()) return UpdateState.Failed(release, FailReason.DOWNLOAD)
                Triple(
                    c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                    c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                    c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)).takeIf { it > 0 } ?: release.size,
                )
            }
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> break
                DownloadManager.STATUS_FAILED -> {
                    downloads.remove(id)
                    return UpdateState.Failed(release, FailReason.DOWNLOAD)
                }
            }
            val percent = if (total > 0) (done * 100 / total).toInt().coerceIn(0, 100) else 0
            _state.value = UpdateState.Downloading(release, percent)
            delay(POLL_MS)
        }
        val uri = downloads.getUriForDownloadedFile(id) ?: return UpdateState.Failed(release, FailReason.DOWNLOAD)
        if (release.sha256 != null && sha256Of(uri) != release.sha256) {
            downloads.remove(id)
            return UpdateState.Failed(release, FailReason.DAMAGED)
        }
        // Deprecated only in favour of PackageInstaller sessions, which is exactly what this avoids;
        // EXTRA_RETURN_RESULT lets the UI tell a cancel from a failure.
        @Suppress("DEPRECATION")
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .putExtra(Intent.EXTRA_RETURN_RESULT, true)
        return UpdateState.Confirm(release, intent)
    }

    private fun sha256Of(uri: Uri): String? = try {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        } ?: return null
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (_: IOException) {
        null
    }

    /** An installed update's APK is dead weight; also keeps DownloadManager from renaming the next one to "-1.apk". */
    private fun removeOldDownloads() {
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.listFiles { f -> f.name.endsWith(".apk") }?.forEach { it.delete() }
    }

    /** The UI has opened Android's installer; don't open it again on the next recomposition. */
    fun confirmShown() {
        (_state.value as? UpdateState.Confirm)?.let { _state.value = UpdateState.Installing(it.release) }
    }

    /** What Android's installer answered. On success Android restarts the app, so this rarely sees it. */
    fun onInstallResult(resultCode: Int) {
        val release = (_state.value as? UpdateState.Installing)?.release ?: return
        _state.value = when (resultCode) {
            Activity.RESULT_OK -> UpdateState.UpToDate
            Activity.RESULT_CANCELED -> UpdateState.Available(release)
            else -> UpdateState.Failed(release, FailReason.INSTALL)
        }
    }
}
