package com.forceorientation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder

import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class ForceService : Service() {

    companion object {
        private const val CHANNEL_ID = "force_orientation_channel"
        private const val NOTIFICATION_ID = 1

        
        @Volatile
        var isRunning = false
            private set
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null


    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        createOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY: If the system kills the service, it will restart it
        // with a null intent, allowing the service to resume
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        removeOverlay()

        
        // Attempt to restart service if it was killed unexpectedly (not manually stopped)
        val prefs = getSharedPreferences(BootReceiver.PREFS_NAME, Context.MODE_PRIVATE)
        val autoStart = prefs.getBoolean(BootReceiver.KEY_AUTO_START, false)
        val manualStop = prefs.getBoolean(BootReceiver.KEY_MANUAL_STOP, false)
        
        if (autoStart && !manualStop) {
            // Schedule restart via broadcast
            val restartIntent = Intent(this, RestartReceiver::class.java)
            sendBroadcast(restartIntent)
        }
    }
    
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // App was swiped away from recent apps
        // Try to restart the service
        val prefs = getSharedPreferences(BootReceiver.PREFS_NAME, Context.MODE_PRIVATE)
        val autoStart = prefs.getBoolean(BootReceiver.KEY_AUTO_START, false)
        val manualStop = prefs.getBoolean(BootReceiver.KEY_MANUAL_STOP, false)
        
        if (autoStart && !manualStop) {
            val restartIntent = Intent(this, RestartReceiver::class.java)
            sendBroadcast(restartIntent)
        }
    }



    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Force Orientation Service",
                // IMPORTANCE_MIN: No sound, no status bar icon, only shows in shade
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Keeps the orientation sync service running"
                setShowBadge(false)
                // Don't show lights or vibrate
                enableLights(false)
                enableVibration(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Stop action
        val stopIntent = Intent(this, StopServiceReceiver::class.java)
        val stopPendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Force Orientation")
            .setContentText("Active")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            // PRIORITY_MIN: Shows at bottom of notification list, no status bar icon on older devices
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopPendingIntent
            )
            .build()
    }

    private fun createOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Create a 1x1 pixel invisible view
        overlayView = View(this).apply {
            setBackgroundColor(0x00000000) // Fully transparent
        }

        val params = WindowManager.LayoutParams(
            1, // Width: 1 pixel
            1, // Height: 1 pixel
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            // Flags:
            // NOT_FOCUSABLE: Don't steal focus from other apps
            // NOT_TOUCHABLE: Let all touches pass through
            // NOT_TOUCH_MODAL: Allow touches outside our window
            // LAYOUT_IN_SCREEN: Position relative to screen
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            // THE SECRET SAUCE:
            // Set orientation to USER.
            // This forces the WindowManager to look at the System User Rotation setting,
            // and ignore the orientation requests of apps sitting below this overlay.
            screenOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
            
            // Position at top-left corner (doesn't really matter for 1x1 invisible view)
            x = 0
            y = 0
        }

        windowManager?.addView(overlayView, params)
    }

    private fun removeOverlay() {
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                // View might already be removed
            }
            overlayView = null
        }
        windowManager = null
    }
}
