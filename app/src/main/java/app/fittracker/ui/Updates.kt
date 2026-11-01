package app.fittracker.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fittracker.FitApp
import app.fittracker.R
import app.fittracker.update.FailReason
import app.fittracker.update.UpdateState
import app.fittracker.update.Updater

@Composable
fun rememberUpdater(): Updater = (LocalContext.current.applicationContext as FitApp).updater

/**
 * Lives at the top of the UI so the install confirmation opens whichever screen is showing,
 * and so returning from the "install unknown apps" setting carries on with the update.
 */
@Composable
fun UpdateEffects() {
    val context = LocalContext.current
    val updater = rememberUpdater()
    val state by updater.state.collectAsStateWithLifecycle()
    LaunchedEffect(state) {
        (state as? UpdateState.Confirm)?.let {
            context.startActivity(it.intent)
            updater.confirmShown()
        }
    }
    LifecycleResumeEffect(Unit) {
        updater.resumeAfterPermission()
        onPauseOrDispose { }
    }
}

@Composable
fun updateStatusText(state: UpdateState): String? = when (state) {
    UpdateState.Idle -> null
    UpdateState.Checking -> stringResource(R.string.settings_update_checking)
    UpdateState.UpToDate -> stringResource(R.string.settings_update_current)
    is UpdateState.Available -> stringResource(R.string.update_available, state.release.versionName)
    is UpdateState.NeedsPermission -> stringResource(R.string.update_permission)
    is UpdateState.Downloading -> stringResource(R.string.update_downloading, state.percent)
    is UpdateState.Confirm, is UpdateState.Installing -> stringResource(R.string.update_installing)
    is UpdateState.Failed -> stringResource(
        when (state.reason) {
            FailReason.OFFLINE -> R.string.update_failed_offline
            FailReason.DOWNLOAD -> R.string.update_failed_download
            FailReason.DAMAGED -> R.string.update_failed_damaged
            FailReason.INSTALL -> R.string.update_failed_install
        },
    )
}

class UpdateAction(val label: String, val run: () -> Unit)

/** The one button that moves the update forward from [state], or null while there's nothing to press. */
@Composable
fun updateAction(state: UpdateState, updater: Updater): UpdateAction? {
    val context = LocalContext.current
    return when (state) {
        is UpdateState.Available -> UpdateAction(stringResource(R.string.update_install)) { updater.install(state.release) }
        is UpdateState.NeedsPermission -> UpdateAction(stringResource(R.string.update_allow)) { openInstallPermission(context) }
        is UpdateState.Failed -> UpdateAction(stringResource(R.string.update_retry)) {
            // A damaged file may mean the release was replaced on GitHub, so look it up again instead of re-downloading the same one.
            val release = state.release
            if (release == null || state.reason == FailReason.DAMAGED) updater.checkNow() else updater.install(release)
        }
        else -> null
    }
}

/** Opens this app's own "install unknown apps" switch, so the user doesn't have to hunt for it. */
private fun openInstallPermission(context: Context) {
    context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri()))
}

/** Today only shows an update once there is one; checking and "up to date" belong in Settings. */
fun UpdateState.showOnToday(): Boolean = when (this) {
    UpdateState.Idle, UpdateState.Checking, UpdateState.UpToDate -> false
    is UpdateState.Failed -> release != null
    else -> true
}
