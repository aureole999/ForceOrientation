package com.forceorientation

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.forceorientation.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    
    private val prefs by lazy { 
        getSharedPreferences(BootReceiver.PREFS_NAME, Context.MODE_PRIVATE) 
    }
    
    // Flag to prevent listener callback during programmatic changes
    private var isUpdatingUI = false
    
    private val handler = Handler(Looper.getMainLooper())

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updateUI()
        if (Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Permission granted!", Toast.LENGTH_SHORT).show()
            // After overlay permission granted, request notification permission
            requestNotificationPermissionIfNeeded()
        } else {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }
    
    private val accessibilitySettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updateUI()
        if (isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "Accessibility service enabled!", Toast.LENGTH_SHORT).show()
        }
    }
    
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()
        }
        // Start service after permission dialog dismissed (regardless of result)
        startForceService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.switchForce.setOnCheckedChangeListener { _, isChecked ->
            // Ignore if this change was triggered by updateUI()
            if (isUpdatingUI) return@setOnCheckedChangeListener
            
            if (isChecked) {
                if (Settings.canDrawOverlays(this)) {
                    // Check if we need notification permission first
                    if (needsNotificationPermission()) {
                        // Will start service after permission dialog
                        requestNotificationPermission()
                    } else {
                        startForceService()
                    }
                } else {
                    isUpdatingUI = true
                    binding.switchForce.isChecked = false
                    isUpdatingUI = false
                    requestOverlayPermission()
                }
            } else {
                stopForceService()
            }
        }
        
        binding.switchAutoStart.setOnCheckedChangeListener { _, isChecked ->
            // Ignore if this change was triggered by updateUI()
            if (isUpdatingUI) return@setOnCheckedChangeListener
            
            prefs.edit().putBoolean(BootReceiver.KEY_AUTO_START, isChecked).apply()
            updateStatusText()
        }

        binding.btnPermission.setOnClickListener {
            requestOverlayPermission()
        }
        
        binding.btnAccessibility.setOnClickListener {
            openAccessibilitySettings()
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun updateUI() {
        isUpdatingUI = true
        
        val hasPermission = Settings.canDrawOverlays(this)
        val isServiceRunning = ForceService.isRunning
        val isAutoStartEnabled = prefs.getBoolean(BootReceiver.KEY_AUTO_START, false)
        val isA11yEnabled = isAccessibilityServiceEnabled()

        binding.btnPermission.isEnabled = !hasPermission
        binding.btnPermission.text = if (hasPermission) "✓ Permission Granted" else "Grant Overlay Permission"
        
        binding.switchForce.isEnabled = hasPermission
        binding.switchForce.isChecked = isServiceRunning
        
        binding.switchAutoStart.isEnabled = hasPermission
        binding.switchAutoStart.isChecked = isAutoStartEnabled
        
        // Update accessibility button
        binding.btnAccessibility.text = if (isA11yEnabled) 
            getString(R.string.accessibility_enabled) 
        else 
            getString(R.string.enable_accessibility)

        updateStatusText()
        
        isUpdatingUI = false
    }
    
    private fun updateStatusText() {
        val hasPermission = Settings.canDrawOverlays(this)
        val isServiceRunning = ForceService.isRunning
        val isAutoStartEnabled = prefs.getBoolean(BootReceiver.KEY_AUTO_START, false)
        
        binding.tvStatus.text = when {
            !hasPermission -> "⚠️ Overlay permission required"
            isServiceRunning && isAutoStartEnabled -> "✅ Active • Auto-start enabled"
            isServiceRunning -> "✅ Service Active - User rotation enforced"
            isAutoStartEnabled -> "⏸️ Service Stopped • Will start on boot"
            else -> "⏸️ Service Stopped"
        }
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayPermissionLauncher.launch(intent)
    }
    
    private fun needsNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        } else {
            false
        }
    }
    
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    
    private fun requestNotificationPermissionIfNeeded() {
        if (needsNotificationPermission()) {
            requestNotificationPermission()
        }
    }

    private fun startForceService() {
        val intent = Intent(this, ForceService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        // Delay UI update to allow service to start
        handler.postDelayed({ updateUI() }, 100)
    }

    private fun stopForceService() {
        // Temporarily disable auto-restart to allow manual stop
        val wasAutoStart = prefs.getBoolean(BootReceiver.KEY_AUTO_START, false)
        prefs.edit().putBoolean(BootReceiver.KEY_MANUAL_STOP, true).apply()
        
        stopService(Intent(this, ForceService::class.java))
        
        // Delay UI update and restore auto-start preference
        handler.postDelayed({
            prefs.edit().putBoolean(BootReceiver.KEY_MANUAL_STOP, false).apply()
            updateUI()
        }, 100)
    }
    
    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        for (service in enabledServices) {
            if (service.resolveInfo.serviceInfo.packageName == packageName) {
                return true
            }
        }
        return false
    }
    
    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        accessibilitySettingsLauncher.launch(intent)
    }
}
