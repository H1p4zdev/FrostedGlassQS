package com.lunaris.frostedglass

import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.view.ViewGroup
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.io.File

object TileBlurHook {

    private const val TAG = "FrostedGlassQS"
    private const val SHARED_PATH = "/data/local/tmp/frosted_glass_qs.xml"

    data class FrostedSettings(
        val enabled: Boolean = true,
        val blurRadius: Int = 20,
        val tileOpacity: Int = 19,
        val panelBlur: Boolean = true,
        val cornerRadius: Int = 20
    )

    private fun readSettings(): FrostedSettings {
        return try {
            val file = File(SHARED_PATH)
            if (!file.exists()) {
                XposedBridge.log("$TAG: Config not found, using defaults")
                return FrostedSettings()
            }
            val xml = file.readText()
            val enabled = parseBool(xml, "enabled", true)
            val blurRadius = parseInt(xml, "blur_radius", 20)
            val tileOpacity = parseInt(xml, "tile_opacity", 19)
            val panelBlur = parseBool(xml, "panel_blur", true)
            val cornerRadius = parseInt(xml, "corner_radius", 20)
            FrostedSettings(enabled, blurRadius, tileOpacity, panelBlur, cornerRadius)
        } catch (e: Throwable) {
            FrostedSettings()
        }
    }

    private fun parseBool(xml: String, key: String, default: Boolean): Boolean {
        val match = Regex("<boolean name=\"$key\" value=\"(true|false)\"").find(xml) ?: return default
        return match.groupValues[1] == "true"
    }

    private fun parseInt(xml: String, key: String, default: Int): Int {
        val match = Regex("<int name=\"$key\" value=\"(-?\\d+)\"").find(xml) ?: return default
        return match.groupValues[1].toIntOrNull() ?: default
    }

    fun hookTiles(classLoader: ClassLoader) {
        // Try to hook init(QSTile) for any FUTURE tiles
        try {
            val tileViewClass = XposedHelpers.findClass(
                "com.android.systemui.qs.tileimpl.QSTileViewImpl", classLoader
            )
            val qstileClass = XposedHelpers.findClass(
                "com.android.systemui.plugins.qs.QSTile", classLoader
            )
            XposedHelpers.findAndHookMethod(
                tileViewClass, "init", qstileClass,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        applyTileEffect(param.thisObject as View, readSettings())
                    }
                }
            )
            XposedBridge.log("$TAG: Hooked QSTileViewImpl.init()")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: init hook failed: ${e.message}")
        }

        // Also hook onAttachedToWindow for future tiles
        try {
            val tileViewClass = XposedHelpers.findClass(
                "com.android.systemui.qs.tileimpl.QSTileViewImpl", classLoader
            )
            XposedHelpers.findAndHookMethod(
                tileViewClass, "onAttachedToWindow",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        applyTileEffect(param.thisObject as View, readSettings())
                    }
                }
            )
            XposedBridge.log("$TAG: Hooked QSTileViewImpl.onAttachedToWindow()")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: onAttachedToWindow hook failed: ${e.message}")
        }
    }

    fun hookPanel(classLoader: ClassLoader) {
        val candidates = listOf(
            "com.android.systemui.qs.QSPanel",
            "com.android.systemui.qs.QSPanelImpl",
            "com.android.systemui.qs.tiles.QuickQSPanel"
        )

        for (name in candidates) {
            try {
                val panelClass = XposedHelpers.findClass(name, classLoader)

                // Hook onLayout — walk tree and modify all tiles
                XposedHelpers.findAndHookMethod(
                    panelClass, "onLayout",
                    Boolean::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val settings = readSettings()
                            if (!settings.enabled) return
                            val panel = param.thisObject as View
                            applyPanelEffect(panel, settings)
                            walkAndApplyTiles(panel, settings)
                        }
                    }
                )
                XposedBridge.log("$TAG: Hooked $name.onLayout() — will walk tile tree")
                return
            } catch (_: Throwable) {}
        }
        XposedBridge.log("$TAG: WARNING - No panel hooks succeeded!")
    }

    private fun walkAndApplyTiles(view: View, settings: FrostedSettings) {
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                // Match any view that could be a QS tile
                val className = child.javaClass.name
                if (className.contains("QSTileView") || className.contains("TileView")) {
                    applyTileEffect(child, settings)
                }
                if (child is ViewGroup) {
                    walkAndApplyTiles(child, settings)
                }
            }
        }
    }

    private fun applyPanelEffect(panel: View, settings: FrostedSettings) {
        if (!settings.panelBlur) return
        try {
            panel.setBackgroundColor(0x08000000.toInt())
        } catch (_: Throwable) {}
    }

    private fun applyTileEffect(view: View, settings: FrostedSettings) {
        try {
            val density = view.resources.displayMetrics.density
            val opacity = settings.tileOpacity.toFloat() / 100f
            val bgColor = ((255 * opacity).toInt() shl 24) or 0xFFFFFF
            val cornerRadius = settings.cornerRadius * density

            val bg = view.background
            if (bg is RippleDrawable) {
                try {
                    val pkgName = view.context.packageName
                    val bgId = view.resources.getIdentifier("background", "id", pkgName)
                    val backgroundLayer = bg.findDrawableByLayerId(bgId)
                    if (backgroundLayer is LayerDrawable) {
                        val baseId = view.resources.getIdentifier(
                            "qs_tile_background_base", "id", pkgName
                        )
                        val baseDrawable = backgroundLayer.findDrawableByLayerId(baseId)
                        if (baseDrawable is GradientDrawable) {
                            baseDrawable.setColor(bgColor)
                            baseDrawable.cornerRadius = cornerRadius
                            XposedBridge.log("$TAG: Tile modified (opacity=${settings.tileOpacity}%, corners=${settings.cornerRadius}dp)")
                        }
                    }
                } catch (e: Throwable) {
                    XposedBridge.log("$TAG: BG modify error: ${e.message}")
                }
            } else {
                // Fallback: try setting background directly
                try {
                    val drawable = view.background?.mutate()
                    if (drawable is GradientDrawable) {
                        drawable.setColor(bgColor)
                        drawable.cornerRadius = cornerRadius
                    }
                } catch (_: Throwable) {}
            }

            // Window background blur via ViewRootImpl
            if (settings.blurRadius > 0) {
                try {
                    val viewClass = Class.forName("android.view.View")
                    val viewRootField = viewClass.getDeclaredField("mViewRootImpl").apply { isAccessible = true }
                    val viewRoot = viewRootField.get(view)
                    if (viewRoot != null) {
                        val blurUtilsClass = XposedHelpers.findClass(
                            "com.android.systemui.statusbar.BlurUtils", view.context.classLoader
                        )
                        val getInstance = blurUtilsClass.getDeclaredMethod("getBlurUtilsInstance")
                        val blurUtils = getInstance.invoke(null)
                        val applyMethod = blurUtilsClass.declaredMethods.find {
                            it.name == "applyBlur" && it.parameterCount == 3
                        }
                        applyMethod?.isAccessible = true
                        applyMethod?.invoke(blurUtils, viewRoot, settings.blurRadius, false)
                        XposedBridge.log("$TAG: Blur applied (radius=${settings.blurRadius})")
                    }
                } catch (e: Throwable) {
                    XposedBridge.log("$TAG: Blur error: ${e.message}")
                }
            }
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: applyTileEffect error: ${e.message}")
        }
    }
}
