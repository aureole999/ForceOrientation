package com.forceorientation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receiver to stop the service from the notification action
 */
class StopServiceReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent?) {
        // Disable auto-start when user explicitly stops
        val prefs = context.getSharedPreferences(BootReceiver.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(BootReceiver.KEY_AUTO_START, false).apply()
        
        // Stop the service
        context.stopService(Intent(context, ForceService::class.java))
    }
}
