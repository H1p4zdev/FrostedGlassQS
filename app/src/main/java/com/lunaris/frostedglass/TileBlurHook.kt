package com.lunaris.frostedglass

import android.graphics.RenderEffect
import android.graphics.Shader
import android.view.View
import android.view.ViewGroup
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

object TileBlurHook {

    private const val TAG = "FrostedGlassQS"

    // Tile view class names (Android 16)
    private val TILE_VIEW_CLASSES = listOf(
        "com.android.systemui.qs.tileimpl.QSTileViewImpl",
        "com.android.systemui.qs.tileimpl.QSTileView",
        "com.android.systemui.qs.tileimpl.QSIconViewImpl"
    )

    // Panel class names
    private val PANEL_CLASSES = listOf(
        "com.android.systemui.qs.QSPanel",
        "com.android.systemui.qs.QSPanelController",
        "com.android.systemui.qs.QSPanelControllerBase"
    )

    fun hookTiles(classLoader: ClassLoader) {
        for (className in TILE_VIEW_CLASSES) {
            try {
                val tileClass = XposedHelpers.findClass(className, classLoader)
                
                // Hook onFinishInflate - called after view is inflated
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
                
                // Hook onLayout - called when panel is laid out
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
            // Set translucent background
            view.background = BlurUtils.createFrostedDrawable(view.context)
            
            // Enable clipping for rounded corners
            view.clipToOutline = true
            
            // Apply blur effect (API 31+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val blurRadius = 10f
                val blurEffect = RenderEffect.createBlurEffect(
                    blurRadius,
                    blurRadius,
                    Shader.TileMode.CLAMP
                )
                view.setRenderEffect(blurEffect)
            }

            XposedBridge.log("$TAG: Applied frosted effect to tile")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Error applying tile effect: ${e.message}")
        }
    }

    private fun applyPanelEffect(view: View) {
        try {
            // Set panel background
            view.setBackgroundColor(0x1A000000.toInt()) // 10% black
            
            // Apply light blur to panel (API 31+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val blurRadius = 5f
                val blurEffect = RenderEffect.createBlurEffect(
                    blurRadius,
                    blurRadius,
                    Shader.TileMode.CLAMP
                )
                view.setRenderEffect(blurEffect)
            }

            XposedBridge.log("$TAG: Applied frosted effect to panel")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Error applying panel effect: ${e.message}")
        }
    }
}
