package com.forceorientation

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.PixelFormat
import android.os.Build
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

class ForceAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var isRunning = false
            private set
        
        private var instance: ForceAccessibilityService? = null
        
        fun getInstance(): ForceAccessibilityService? = instance
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isRunning = true
        createOverlay()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't need to handle events, just maintain the overlay
    }

    override fun onInterrupt() {
        // Handle interruption
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        instance = null
        removeOverlay()
    }

    private fun createOverlay() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // Create a 1x1 pixel invisible view
        overlayView = View(this).apply {
            setBackgroundColor(0x00000000) // Fully transparent
        }

        val params = WindowManager.LayoutParams(
            1, // Width: 1 pixel
            1, // Height: 1 pixel
            // TYPE_ACCESSIBILITY_OVERLAY has the highest priority
            // It can display over system dialogs
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1)
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
            // Flags
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            // THE SECRET SAUCE:
            // Set orientation to USER - respects system rotation lock
            screenOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
            
            x = 0
            y = 0
        }

        try {
            windowManager?.addView(overlayView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
