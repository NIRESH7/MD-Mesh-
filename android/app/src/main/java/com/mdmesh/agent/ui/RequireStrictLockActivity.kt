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
import com.mdmesh.agent.policy.OemProtectHelper
import com.mdmesh.agent.policy.StrictLockHelper

/**
 * Shown when device is restricted but Strict Lock is fully OFF in settings.
 * Still lets the user open allowlisted apps (BNCI / JCI) so work is not blocked.
 */
class RequireStrictLockActivity : AppCompatActivity() {
    private lateinit var prefs: DevicePrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = DevicePrefs(this)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (!prefs.isLocked || StrictLockHelper.isWorking(this)) {
            finish()
            return
        }
        render()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (prefs.isLocked && !StrictLockHelper.isWorking(this)) {
            Toast.makeText(this, "Turn Strict Lock ON, or open an allowed app below", Toast.LENGTH_LONG).show()
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
            text = "Strict Lock is OFF"
            textSize = 26f
            setTextColor(0xFFB00020.toInt())
            setPadding(0, 0, 0, 16)
        })
        root.addView(TextView(this).apply {
            text = "Turn Strict Lock ON to block other apps.\n\n" +
                "Pandiyan is your Home launcher.\n" +
                "You can open allowed apps below while you turn Strict Lock on.\n\n" +
                "Accessibility → Downloaded services → Pandiyan Agency → ON\n" +
                "(If On already: Off → wait → On again)"
            textSize = 16f
            setPadding(0, 0, 0, 20)
        })

        val allowed = prefs.allowedPackages
        if (allowed.isNotEmpty()) {
            root.addView(TextView(this).apply {
                text = "Open allowed app"
                textSize = 18f
                setPadding(0, 8, 0, 8)
            })
            for (pkg in allowed.sorted()) {
                val label = runCatching {
                    val pm = packageManager
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                }.getOrDefault(pkg.substringAfterLast('.'))
                root.addView(Button(this).apply {
                    text = "Open $label"
                    setOnClickListener {
                        val launch = packageManager.getLaunchIntentForPackage(pkg)
                        if (launch != null) {
                            startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } else {
                            Toast.makeText(this@RequireStrictLockActivity, "Cannot open $label", Toast.LENGTH_SHORT).show()
                        }
                    }
                })
            }
        }

        root.addView(Button(this).apply {
            text = "Turn Strict Lock ON"
            setOnClickListener {
                prefs.unlockServiceControlFor(5 * 60 * 1000L)
                StrictLockHelper.openAccessibilitySettings(this@RequireStrictLockActivity)
            }
        })
        root.addView(Button(this).apply {
            text = "Autostart / battery settings"
            setOnClickListener {
                OemProtectHelper.openRecentsLockHelp(this@RequireStrictLockActivity)
            }
        })
        root.addView(Button(this).apply {
            text = "Done"
            setOnClickListener {
                if (StrictLockHelper.isWorking(this@RequireStrictLockActivity)) {
                    startActivity(Intent(this@RequireStrictLockActivity, KioskHomeActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(
                        this@RequireStrictLockActivity,
                        "Strict Lock still OFF — toggle Off then On, or open allowed app above",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
