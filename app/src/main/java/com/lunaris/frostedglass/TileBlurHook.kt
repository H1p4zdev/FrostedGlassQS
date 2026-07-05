package com.lunaris.frostedglass

import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.View
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

object TileBlurHook {

    private const val TAG = "FrostedGlassQS"

    private val TILE_VIEW_CLASSES = listOf(
        "com.android.systemui.qs.tileimpl.QSTileViewImpl",
        "com.android.systemui.qs.tileimpl.QSTileView",
        "com.android.systemui.qs.tileimpl.QSIconViewImpl"
    )

    private val PANEL_CLASSES = listOf(
        "com.android.systemui.qs.QSPanel",
        "com.android.systemui.qs.QSPanelController",
        "com.android.systemui.qs.QSPanelControllerBase"
    )

    // Cached method reference for setBackgroundBlurRadius
    private var blurMethod: java.lang.reflect.Method? = null
    private var blurMethodInitialized = false

    private fun getBlurMethod(): java.lang.reflect.Method? {
        if (blurMethodInitialized) return blurMethod
        blurMethodInitialized = true
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                blurMethod = View::class.java.getMethod(
                    "setBackgroundBlurRadius",
                    Int::class.javaPrimitiveType
                )
            }
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Could not find setBackgroundBlurRadius: ${e.message}")
        }
        return blurMethod
    }

    private fun applyBlur(view: View, radius: Int) {
        try {
            val method = getBlurMethod()
            if (method != null) {
                method.invoke(view, radius)
                XposedBridge.log("$TAG: Applied blur radius $radius via reflection")
            } else {
                XposedBridge.log("$TAG: setBackgroundBlurRadius not available")
            }
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Error applying blur: ${e.message}")
        }
    }

    fun hookTiles(classLoader: ClassLoader) {
        for (className in TILE_VIEW_CLASSES) {
            try {
                val tileClass = XposedHelpers.findClass(className, classLoader)

                XposedHelpers.findAndHookMethod(
                    tileClass,
                    "onFinishInflate",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            applyFrostedEffect(param.thisObject as View)
                        }
                    }
                )

                XposedBridge.log("$TAG: Hooked $className")
            } catch (e: Throwable) {
                XposedBridge.log("$TAG: Could not hook $className: ${e.message}")
            }
        }
    }

    fun hookPanel(classLoader: ClassLoader) {
        for (className in PANEL_CLASSES) {
            try {
                val panelClass = XposedHelpers.findClass(className, classLoader)

                XposedHelpers.findAndHookMethod(
                    panelClass,
                    "onLayout",
                    Boolean::class.java,
                    Int::class.java,
                    Int::class.java,
                    Int::class.java,
                    Int::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            applyPanelEffect(param.thisObject as View)
                        }
                    }
                )

                XposedBridge.log("$TAG: Hooked $className")
            } catch (e: Throwable) {
                XposedBridge.log("$TAG: Could not hook $className: ${e.message}")
            }
        }
    }

    private fun applyFrostedEffect(view: View) {
        try {
            val density = view.resources.displayMetrics.density

            // 1. Translucent background
            val bgDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20f * density
                setColor(0x40FFFFFF.toInt())
                setStroke((1 * density).toInt(), 0x20FFFFFF.toInt())
            }
            view.background = bgDrawable

            // 2. Rounded corners
            view.clipToOutline = true

            // 3. Background blur via reflection (API 31+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                applyBlur(view, 25)
            }

            XposedBridge.log("$TAG: Applied frosted effect to tile")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Error applying tile effect: ${e.message}")
            XposedBridge.log(e)
        }
    }

    private fun applyPanelEffect(view: View) {
        try {
            view.setBackgroundColor(0x1A000000.toInt())

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                applyBlur(view, 15)
            }

            XposedBridge.log("$TAG: Applied frosted effect to panel")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Error applying panel effect: ${e.message}")
            XposedBridge.log(e)
        }
    }
}
