package com.forceorientation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * Receiver to restart the service after it's killed
 */
class RestartReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent?) {
        // Check if we have permission and auto-start is enabled
        val prefs = context.getSharedPreferences(BootReceiver.PREFS_NAME, Context.MODE_PRIVATE)
        val autoStart = prefs.getBoolean(BootReceiver.KEY_AUTO_START, false)
        
        if (autoStart && Settings.canDrawOverlays(context) && !ForceService.isRunning) {
            val serviceIntent = Intent(context, ForceService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
