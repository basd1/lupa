package dev.bastian.lupa.data.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.bastian.lupa.MainActivity
import dev.bastian.lupa.R
import dev.bastian.lupa.domain.AnalysisOutcome
import dev.bastian.lupa.domain.ChangeNotifier

/** Posts one notification per analysis that opens the Changes screen. Without the permission it does nothing. */
class AndroidChangeNotifier(private val context: Context) : ChangeNotifier {

    override fun notifyChanges(outcome: AnalysisOutcome) {
        val changes = outcome.changes ?: return
        if (!canNotify()) return
        ensureChannel()
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_CHANGES, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val granted = changes.newlyGranted.size + changes.newPermissions.count { it.granted }
        val parts = buildList {
            if (changes.newApps.isNotEmpty()) {
                add(context.resources.getQuantityString(R.plurals.notify_new_apps, changes.newApps.size, changes.newApps.size))
            }
            if (granted > 0) {
                add(context.resources.getQuantityString(R.plurals.notify_granted, granted, granted))
            }
            if (outcome.newHighRiskApps.isNotEmpty()) {
                add(context.resources.getQuantityString(R.plurals.notify_high_risk, outcome.newHighRiskApps.size, outcome.newHighRiskApps.size))
            }
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notify_title))
            .setContentText(parts.joinToString(", "))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        // The permission was checked in canNotify(); lint cannot see through the helper.
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun canNotify(): Boolean {
        val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permitted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.notify_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    private companion object {
        const val CHANNEL_ID = "changes"
        const val NOTIFICATION_ID = 1
    }
}
