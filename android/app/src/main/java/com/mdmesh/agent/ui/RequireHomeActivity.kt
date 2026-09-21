package com.mdmesh.agent.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.mdmesh.agent.data.DevicePrefs
import com.mdmesh.agent.policy.HomeEnforcer
import com.mdmesh.agent.policy.StrictLockHelper

/**
 * Required while Restricted: Pandiyan must be the default Home on phones and tablets.
 */
class RequireHomeActivity : AppCompatActivity() {
    private lateinit var prefs: DevicePrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = DevicePrefs(this)
        if (!prefs.isLocked) {
            finish()
            return
        }
        HomeEnforcer.enableHomeComponent(this)
        render()
        if (!HomeEnforcer.requestHomeRole(this)) {
            HomeEnforcer.openHomeChooser(this)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!prefs.isLocked) {
            finish()
            return
        }
        if (HomeEnforcer.isOurLauncherDefault(this)) {
            Toast.makeText(this, "Home locked to Pandiyan Agency", Toast.LENGTH_SHORT).show()
            // Do NOT auto-open Accessibility here — that fought Soft Lock and flickered
            startActivity(
                Intent(this, KioskHomeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
            finish()
            return
        }
        render()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (prefs.isLocked) {
            Toast.makeText(this, "Set Pandiyan Agency as Home (Always) to continue", Toast.LENGTH_LONG).show()
            return
        }
        super.onBackPressed()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 72, 48, 48)
            setBackgroundColor(0xFFF4F7F8.toInt())
        }
        root.addView(TextView(this).apply {
            text = "Set Pandiyan as Home"
            textSize = 26f
            setTextColor(0xFF0B1F2A.toInt())
            setPadding(0, 0, 0, 16)
        })
        root.addView(TextView(this).apply {
            text = "Required on every phone and tablet.\n\n" +
                "1) Choose Pandiyan Agency as Home → Always\n" +
                "2) Turn Strict Lock ON\n\n" +
                "Then only admin apps + website links open.\n" +
                "Use Clear other apps in Pandiyan (not phone Clear All)."
            textSize = 16f
            setPadding(0, 0, 0, 28)
        })
        root.addView(Button(this).apply {
            text = "1) Set Pandiyan as Home (Always)"
            setOnClickListener {
                HomeEnforcer.enableHomeComponent(this@RequireHomeActivity)
                if (!HomeEnforcer.requestHomeRole(this@RequireHomeActivity)) {
                    HomeEnforcer.openHomeChooser(this@RequireHomeActivity)
                }
            }
        })
        root.addView(Button(this).apply {
            text = "2) Turn Strict Lock ON"
            setOnClickListener {
                prefs.unlockServiceControlFor(5 * 60 * 1000L)
                StrictLockHelper.openAccessibilitySettings(this@RequireHomeActivity)
            }
        })
        root.addView(Button(this).apply {
            text = "Done — Open launcher"
            setOnClickListener {
                if (HomeEnforcer.isOurLauncherDefault(this@RequireHomeActivity)) {
                    startActivity(Intent(this@RequireHomeActivity, KioskHomeActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(
                        this@RequireHomeActivity,
                        "Still not default Home. Choose Pandiyan Agency → Always.",
                        Toast.LENGTH_LONG
                    ).show()
                    HomeEnforcer.openHomeChooser(this@RequireHomeActivity)
                }
            }
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
