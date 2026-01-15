package com.forceorientation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

class BootReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Check if auto-start is enabled and we have overlay permission
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val autoStart = prefs.getBoolean(KEY_AUTO_START, false)
            
            // Clear any manual stop flag on boot
            prefs.edit().putBoolean(KEY_MANUAL_STOP, false).apply()
            
            if (autoStart && Settings.canDrawOverlays(context)) {
                val serviceIntent = Intent(context, ForceService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
    
    companion object {
        const val PREFS_NAME = "force_orientation_prefs"
        const val KEY_AUTO_START = "auto_start_on_boot"
        const val KEY_MANUAL_STOP = "manual_stop"
    }
}
