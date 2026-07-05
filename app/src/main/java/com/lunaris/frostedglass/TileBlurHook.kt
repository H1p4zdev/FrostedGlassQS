package com.lunaris.frostedglass

import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
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
                XposedBridge.log("$TAG: Config file not found at $SHARED_PATH, using defaults")
                return FrostedSettings()
            }
            val xml = file.readText()
            val enabled = parseBool(xml, "enabled", true)
            val blurRadius = parseInt(xml, "blur_radius", 20)
            val tileOpacity = parseInt(xml, "tile_opacity", 19)
            val panelBlur = parseBool(xml, "panel_blur", true)
            val cornerRadius = parseInt(xml, "corner_radius", 20)
            XposedBridge.log("$TAG: Read settings: enabled=$enabled, blur=$blurRadius, opacity=$tileOpacity, corners=$cornerRadius")
            FrostedSettings(enabled, blurRadius, tileOpacity, panelBlur, cornerRadius)
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Error reading config: ${e.message}")
            FrostedSettings()
        }
    }

    private fun parseBool(xml: String, key: String, default: Boolean): Boolean {
        val pattern = "<boolean name=\"$key\" value=\"(true|false)\""
        val match = Regex(pattern).find(xml) ?: return default
        return match.groupValues[1] == "true"
    }

    private fun parseInt(xml: String, key: String, default: Int): Int {
        val pattern = "<int name=\"$key\" value=\"(-?\\d+)\""
        val match = Regex(pattern).find(xml) ?: return default
        return match.groupValues[1].toIntOrNull() ?: default
    }

    fun hookTiles(classLoader: ClassLoader) {
        val settings = readSettings()
        if (!settings.enabled) {
            XposedBridge.log("$TAG: Module disabled in settings")
            return
        }

        var hooked = false

        // Primary: hook QSTileViewImpl.init(QSTile)
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
            hooked = true
            XposedBridge.log("$TAG: Hooked QSTileViewImpl.init(QSTile)")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: QSTileViewImpl.init hook failed: ${e.message}")
        }

        // Fallback: hook onFinishInflate on QSTileView (parent class)
        if (!hooked) {
            try {
                val tileViewClass = XposedHelpers.findClass(
                    "com.android.systemui.plugins.qs.QSTileView", classLoader
                )
                XposedHelpers.findAndHookMethod(
                    tileViewClass, "onFinishInflate",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            applyTileEffect(param.thisObject as View, readSettings())
                        }
                    }
                )
                hooked = true
                XposedBridge.log("$TAG: Hooked QSTileView.onFinishInflate()")
            } catch (e: Throwable) {
                XposedBridge.log("$TAG: QSTileView.onFinishInflate hook failed: ${e.message}")
            }
        }

        // Fallback: hook any View subclass named QSTile*
        if (!hooked) {
            try {
                val candidates = listOf(
                    "com.android.systemui.qs.tileimpl.QSTileViewImpl",
                    "com.android.systemui.qs.tileimpl.QSTileView",
                    "com.android.systemui.qs.tileimpl.ChipView",
                    "com.android.systemui.qs.tileimpl.SecQSTileViewImpl"
                )
                for (name in candidates) {
                    try {
                        val clazz = XposedHelpers.findClass(name, classLoader)
                        XposedHelpers.findAndHookMethod(
                            clazz, "onFinishInflate",
                            object : XC_MethodHook() {
                                override fun afterHookedMethod(param: MethodHookParam) {
                                    applyTileEffect(param.thisObject as View, readSettings())
                                }
                            }
                        )
                        hooked = true
                        XposedBridge.log("$TAG: Hooked $name.onFinishInflate()")
                        break
                    } catch (_: Throwable) {}
                }
            } catch (e: Throwable) {
                XposedBridge.log("$TAG: Candidate hooks failed: ${e.message}")
            }
        }

        if (!hooked) {
            XposedBridge.log("$TAG: WARNING - No tile hooks were successful!")
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
                            if (!settings.enabled || !settings.panelBlur) return
                            val view = param.thisObject as View
                            try {
                                view.setBackgroundColor(0x10000000.toInt())
                            } catch (_: Throwable) {}
                        }
                    }
                )
                XposedBridge.log("$TAG: Hooked $name.onLayout()")
                return
            } catch (_: Throwable) {}
        }
        XposedBridge.log("$TAG: WARNING - No panel hooks succeeded!")
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
                            XposedBridge.log("$TAG: Tile translucent (opacity=${settings.tileOpacity}%, corners=${settings.cornerRadius}dp)")
                        }
                    }
                } catch (e: Throwable) {
                    XposedBridge.log("$TAG: Background modify error: ${e.message}")
                }
            }

            // Window background blur
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
                        XposedBridge.log("$TAG: Applied blur radius ${settings.blurRadius}")
                    }
                } catch (e: Throwable) {
                    XposedBridge.log("$TAG: Blur apply error: ${e.message}")
                }
            }
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: applyTileEffect error: ${e.message}")
            XposedBridge.log(e)
        }
    }
}
