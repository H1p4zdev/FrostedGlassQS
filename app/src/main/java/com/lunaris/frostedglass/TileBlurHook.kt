package com.lunaris.frostedglass

import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.provider.Settings
import android.view.View
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

object TileBlurHook {

    private const val TAG = "FrostedGlassQS"

    private const val TILE_VIEW_IMPL = "com.android.systemui.qs.tileimpl.QSTileViewImpl"
    private const val QS_PANEL = "com.android.systemui.qs.QSPanel"
    private const val QSTILE_PLUGIN = "com.android.systemui.plugins.qs.QSTile"

    private const val KEY_ENABLED = "frosted_glass_enabled"
    private const val KEY_BLUR_RADIUS = "frosted_glass_blur_radius"
    private const val KEY_TILE_OPACITY = "frosted_glass_tile_opacity"
    private const val KEY_PANEL_BLUR = "frosted_glass_panel_blur"
    private const val KEY_CORNER_RADIUS = "frosted_glass_corner_radius"

    private var viewRootImplField: java.lang.reflect.Field? = null
    private var blurUtilsClass: Class<*>? = null
    private var blurUtilsInstance: Any? = null
    private var applyBlurMethod: java.lang.reflect.Method? = null
    private var initialized = false

    private fun initReflections(classLoader: ClassLoader) {
        if (initialized) return
        initialized = true

        try {
            val viewClass = Class.forName("android.view.View")
            viewRootImplField = viewClass.getDeclaredField("mViewRootImpl")
            viewRootImplField?.isAccessible = true
            XposedBridge.log("$TAG: Found mViewRootImpl field")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Could not find mViewRootImpl: ${e.message}")
        }

        try {
            blurUtilsClass = XposedHelpers.findClass(
                "com.android.systemui.statusbar.BlurUtils", classLoader
            )
            val getInstanceMethod = blurUtilsClass?.getDeclaredMethod("getBlurUtilsInstance")
            blurUtilsInstance = getInstanceMethod?.invoke(null)
            XposedBridge.log("$TAG: Found BlurUtils instance: ${blurUtilsInstance != null}")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Could not find BlurUtils: ${e.message}")
        }

        try {
            applyBlurMethod = blurUtilsClass?.declaredMethods?.find { method ->
                method.name == "applyBlur" && method.parameterCount == 3
            }
            applyBlurMethod?.isAccessible = true
            XposedBridge.log("$TAG: Found applyBlur method: ${applyBlurMethod != null}")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Could not find applyBlur: ${e.message}")
        }
    }

    private fun applyBlurToView(view: View, radius: Int) {
        try {
            val viewRoot = viewRootImplField?.get(view) ?: return
            val blurUtils = blurUtilsInstance ?: return
            val method = applyBlurMethod ?: return
            method.invoke(blurUtils, viewRoot, radius, false)
            XposedBridge.log("$TAG: Applied blur radius $radius")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Error applying blur: ${e.message}")
        }
    }

    private fun readSettings(view: View): FrostedSettings {
        return try {
            val ctx = view.context
            val resolver = ctx.contentResolver

            val enabled = Settings.Global.getInt(resolver, KEY_ENABLED, 1) == 1
            val blurRadius = Settings.Global.getInt(resolver, KEY_BLUR_RADIUS, 20)
            val tileOpacity = Settings.Global.getInt(resolver, KEY_TILE_OPACITY, 19)
            val panelBlur = Settings.Global.getInt(resolver, KEY_PANEL_BLUR, 1) == 1
            val cornerRadius = Settings.Global.getInt(resolver, KEY_CORNER_RADIUS, 20)

            FrostedSettings(enabled, blurRadius, tileOpacity, panelBlur, cornerRadius)
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Could not read settings, using defaults: ${e.message}")
            FrostedSettings()
        }
    }

    data class FrostedSettings(
        val enabled: Boolean = true,
        val blurRadius: Int = 20,
        val tileOpacity: Int = 19,
        val panelBlur: Boolean = true,
        val cornerRadius: Int = 20
    )

    fun hookTiles(classLoader: ClassLoader) {
        initReflections(classLoader)

        try {
            val tileViewClass = XposedHelpers.findClass(TILE_VIEW_IMPL, classLoader)
            val qstileClass = XposedHelpers.findClass(QSTILE_PLUGIN, classLoader)

            XposedHelpers.findAndHookMethod(
                tileViewClass,
                "init",
                qstileClass,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val view = param.thisObject as View
                        applyTileEffect(view)
                    }
                }
            )

            XposedBridge.log("$TAG: Hooked $TILE_VIEW_IMPL.init()")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Error hooking tiles: ${e.message}")
            XposedBridge.log(e)
        }
    }

    fun hookPanel(classLoader: ClassLoader) {
        try {
            val panelClass = XposedHelpers.findClass(QS_PANEL, classLoader)

            XposedHelpers.findAndHookMethod(
                panelClass,
                "onLayout",
                Boolean::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val view = param.thisObject as View
                        applyPanelEffect(view)
                    }
                }
            )

            XposedBridge.log("$TAG: Hooked $QS_PANEL.onLayout()")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Error hooking panel: ${e.message}")
        }
    }

    private fun applyTileEffect(view: View) {
        try {
            val settings = readSettings(view)
            if (!settings.enabled) {
                XposedBridge.log("$TAG: Module disabled by user")
                return
            }

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
                            XposedBridge.log("$TAG: Made tile translucent (opacity=${settings.tileOpacity}%)")
                        }
                    }
                } catch (e: Throwable) {
                    XposedBridge.log("$TAG: Error modifying background: ${e.message}")
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && settings.blurRadius > 0) {
                applyBlurToView(view, settings.blurRadius)
            }

            XposedBridge.log("$TAG: Applied frosted effect (blur=${settings.blurRadius}, opacity=${settings.tileOpacity}%)")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Error in applyTileEffect: ${e.message}")
            XposedBridge.log(e)
        }
    }

    private fun applyPanelEffect(view: View) {
        try {
            val settings = readSettings(view)
            if (!settings.enabled || !settings.panelBlur) return

            view.setBackgroundColor(0x10000000.toInt())
            XposedBridge.log("$TAG: Applied panel effect")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Error in applyPanelEffect: ${e.message}")
        }
    }
}
