package com.example.sentriai.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.sentriai.MainActivity
import com.example.sentriai.R

/**
 * The ongoing notification that makes continuous listening legible to the user — and,
 * on the platform side, is what a foreground service is required to post.
 */
internal object ListeningNotifications {

    const val CHANNEL_ID = "sentriai_listening"
    const val NOTIFICATION_ID = 1001

    /**
     * Idempotent. Deliberately IMPORTANCE_LOW: this notification can be up for hours, so
     * it must not make a sound or peek every time it is updated.
     */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.listening_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.listening_channel_description)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * @param text the status line, or the latest transcribed utterance once there is one.
     */
    fun build(context: Context, text: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            pendingIntentFlags(),
        )
        val stopIntent = PendingIntent.getService(
            context,
            1,
            Intent(context, ListeningService::class.java).setAction(ListeningService.ACTION_STOP),
            pendingIntentFlags(),
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(context.getString(R.string.listening_notification_title))
            .setContentText(text)
            // Utterances routinely run past one line; without this the user sees a stub.
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent)
            .addAction(
                0,
                context.getString(R.string.listening_notification_stop),
                stopIntent,
            )
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            // The transcript is personal; keep it off the lock screen.
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()
    }

    private fun pendingIntentFlags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
}
