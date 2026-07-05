package com.lunaris.frostedglass

import android.graphics.drawable.GradientDrawable
import android.os.Build
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

    /**
     * Applies frosted glass effect to QS tile
     * - Translucent background (required for blur to show through)
     * - Background blur radius (blurs what's BEHIND the view)
     * - Rounded corners
     */
    private fun applyFrostedEffect(view: View) {
        try {
            // 1. Set translucent background (alpha must be < 255 for blur to show)
            val bgDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20f * view.resources.displayMetrics.density
                setColor(0x40FFFFFF.toInt()) // 25% white - translucent
                setStroke(
                    (1 * view.resources.displayMetrics.density).toInt(),
                    0x20FFFFFF.toInt() // subtle border
                )
            }
            view.background = bgDrawable
            
            // 2. Enable clipping for rounded corners
            view.clipToOutline = true
            
            // 3. Apply BACKGROUND blur (blurs what's behind, not the view itself)
            //    API 31+ (Android 12+) required
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val blurRadius = 25f
                view.setBackgroundBlurRadius(blurRadius.toInt())
                XposedBridge.log("$TAG: Applied background blur radius: $blurRadius")
            } else {
                XposedBridge.log("$TAG: Background blur requires API 31+, current: ${Build.VERSION.SDK_INT}")
            }

            XposedBridge.log("$TAG: Applied frosted effect to tile")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Error applying tile effect: ${e.message}")
            XposedBridge.log(e)
        }
    }

    /**
     * Applies frosted glass effect to QS panel
     * - Semi-transparent background
     * - Lower blur radius for panel (subtle effect)
     */
    private fun applyPanelEffect(view: View) {
        try {
            // 1. Semi-transparent background
            view.setBackgroundColor(0x1A000000.toInt()) // 10% black
            
            // 2. Background blur for panel (lower radius for subtlety)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val blurRadius = 15f
                view.setBackgroundBlurRadius(blurRadius.toInt())
                XposedBridge.log("$TAG: Applied panel blur radius: $blurRadius")
            }

            XposedBridge.log("$TAG: Applied frosted effect to panel")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Error applying panel effect: ${e.message}")
            XposedBridge.log(e)
        }
    }
}
