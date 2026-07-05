package com.lunaris.frostedglass

import android.content.Context
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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME = "frosted_glass_prefs"
        const val KEY_ENABLED = "enabled"
        const val KEY_BLUR_RADIUS = "blur_radius"
        const val KEY_TILE_OPACITY = "tile_opacity"
        const val KEY_PANEL_BLUR = "panel_blur"
        const val KEY_CORNER_RADIUS = "corner_radius"
        const val SHARED_PATH = "/data/local/tmp/frosted_glass_qs.xml"

        fun getPrefs(context: Context) =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        private fun copyPrefsToShared(ctx: Context): Boolean {
            return try {
                val src = File(ctx.applicationInfo.dataDir, "shared_prefs/$PREFS_NAME.xml")
                if (!src.exists()) {
                    Toast.makeText(ctx, "Prefs file not found", Toast.LENGTH_SHORT).show()
                    return false
                }
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c",
                    "cp '${src.absolutePath}' $SHARED_PATH && chmod 666 $SHARED_PATH"
                ))
                val done = process.waitFor(5, TimeUnit.SECONDS)
                done && process.exitValue() == 0
            } catch (e: Exception) {
                Toast.makeText(ctx, "Root failed: ${e.message}", Toast.LENGTH_SHORT).show()
                false
            }
        }
    }

    private var previewContainerId = View.generateViewId()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getPrefs(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(0xFF1A1A2E.toInt())
        }

        root.addView(TextView(this).apply {
            text = "Frosted Glass QS"
            setTextColor(Color.WHITE)
            textSize = 24f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
        })

        root.addView(TextView(this).apply {
            text = "Adjust blur settings, save, then reboot"
            setTextColor(0x99FFFFFF.toInt())
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(24))
        })

        root.addView(Switch(this).apply {
            text = "Enable Frosted Glass"
            setTextColor(Color.WHITE)
            textSize = 16f
            isChecked = prefs.getBoolean(KEY_ENABLED, true)
            setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
                prefs.edit().putBoolean(KEY_ENABLED, isChecked).apply()
                updatePreview()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(12), 0, dp(12)) }
        })

        root.addView(createSlider("Tile Blur Radius", prefs.getInt(KEY_BLUR_RADIUS, 20), 0, 50) { v ->
            prefs.edit().putInt(KEY_BLUR_RADIUS, v).apply()
        })

        root.addView(createSlider("Tile Opacity", prefs.getInt(KEY_TILE_OPACITY, 19), 0, 100) { v ->
            prefs.edit().putInt(KEY_TILE_OPACITY, v).apply()
            updatePreview()
        })

        root.addView(createSlider("Corner Radius (dp)", prefs.getInt(KEY_CORNER_RADIUS, 20), 0, 40) { v ->
            prefs.edit().putInt(KEY_CORNER_RADIUS, v).apply()
            updatePreview()
        })

        root.addView(Switch(this).apply {
            text = "Panel Blur"
            setTextColor(Color.WHITE)
            textSize = 16f
            isChecked = prefs.getBoolean(KEY_PANEL_BLUR, true)
            setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
                prefs.edit().putBoolean(KEY_PANEL_BLUR, isChecked).apply()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(12), 0, dp(12)) }
        })

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
            tile.layoutParams = LinearLayout.LayoutParams(dp(80), dp(80)).apply {
                setMargins(dp(8), dp(8), dp(8), dp(8))
            }
            previewContainer.addView(tile)
        }
        root.addView(previewContainer)

        root.addView(Button(this).apply {
            text = "Save & Reboot to Apply"
            setBackgroundColor(0xFF6C63FF.toInt())
            setTextColor(Color.WHITE)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(24) }
            setOnClickListener {
                val saved = copyPrefsToShared(this@MainActivity)
                if (saved) {
                    Toast.makeText(this@MainActivity, "Settings saved! Rebooting...", Toast.LENGTH_SHORT).show()
                    android.os.Handler(mainLooper).postDelayed({
                        Runtime.getRuntime().exec(arrayOf("su", "-c", "reboot"))
                    }, 1000)
                }
            }
        })

        root.addView(Button(this).apply {
            text = "Save Only (No Reboot)"
            setBackgroundColor(0xFF444444.toInt())
            setTextColor(0x99FFFFFF.toInt())
            setPadding(dp(16), dp(12), dp(16), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
            setOnClickListener {
                val saved = copyPrefsToShared(this@MainActivity)
                Toast.makeText(this@MainActivity,
                    if (saved) "Saved! Reboot to apply." else "Save failed",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })

        root.addView(Button(this).apply {
            text = "Reset to Defaults"
            setBackgroundColor(0xFF333333.toInt())
            setTextColor(0x99FFFFFF.toInt())
            setPadding(dp(16), dp(12), dp(16), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
            setOnClickListener {
                getPrefs(this@MainActivity).edit().clear().apply()
                Runtime.getRuntime().exec(arrayOf("su", "-c", "rm -f $SHARED_PATH"))
                recreate()
            }
        })

        setContentView(root)
    }

    private fun createPreviewTile(): View {
        val density = resources.displayMetrics.density
        val prefs = getPrefs(this)
        val enabled = prefs.getBoolean(KEY_ENABLED, true)
        val opacity = prefs.getInt(KEY_TILE_OPACITY, 19)
        val cornerRadius = prefs.getInt(KEY_CORNER_RADIUS, 20)
        val alpha = if (enabled) opacity.toFloat() / 100f else 1f
        val bgColor = if (enabled) ((255 * alpha).toInt() shl 24) or 0xFFFFFF else 0xFF333333.toInt()

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
        val prefs = getPrefs(this)
        val enabled = prefs.getBoolean(KEY_ENABLED, true)
        val opacity = prefs.getInt(KEY_TILE_OPACITY, 19)
        val cornerRadius = prefs.getInt(KEY_CORNER_RADIUS, 20)
        val alpha = if (enabled) opacity.toFloat() / 100f else 1f
        val bgColor = if (enabled) ((255 * alpha).toInt() shl 24) or 0xFFFFFF else 0xFF333333.toInt()

        for (i in 0 until container.childCount) {
            container.getChildAt(i).background = GradientDrawable().apply {
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
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    valueText.text = "$progress"
                    onChange(progress)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
