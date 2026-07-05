package com.lunaris.frostedglass

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import de.robv.android.xposed.XposedBridge

object LiquidGlassHook {

    private const val TAG = "FrostedGlassQS"

    fun applyToSurface(
        surface: View,
        settings: TileBlurHook.FrostedSettings,
        moduleLoader: ClassLoader
    ): Boolean {
        if (!settings.liquidGlass) return false
        return try {
            val context = surface.context
            val density = context.resources.displayMetrics.density

            val lgClass = moduleLoader.loadClass("com.qmdeve.liquidglass.widget.LiquidGlassView")
            val constructor = lgClass.getConstructor(Context::class.java)
            val lgView = constructor.newInstance(context)

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

            val viewClass = Class.forName("android.view.View")
            if (!viewClass.isInstance(lgView)) {
                XposedBridge.log("$TAG: LiquidGlassView is not a View")
                return false
            }
            val lv = lgView as android.view.View
            lv.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )

            lv.isClickable = false
            lv.isFocusable = false
            lv.isEnabled = false

            if (surface is ViewGroup) {
                surface.addView(lv, 0)
                val source = surface.parent as? ViewGroup ?: (surface.rootView as? ViewGroup) ?: surface
                bind.invoke(lgView, source)
                XposedBridge.log("$TAG: LiquidGlassView added to ${surface.javaClass.simpleName}, bound to ${source.javaClass.simpleName}")
            } else if (surface.parent is ViewGroup) {
                val parent = surface.parent as ViewGroup
                parent.addView(lv, parent.indexOfChild(surface))
                val source = surface.rootView as? ViewGroup ?: parent
                bind.invoke(lgView, source)
                XposedBridge.log("$TAG: LiquidGlassView added before ${surface.javaClass.simpleName}, bound to ${source.javaClass.simpleName}")
            } else {
                XposedBridge.log("$TAG: Cannot find parent for ${surface.javaClass.simpleName}")
                return false
            }

            XposedBridge.log("$TAG: LiquidGlassView applied to ${surface.javaClass.simpleName}")
            true
        } catch (e: UnsatisfiedLinkError) {
            XposedBridge.log("$TAG: LiquidGlass native libs failed: ${e.message}")
            false
        } catch (e: ClassNotFoundException) {
            XposedBridge.log("$TAG: LiquidGlassView not found: ${e.message}")
            false
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: LiquidGlassView error on ${surface.javaClass.simpleName}: ${e.message}")
            XposedBridge.log(e)
            false
        }
    }
}
