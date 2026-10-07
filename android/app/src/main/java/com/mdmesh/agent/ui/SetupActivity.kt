package com.mdmesh.agent.ui

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.mdmesh.agent.R
import com.mdmesh.agent.databinding.ActivitySetupBinding
import com.mdmesh.agent.data.DevicePrefs
import com.mdmesh.agent.policy.HomeEnforcer
import com.mdmesh.agent.policy.MeshDeviceAdminReceiver
import com.mdmesh.agent.service.PollingService

class SetupActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySetupBinding
    private lateinit var prefs: DevicePrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = DevicePrefs(this)
        if (prefs.setupComplete) {
            goMain()
            return
        }

        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.serverUrl.setText(prefs.serverUrl)

        binding.enableAdmin.setOnClickListener { requestAdmin() }
        binding.connect.setOnClickListener { connect() }

        // Prompt to set Pandiyan Agency as Default Home Launcher immediately
        if (!HomeEnforcer.isOurLauncherDefault(this)) {
            HomeEnforcer.enableHomeComponent(this)
            if (!HomeEnforcer.requestHomeRole(this)) {
                HomeEnforcer.openHomeChooser(this)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) {
            binding.setupStatus.text = if (isAdminActive()) "Device admin is on." else getString(R.string.admin_required)
        }
    }

    private fun isAdminActive(): Boolean {
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(ComponentName(this, MeshDeviceAdminReceiver::class.java))
    }

    private fun requestAdmin() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(
                DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                ComponentName(this@SetupActivity, MeshDeviceAdminReceiver::class.java)
            )
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "MD Mesh uses device admin to manage apps and protect against uninstall without the admin PIN."
            )
        }
        startActivity(intent)
    }

    private fun connect() {
        val url = binding.serverUrl.text.toString().trim().trimEnd('/')
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            Toast.makeText(this, "Enter a URL starting with http:// or https://", Toast.LENGTH_LONG).show()
            return
        }
        if (!isAdminActive()) {
            Toast.makeText(this, getString(R.string.admin_required), Toast.LENGTH_LONG).show()
            requestAdmin()
            return
        }
        prefs.serverUrl = url
        prefs.setupComplete = true
        requestNotificationPermission()
        requestBatteryExemption()
        val service = Intent(this, PollingService::class.java)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service) else startService(service)
        }
        goMain()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 21)
            }
        }
    }

    private fun requestBatteryExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val pm = getSystemService(PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        runCatching { startActivity(intent) }
    }

    private fun goMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
