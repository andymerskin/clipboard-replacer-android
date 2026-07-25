package dev.andymerskin.clipboardreplacer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.edit

/**
 * Keeps a clipboard listener alive. On Android 10+, reading the clipboard from a
 * background service is blocked, so we prompt the user to tap a notification which
 * opens [ClipboardFixActivity] with window focus. The ongoing notification opens
 * [MainActivity]; a successful fix from that path can return the user to the previous app.
 */
class ClipboardMonitorService : Service() {
    private var clipboardManager: ClipboardManager? = null

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        maybePromptToFix()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
        clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager?.addPrimaryClipChangedListener(clipListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                AppPrefs.get(this).edit { putBoolean(AppPrefs.KEY_MONITORING, false) }
                (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                    .cancel(PROMPT_NOTIFICATION_ID)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val notification = buildOngoingNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                ONGOING_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(ONGOING_NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        clipboardManager?.removePrimaryClipChangedListener(clipListener)
        clipboardManager = null
        super.onDestroy()
    }

    private fun maybePromptToFix() {
        if (ClipboardHelper.shouldSuppressPrompt()) return

        // We cannot reliably read clipboard contents without focus. Always offer a
        // one-tap fix path when the clipboard changes while monitoring.
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(PROMPT_NOTIFICATION_ID, buildPromptNotification())
    }

    private fun createChannels() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        // LOW: visible in the shade with the small icon, typically not in the status bar
        // (same general pattern as apps like Google Weather).
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ONGOING,
                getString(R.string.channel_ongoing_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.channel_ongoing_desc)
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PROMPT,
                getString(R.string.channel_prompt_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.channel_prompt_desc)
                setShowBadge(false)
            },
        )
    }

    private fun buildOngoingNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_FROM_ONGOING_NOTIFICATION, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, ClipboardMonitorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return baseNotificationBuilder(
            CHANNEL_ONGOING,
            getString(R.string.notification_ongoing_text),
        )
            .setContentIntent(openApp)
            .addAction(0, getString(R.string.action_stop), stop)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
            .build()
    }

    private fun buildPromptNotification(): Notification {
        val fixClipboard = PendingIntent.getActivity(
            this,
            2,
            Intent(this, ClipboardFixActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return baseNotificationBuilder(
            CHANNEL_PROMPT,
            getString(R.string.notification_prompt_text),
        )
            .setContentIntent(fixClipboard)
            .setAutoCancel(true)
            .build()
    }

    private fun baseNotificationBuilder(
        channelId: String,
        contentText: String,
    ): NotificationCompat.Builder =
        NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(contentText)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

    companion object {
        const val ACTION_STOP = "dev.andymerskin.clipboardreplacer.STOP"
        const val EXTRA_FROM_ONGOING_NOTIFICATION =
            "dev.andymerskin.clipboardreplacer.FROM_ONGOING_NOTIFICATION"
        private const val CHANNEL_ONGOING = "clipboard_monitor_v5"
        private const val CHANNEL_PROMPT = "clipboard_fix_prompt_v5"
        private const val ONGOING_NOTIFICATION_ID = 1001
        const val PROMPT_NOTIFICATION_ID = 1002

        fun start(context: Context) {
            val intent = Intent(context, ClipboardMonitorService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ClipboardMonitorService::class.java))
        }
    }
}
