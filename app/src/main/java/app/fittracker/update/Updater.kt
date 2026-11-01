package app.fittracker.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import app.fittracker.BuildConfig
import app.fittracker.data.SettingsStore
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

class Updater(private val context: Context, private val settings: SettingsStore, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private var checkedOnOpen = false

    /** The release being installed, so results from the system can be shown against it. */
    private var pending: Release? = null

    /**
     * Runs from the activity, not Application.onCreate: reminder alarms also start the process,
     * and those must not go online. Once per process keeps it to roughly once per day of use.
     */
    fun checkOnOpen() {
        if (checkedOnOpen) return
        checkedOnOpen = true
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
        scope.launch(Dispatchers.IO) { _state.value = downloadAndCommit(release) }
    }

    /** The user came back from the "install unknown apps" setting; carry on if they allowed it. */
    fun resumeAfterPermission() {
        val s = _state.value as? UpdateState.NeedsPermission ?: return
        if (context.packageManager.canRequestPackageInstalls()) install(s.release)
    }

    private fun downloadAndCommit(release: Release): UpdateState {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            // Once this app has installed the running version, Android may update without asking again.
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            if (release.size > 0) setSize(release.size)
        }
        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val conn = URL(release.apkUrl).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = TIMEOUT_MS
                if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${conn.responseCode}")
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.size
                // Streams straight into the installer: no copy of the APK is left lying in the app's storage.
                conn.inputStream.use { input ->
                    session.openWrite("base.apk", 0, if (total > 0) total else -1).use { out ->
                        val buffer = ByteArray(64 * 1024)
                        var written = 0L
                        var shown = 0
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            digest.update(buffer, 0, n)
                            written += n
                            val percent = if (total > 0) (written * 100 / total).toInt().coerceAtMost(100) else 0
                            if (percent != shown) {
                                shown = percent
                                _state.value = UpdateState.Downloading(release, percent)
                            }
                        }
                        session.fsync(out)
                    }
                }
            } finally {
                conn.disconnect()
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (release.sha256 != null && actual != release.sha256) {
                session.abandon()
                return UpdateState.Failed(release, FailReason.DAMAGED)
            }
            pending = release
            session.commit(InstallResultReceiver.statusReceiver(context, sessionId))
            session.close()
            return UpdateState.Installing(release)
        } catch (_: IOException) {
            session.abandon()
            return UpdateState.Failed(release, FailReason.DOWNLOAD)
        }
    }

    internal fun onNeedsConfirm(intent: Intent) {
        pending?.let { _state.value = UpdateState.Confirm(it, intent) }
    }

    /** The UI has shown the system dialog; don't open it again on the next recomposition. */
    fun confirmShown() {
        (_state.value as? UpdateState.Confirm)?.let { _state.value = UpdateState.Installing(it.release) }
    }

    internal fun onCancelled() {
        pending?.let { _state.value = UpdateState.Available(it) }
    }

    internal fun onFailed() {
        _state.value = UpdateState.Failed(pending, FailReason.INSTALL)
    }

    internal fun onInstalled() {
        // Usually unseen: Android restarts the app with the new version.
        _state.value = UpdateState.UpToDate
    }
}
