package app.fittracker.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.fittracker.MainActivity
import app.fittracker.R
import app.fittracker.data.DayView
import app.fittracker.ui.exerciseNameText
import app.fittracker.ui.setAmountText

object Notifications {
    private const val CHANNEL_REMINDERS = "reminders"
    private const val ROUND_NOTIFICATION_ID = 1

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_REMINDERS,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = context.getString(R.string.notif_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun showRound(context: Context, day: DayView) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        ensureChannel(context)
        val round = day.round
        val byId = day.focus.associateBy { it.exercise.id }
        val res = context.resources
        val lines = round.items.mapNotNull { item ->
            byId[item.exerciseId]?.let { context.getString(R.string.notif_round_line, exerciseNameText(context, it.exercise), setAmountText(context, it.exercise, item.amount)) }
        }
        val title = context.getString(
            R.string.notif_round_title,
            round.roundNumber,
            res.getQuantityString(R.plurals.round_moves, round.items.size, round.items.size),
            round.estimatedMinutes,
        )
        val summary = context.getString(R.string.notif_round_summary, round.roundNumber, round.totalRounds)
        val style = NotificationCompat.InboxStyle().setSummaryText(summary)
        lines.forEach(style::addLine)

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_move)
            .setColor(ContextCompat.getColor(context, R.color.brand_accent))
            .setContentTitle(title)
            .setContentText(lines.joinToString(" · "))
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openRoundIntent(context))
            .addAction(
                0,
                context.getString(R.string.notif_done_round),
                // Only the day travels with the button; the sets are worked out when it is tapped.
                actionIntent(context, ReminderActionReceiver.ACTION_DONE_ROUND, 10) {
                    putExtra(ReminderActionReceiver.EXTRA_DAY, day.date.toEpochDay())
                },
            )
            .addAction(0, context.getString(R.string.notif_snooze, ReminderScheduler.SNOOZE_MINUTES), actionIntent(context, ReminderActionReceiver.ACTION_SNOOZE, 11))
            .addAction(0, context.getString(R.string.notif_skip), actionIntent(context, ReminderActionReceiver.ACTION_SKIP, 12))
            .build()
        NotificationManagerCompat.from(context).notify(ROUND_NOTIFICATION_ID, notification)
    }

    fun cancelRound(context: Context) = NotificationManagerCompat.from(context).cancel(ROUND_NOTIFICATION_ID)

    private fun openRoundIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            20,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_ROUND)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun actionIntent(context: Context, action: String, requestCode: Int, extras: Intent.() -> Unit = {}): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, ReminderActionReceiver::class.java).setAction(action).apply(extras),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
