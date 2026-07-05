package com.lunaris.frostedglass

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME = "frosted_glass_prefs"
        const val KEY_ENABLED = "enabled"
        const val KEY_BLUR_RADIUS = "blur_radius"
        const val KEY_TILE_OPACITY = "tile_opacity"
        const val KEY_PANEL_BLUR = "panel_blur"
        const val KEY_CORNER_RADIUS = "corner_radius"

        fun getPrefs(context: Context): SharedPreferences {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE)
        }
    }

    private lateinit var prefs: SharedPreferences
    private var previewContainerId = View.generateViewId()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getPrefs(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(0xFF1A1A2E.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Title
        root.addView(TextView(this).apply {
            text = "Frosted Glass QS"
            setTextColor(Color.WHITE)
            textSize = 24f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
        })

        root.addView(TextView(this).apply {
            text = "Control blur settings in real-time"
            setTextColor(0x99FFFFFF.toInt())
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(24))
        })

        // Master toggle
        val enabledSwitch = Switch(this).apply {
            text = "Enable Frosted Glass"
            setTextColor(Color.WHITE)
            textSize = 16f
            isChecked = prefs.getBoolean(KEY_ENABLED, true)
            setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
                prefs.edit().putBoolean(KEY_ENABLED, isChecked).apply()
                updatePreview()
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, dp(12), 0, dp(12))
            layoutParams = params
        }
        root.addView(enabledSwitch)

        // Blur radius
        root.addView(createSlider("Tile Blur Radius", prefs.getInt(KEY_BLUR_RADIUS, 20), 0, 50) { value ->
            prefs.edit().putInt(KEY_BLUR_RADIUS, value).apply()
        })

        // Tile opacity
        root.addView(createSlider("Tile Opacity", prefs.getInt(KEY_TILE_OPACITY, 19), 0, 100) { value ->
            prefs.edit().putInt(KEY_TILE_OPACITY, value).apply()
            updatePreview()
        })

        // Corner radius
        root.addView(createSlider("Corner Radius (dp)", prefs.getInt(KEY_CORNER_RADIUS, 20), 0, 40) { value ->
            prefs.edit().putInt(KEY_CORNER_RADIUS, value).apply()
            updatePreview()
        })

        // Panel blur toggle
        val panelSwitch = Switch(this).apply {
            text = "Panel Blur"
            setTextColor(Color.WHITE)
            textSize = 16f
            isChecked = prefs.getBoolean(KEY_PANEL_BLUR, true)
            setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
                prefs.edit().putBoolean(KEY_PANEL_BLUR, isChecked).apply()
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, dp(12), 0, dp(12))
            layoutParams = params
        }
        root.addView(panelSwitch)

        // Preview section
        root.addView(TextView(this).apply {
            text = "Preview"
            setTextColor(0x99FFFFFF.toInt())
            textSize = 16f
            setPadding(0, dp(24), 0, dp(12))
        })

        val previewContainer = LinearLayout(this).apply {
            id = previewContainerId
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(12))
        }

        for (i in 1..3) {
            val tile = createPreviewTile()
            val params = LinearLayout.LayoutParams(dp(80), dp(80)).apply {
                setMargins(dp(8), dp(8), dp(8), dp(8))
            }
            tile.layoutParams = params
            previewContainer.addView(tile)
        }

        root.addView(previewContainer)

        // Save button
        root.addView(Button(this).apply {
            text = "Save & Reboot to Apply"
            setBackgroundColor(0xFF6C63FF.toInt())
            setTextColor(Color.WHITE)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(24)
            }
            layoutParams = params
            setOnClickListener {
                prefs.edit().apply()
                recreate()
            }
        })

        // Reset button
        root.addView(Button(this).apply {
            text = "Reset to Defaults"
            setBackgroundColor(0xFF333333.toInt())
            setTextColor(0x99FFFFFF.toInt())
            setPadding(dp(16), dp(12), dp(16), dp(12))
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
            layoutParams = params
            setOnClickListener {
                prefs.edit().clear().apply()
                recreate()
            }
        })

        setContentView(root)
    }

    private fun createPreviewTile(): View {
        val density = resources.displayMetrics.density
        val enabled = prefs.getBoolean(KEY_ENABLED, true)
        val opacity = prefs.getInt(KEY_TILE_OPACITY, 19)
        val cornerRadius = prefs.getInt(KEY_CORNER_RADIUS, 20)

        val alpha = if (enabled) opacity.toFloat() / 100f else 1f
        val bgColor = if (enabled) {
            val white = (255 * alpha).toInt()
            (white shl 24) or 0xFFFFFF
        } else {
            0xFF333333.toInt()
        }

        return View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setCornerRadius(cornerRadius * density)
                setColor(bgColor)
                setStroke((1 * density).toInt(), 0x30FFFFFF.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(dp(80), dp(80))
        }
    }

    private fun updatePreview() {
        val container = findViewById<LinearLayout>(previewContainerId) ?: return
        val density = resources.displayMetrics.density
        val enabled = prefs.getBoolean(KEY_ENABLED, true)
        val opacity = prefs.getInt(KEY_TILE_OPACITY, 19)
        val cornerRadius = prefs.getInt(KEY_CORNER_RADIUS, 20)

        val alpha = if (enabled) opacity.toFloat() / 100f else 1f
        val bgColor = if (enabled) {
            val white = (255 * alpha).toInt()
            (white shl 24) or 0xFFFFFF
        } else {
            0xFF333333.toInt()
        }

        for (i in 0 until container.childCount) {
            val tile = container.getChildAt(i)
            tile.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setCornerRadius(cornerRadius * density)
                setColor(bgColor)
                setStroke((1 * density).toInt(), 0x30FFFFFF.toInt())
            }
        }
    }

    private fun createSlider(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit): LinearLayout {
        val valueText = TextView(this).apply {
            text = "$value"
            setTextColor(0xFF6C63FF.toInt())
            textSize = 16f
            gravity = Gravity.END
            minWidth = dp(40)
        }

        val seekBar = SeekBar(this).apply {
            this.max = max
            this.progress = value
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    valueText.text = "$progress"
                    onChange(progress)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))

            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(TextView(this@MainActivity).apply {
                    text = label
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                addView(valueText)
            })

            addView(seekBar)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
