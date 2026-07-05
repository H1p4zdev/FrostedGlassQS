package com.lunaris.frostedglass

import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.View
import android.view.ViewRootImpl
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

object TileBlurHook {

    private const val TAG = "FrostedGlassQS"

    // Correct class names from Lunaris AOSP source
    private const val TILE_VIEW_IMPL = "com.android.systemui.qs.tileimpl.QSTileViewImpl"
    private const val QS_PANEL = "com.android.systemui.qs.QSPanel"
    private const val QS_PANEL_CONTROLLER_BASE = "com.android.systemui.qs.QSPanelControllerBase"

    // Reflection caches
    private var viewRootImplField: java.lang.reflect.Field? = null
    private var blurUtilsClass: Class<*>? = null
    private var blurUtilsInstance: Any? = null
    private var applyBlurMethod: java.lang.reflect.Method? = null

    private fun initReflections(classLoader: ClassLoader) {
        try {
            // ViewRootImpl field
            viewRootImplField = View::class.java.getDeclaredField("mViewRootImpl")
            viewRootImplField?.isAccessible = true

            // BlurUtils singleton
            blurUtilsClass = XposedHelpers.findClass(
                "com.android.systemui.statusbar.BlurUtils", classLoader
            )
            val getInstanceMethod = blurUtilsClass?.getDeclaredMethod("getBlurUtilsInstance")
            blurUtilsInstance = getInstanceMethod?.invoke(null)

            // applyBlur method
            applyBlurMethod = blurUtilsClass?.getDeclaredMethod(
                "applyBlur",
                ViewRootImpl::class.java,
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType
            )

            XposedBridge.log("$TAG: Reflections initialized successfully")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Error initializing reflections: ${e.message}")
        }
    }

    private fun getViewRootImpl(view: View): ViewRootImpl? {
        return try {
            viewRootImplField?.get(view) as? ViewRootImpl
        } catch (e: Throwable) {
            null
        }
    }

    private fun applyBlurToWindow(view: View, radius: Int) {
        try {
            val viewRoot = getViewRootImpl(view) ?: return
            val blurUtils = blurUtilsInstance ?: return
            val method = applyBlurMethod ?: return

            method.invoke(blurUtils, viewRoot, radius, false)
            XposedBridge.log("$TAG: Applied blur radius $radius to window")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Error applying blur: ${e.message}")
        }
    }

    fun hookTiles(classLoader: ClassLoader) {
        initReflections(classLoader)

        try {
            val tileViewClass = XposedHelpers.findClass(TILE_VIEW_IMPL, classLoader)

            // Hook init(QSTile) - called when tile is initialized
            XposedHelpers.findAndHookMethod(
                tileViewClass,
                "init",
                XposedHelpers.findClass("com.android.systemui.plugins.qs.QSTile", classLoader),
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val view = param.thisObject as View
                        applyFrostedEffect(view)
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

            // Hook onLayout
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

    /**
     * Apply frosted glass effect to a QS tile
     */
    private fun applyFrostedEffect(view: View) {
        try {
            val density = view.resources.displayMetrics.density

            // 1. Get the background drawable and make it translucent
            val bg = view.background
            if (bg is RippleDrawable) {
                // Find the background layer
                val backgroundLayer = bg.findDrawableByLayerId(
                    view.resources.getIdentifier("background", "id", "android")
                ) ?: bg.findDrawableByLayerId(
                    view.resources.getIdentifier("background", "id", view.context.packageName)
                )

                if (backgroundLayer is LayerDrawable) {
                    // Find the base background shape
                    val baseId = view.resources.getIdentifier(
                        "qs_tile_background_base", "id", view.context.packageName
                    )
                    val baseDrawable = backgroundLayer.findDrawableByLayerId(baseId)
                    if (baseDrawable is GradientDrawable) {
                        // Make it translucent
                        baseDrawable.setColor(0x30FFFFFF.toInt()) // ~19% white
                    }
                }
            } else if (bg is GradientDrawable) {
                bg.setColor(0x30FFFFFF.toInt())
            }

            // 2. Apply blur to the tile's window
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                applyBlurToWindow(view, 20)
            }

            XposedBridge.log("$TAG: Applied frosted effect to tile")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Error applying tile effect: ${e.message}")
            XposedBridge.log(e)
        }
    }

    /**
     * Apply frosted glass effect to the QS panel
     */
    private fun applyPanelEffect(view: View) {
        try {
            // Make panel background semi-transparent
            view.setBackgroundColor(0x10000000.toInt()) // ~6% black

            XposedBridge.log("$TAG: Applied frosted effect to panel")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Error applying panel effect: ${e.message}")
        }
    }
}
