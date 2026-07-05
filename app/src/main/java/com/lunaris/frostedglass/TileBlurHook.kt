package com.lunaris.frostedglass

import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.View
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

object TileBlurHook {

    private const val TAG = "FrostedGlassQS"

    private const val TILE_VIEW_IMPL = "com.android.systemui.qs.tileimpl.QSTileViewImpl"
    private const val QS_PANEL = "com.android.systemui.qs.QSPanel"
    private const val QSTILE_PLUGIN = "com.android.systemui.plugins.qs.QSTile"

    // Reflection caches
    private var viewRootImplField: java.lang.reflect.Field? = null
    private var blurUtilsClass: Class<*>? = null
    private var blurUtilsInstance: Any? = null
    private var applyBlurMethod: java.lang.reflect.Method? = null
    private var surfaceControlField: java.lang.reflect.Field? = null

    private var initialized = false

    private fun initReflections(classLoader: ClassLoader) {
        if (initialized) return
        initialized = true

        try {
            // View.mViewRootImpl
            val viewClass = Class.forName("android.view.View")
            viewRootImplField = viewClass.getDeclaredField("mViewRootImpl")
            viewRootImplField?.isAccessible = true
            XposedBridge.log("$TAG: Found mViewRootImpl field")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Could not find mViewRootImpl: ${e.message}")
        }

        try {
            // BlurUtils singleton
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
            // BlurUtils.applyBlur(ViewRootImpl, int, boolean)
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
            // 1. Make tile background translucent
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
                            baseDrawable.setColor(0x30FFFFFF.toInt())
                            XposedBridge.log("$TAG: Made tile background translucent")
                        }
                    }
                } catch (e: Throwable) {
                    XposedBridge.log("$TAG: Error modifying background: ${e.message}")
                }
            }

            // 2. Apply blur to window
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                applyBlurToView(view, 20)
            }

            XposedBridge.log("$TAG: Applied frosted effect to tile")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Error in applyTileEffect: ${e.message}")
            XposedBridge.log(e)
        }
    }

    private fun applyPanelEffect(view: View) {
        try {
            view.setBackgroundColor(0x10000000.toInt())
            XposedBridge.log("$TAG: Applied panel effect")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Error in applyPanelEffect: ${e.message}")
        }
    }
}
