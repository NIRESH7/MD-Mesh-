package com.mdmesh.agent.ui

import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import com.mdmesh.agent.policy.ProtectionPin

object PinDialogs {
    fun askPin(
        context: Context,
        title: String,
        message: String,
        onResult: (String?) -> Unit
    ) {
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "4–8 digit PIN"
            setPadding(48, 32, 48, 32)
        }
        val wrap = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 0)
            addView(input)
        }
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setView(wrap)
            .setCancelable(true)
            .setNegativeButton("Cancel") { _, _ -> onResult(null) }
            .setPositiveButton("OK") { _, _ -> onResult(input.text?.toString()?.trim()) }
            .show()
    }

    fun askNewPin(
        context: Context,
        title: String,
        onResult: (String?) -> Unit
    ) {
        val pin = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "New PIN (4–8 digits)"
            setPadding(48, 24, 48, 16)
        }
        val confirm = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "Confirm PIN"
            setPadding(48, 16, 48, 24)
        }
        val wrap = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 0)
            addView(pin)
            addView(confirm)
        }
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage("This PIN is required before MD Mesh can be uninstalled.")
            .setView(wrap)
            .setCancelable(false)
            .setPositiveButton("Save") { _, _ ->
                val a = pin.text?.toString()?.trim().orEmpty()
                val b = confirm.text?.toString()?.trim().orEmpty()
                when {
                    !ProtectionPin.isValidFormat(a) -> onResult(null)
                    a != b -> onResult("")
                    else -> onResult(a)
                }
            }
            .show()
    }
}
