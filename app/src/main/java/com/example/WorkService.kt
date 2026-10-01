package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import java.util.concurrent.atomic.AtomicInteger

/**
 * Keeps the process alive while a long scan, tag write or split runs, so Android does not kill
 * it when the user switches apps. Calls are reference counted: the service runs from the first
 * [begin] until the matching last [end].
 */
class WorkService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Must go foreground right away; the text is refined by onStartCommand and update().
        startForegroundCompat(build(this, label, null, 0, 0))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra(EXTRA_LABEL)?.let { label = it }
        startForegroundCompat(build(this, label, null, 0, 0))
        return START_NOT_STICKY
    }

    // Android 15 caps dataSync services; stop cleanly instead of being killed.
    override fun onTimeout(startId: Int, fgsType: Int) {
        active.set(0)
        stopSelf()
    }

    private fun startForegroundCompat(n: Notification) {
        startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    companion object {
        private const val CHANNEL_ID = "tagcue_work"
        private const val NOTIFICATION_ID = 4101
        private const val EXTRA_LABEL = "label"

        private val active = AtomicInteger(0)
        @Volatile private var label: String = "Working"

        fun begin(context: Context, text: String) {
            label = text
            if (active.getAndIncrement() == 0) {
                runCatching {
                    context.applicationContext.startForegroundService(
                        Intent(context.applicationContext, WorkService::class.java).putExtra(EXTRA_LABEL, text)
                    )
                }.onFailure { active.decrementAndGet() } // e.g. started while the app is in the background
            }
        }

        fun end(context: Context) {
            if (active.get() <= 0) return
            if (active.decrementAndGet() <= 0) {
                active.set(0)
                context.applicationContext.stopService(Intent(context.applicationContext, WorkService::class.java))
            }
        }

        fun update(context: Context, text: String, current: Int, total: Int) {
            if (active.get() <= 0) return
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            nm.notify(NOTIFICATION_ID, build(context, label, text, current, total))
        }

        private fun build(context: Context, title: String, text: String?, current: Int, total: Int): Notification {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Long-running work", NotificationManager.IMPORTANCE_LOW)
                )
            }
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE
            )
            val b = Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle(title)
                .setContentIntent(open)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            if (!text.isNullOrBlank()) b.setContentText(text)
            if (total > 0) b.setProgress(total, current.coerceIn(0, total), false)
            else b.setProgress(0, 0, true)
            return b.build()
        }
    }
}
