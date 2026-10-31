package app.fittracker.ui.today

import android.content.Context
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect

data class SystemStatus(val notificationsEnabled: Boolean, val batteryUnrestricted: Boolean)

fun readSystemStatus(context: Context): SystemStatus {
    val power = context.getSystemService(PowerManager::class.java)
    return SystemStatus(
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        batteryUnrestricted = power.isIgnoringBatteryOptimizations(context.packageName),
    )
}

/** Re-read whenever the screen resumes, because the user fixes these in system settings and comes back. */
@Composable
fun rememberSystemStatus(): SystemStatus {
    val context = LocalContext.current
    var status by remember { mutableStateOf(readSystemStatus(context)) }
    LifecycleResumeEffect(Unit) {
        status = readSystemStatus(context)
        onPauseOrDispose { }
    }
    return status
}
