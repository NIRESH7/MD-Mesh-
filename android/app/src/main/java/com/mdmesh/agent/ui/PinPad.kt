package com.mdmesh.agent.ui
import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
/** On-screen number pad — avoids soft keyboard (IME was kicking restricted devices to Home). */
object PinPad {
    data class Host(
        val root: LinearLayout,
        val display: EditText
    ) {
        fun pin(): String = display.text?.toString()?.trim().orEmpty()
    }
    fun build(
        context: Context,
        title: String,
        message: String,
        confirmLabel: String,
        onConfirm: (String) -> Unit,
        onCancel: (() -> Unit)? = null
    ): Host {
        val display = EditText(context).apply {
            isFocusable = false
            isFocusableInTouchMode = false
            isClickable = false
            isLongClickable = false
            isCursorVisible = false
            showSoftInputOnFocus = false
            inputType = EditorInfo.TYPE_NULL
            hint = "PIN"
            textSize = 28f
            gravity = Gravity.CENTER
            typeface = Typeface.MONOSPACE
            setPadding(16, 24, 16, 24)
        }
        fun appendDigit(d: String) {
            if (display.text.length >= 12) return
            display.append(d)
        }
        fun backspace() {
            val t = display.text
            if (t.isNotEmpty()) display.setText(t.subSequence(0, t.length - 1))
        }
        val grid = GridLayout(context).apply {
            columnCount = 3
            rowCount = 4
            setPadding(0, 16, 0, 16)
        }
        val keys = listOf(
            "1", "2", "3",
            "4", "5", "6",
            "7", "8", "9",
            "⌫", "0", "OK"
        )
        for (key in keys) {
            val btn = Button(context).apply {
                text = key
                textSize = 22f
                minimumHeight = dp(context, 56)
                setOnClickListener {
                    when (key) {
                        "⌫" -> backspace()
                        "OK" -> onConfirm(display.text?.toString()?.trim().orEmpty())
                        else -> appendDigit(key)
                    }
                }
            }
            val lp = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(8, 8, 8, 8)
            }
            grid.addView(btn, lp)
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 48)
            setBackgroundColor(0xFFF4F7F8.toInt())
            addView(TextView(context).apply {
                text = title
                textSize = 22f
                setPadding(0, 0, 0, 12)
            })
            addView(TextView(context).apply {
                text = message
                textSize = 14f
                setPadding(0, 0, 0, 16)
            })
            addView(display)
            addView(grid)
            if (onCancel != null) {
                addView(Button(context).apply {
                    text = "Cancel"
                    setOnClickListener { onCancel() }
                })
            }
            addView(Button(context).apply {
                text = confirmLabel
                setOnClickListener { onConfirm(display.text?.toString()?.trim().orEmpty()) }
            })
        }
        return Host(root, display)
    }
    private fun dp(context: Context, value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
}
