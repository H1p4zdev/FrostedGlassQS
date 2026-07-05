package com.lunaris.frostedglass

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Simple UI to show module status
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        // Title
        val title = TextView(this).apply {
            text = "Frosted Glass QS"
            textSize = 24f
            setTextColor(resources.getColor(android.R.color.white, theme))
            setPadding(0, 0, 0, 24)
        }

        // Status
        val status = TextView(this).apply {
            text = "Module Status: Active"
            textSize = 16f
            setTextColor(resources.getColor(android.R.color.holo_green_light, theme))
            setPadding(0, 0, 0, 16)
        }

        // Description
        val desc = TextView(this).apply {
            text = "This module adds a frosted glass blur effect to Quick Settings tiles.\n\n" +
                    "Features:\n" +
                    "- Translucent tile backgrounds\n" +
                    "- Background blur on tiles (Android 12+)\n" +
                    "- Rounded corners\n\n" +
                    "Enable in LSPosed and select SystemUI as scope."
            textSize = 14f
            setTextColor(0xCCFFFFFF.toInt())
            setLineSpacing(0f, 1.2f)
        }

        layout.addView(title)
        layout.addView(status)
        layout.addView(desc)

        setContentView(layout)
    }
}
