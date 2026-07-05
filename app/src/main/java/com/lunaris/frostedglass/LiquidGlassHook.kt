package com.lunaris.frostedglass

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import de.robv.android.xposed.XposedBridge

object LiquidGlassHook {

    private const val TAG = "FrostedGlassQS"

    fun applyToPowerMenu(
        cardContainer: ViewGroup,
        settings: TileBlurHook.FrostedSettings,
        moduleLoader: ClassLoader
    ): Boolean {
        return try {
            val context = cardContainer.context
            val density = context.resources.displayMetrics.density

            val lgClass = moduleLoader.loadClass("com.qmdeve.liquidglass.widget.LiquidGlassView")
            XposedBridge.log("$TAG: LiquidGlassView class loaded")

            val constructor = lgClass.getConstructor(Context::class.java)
            val lgView = constructor.newInstance(context)
            XposedBridge.log("$TAG: LiquidGlassView instance created")

            val bind = lgClass.getMethod("bind", ViewGroup::class.java)
            val setCornerRadius = lgClass.getMethod("setCornerRadius", Float::class.javaPrimitiveType!!)
            val setRefractionHeight = lgClass.getMethod("setRefractionHeight", Float::class.javaPrimitiveType!!)
            val setRefractionOffset = lgClass.getMethod("setRefractionOffset", Float::class.javaPrimitiveType!!)
            val setDispersion = lgClass.getMethod("setDispersion", Float::class.javaPrimitiveType!!)
            val setBlurRadius = lgClass.getMethod("setBlurRadius", Float::class.javaPrimitiveType!!)
            val setTintAlpha = lgClass.getMethod("setTintAlpha", Float::class.javaPrimitiveType!!)
            val setDraggable = lgClass.getMethod("setDraggableEnabled", Boolean::class.javaPrimitiveType!!)
            val setElastic = lgClass.getMethod("setElasticEnabled", Boolean::class.javaPrimitiveType!!)
            val setTouch = lgClass.getMethod("setTouchEffectEnabled", Boolean::class.javaPrimitiveType!!)
            XposedBridge.log("$TAG: LiquidGlassView methods resolved")

            val cornerPx = settings.cornerRadius * density
            setCornerRadius.invoke(lgView, cornerPx)

            val refHeightPx = settings.refractionHeight * density
            setRefractionHeight.invoke(lgView, refHeightPx)

            val refOffsetPx = settings.refractionOffset * density
            setRefractionOffset.invoke(lgView, refOffsetPx)

            val dispersion = settings.dispersion.toFloat() / 100f
            setDispersion.invoke(lgView, dispersion)

            setBlurRadius.invoke(lgView, settings.blurRadius.toFloat())

            val tintA = settings.tintAlpha.toFloat() / 100f
            setTintAlpha.invoke(lgView, tintA)

            setDraggable.invoke(lgView, false)
            setElastic.invoke(lgView, false)
            setTouch.invoke(lgView, false)

            XposedBridge.log("$TAG: LiquidGlassView configured")

            val viewClass = Class.forName("android.view.View")
            if (viewClass.isInstance(lgView)) {
                val lv = lgView as android.view.View
                lv.layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                cardContainer.addView(lv, 0)
                XposedBridge.log("$TAG: LiquidGlassView added to card container at index 0")
            } else {
                XposedBridge.log("$TAG: LiquidGlassView is not a View")
                return false
            }

            bind.invoke(lgView, cardContainer)
            XposedBridge.log("$TAG: LiquidGlassView bound to card container")

            true
        } catch (e: UnsatisfiedLinkError) {
            XposedBridge.log("$TAG: LiquidGlass native libs failed: ${e.message}")
            false
        } catch (e: ClassNotFoundException) {
            XposedBridge.log("$TAG: LiquidGlassView not found: ${e.message}")
            false
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: LiquidGlassView error: ${e.message}")
            XposedBridge.log(e)
            false
        }
    }
}
