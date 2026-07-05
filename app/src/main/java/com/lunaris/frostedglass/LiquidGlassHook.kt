package com.lunaris.frostedglass

import android.content.Context
import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import de.robv.android.xposed.XposedBridge

object LiquidGlassHook {

    private const val TAG = "FrostedGlassQS"

    fun applyToPowerMenu(
        card: ViewGroup,
        settings: TileBlurHook.FrostedSettings,
        moduleLoader: ClassLoader
    ): Boolean {
        if (!settings.liquidGlass) return false
        return try {
            val context = card.context
            val density = context.resources.displayMetrics.density

            // 1. Detach card from parent
            val parent = card.parent as? ViewGroup ?: return false
            val cardIndex = parent.indexOfChild(card)
            if (cardIndex < 0) return false
            val cardLp = card.layoutParams

            parent.removeView(card)

            // 2. Create FrameLayout wrapper
            val wrapper = FrameLayout(context)
            wrapper.layoutParams = cardLp

            // 3. Add card to wrapper at index 0 (will be bind source)
            wrapper.addView(card, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))

            // 4. Create LiquidGlassView via reflection
            val lgClass = moduleLoader.loadClass("com.qmdeve.liquidglass.widget.LiquidGlassView")
            val constructor = lgClass.getConstructor(Context::class.java)
            val lgView = constructor.newInstance(context) as View

            lgView.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            lgView.isClickable = false
            lgView.isFocusable = false
            lgView.isEnabled = false

            // 5. Configure visual params via reflection
            val setCornerRadius = lgClass.getMethod("setCornerRadius", Float::class.javaPrimitiveType!!)
            val setRefractionHeight = lgClass.getMethod("setRefractionHeight", Float::class.javaPrimitiveType!!)
            val setRefractionOffset = lgClass.getMethod("setRefractionOffset", Float::class.javaPrimitiveType!!)
            val setDispersion = lgClass.getMethod("setDispersion", Float::class.javaPrimitiveType!!)
            val setBlurRadius = lgClass.getMethod("setBlurRadius", Float::class.javaPrimitiveType!!)
            val setTintAlpha = lgClass.getMethod("setTintAlpha", Float::class.javaPrimitiveType!!)
            val setDraggable = lgClass.getMethod("setDraggableEnabled", Boolean::class.javaPrimitiveType!!)
            val setElastic = lgClass.getMethod("setElasticEnabled", Boolean::class.javaPrimitiveType!!)
            val setTouch = lgClass.getMethod("setTouchEffectEnabled", Boolean::class.javaPrimitiveType!!)
            val bind = lgClass.getMethod("bind", ViewGroup::class.java)

            setCornerRadius.invoke(lgView, settings.cornerRadius * density)
            setRefractionHeight.invoke(lgView, settings.refractionHeight * density)
            setRefractionOffset.invoke(lgView, settings.refractionOffset * density)
            setDispersion.invoke(lgView, settings.dispersion.toFloat() / 100f)
            setBlurRadius.invoke(lgView, settings.blurRadius.toFloat())
            setTintAlpha.invoke(lgView, settings.tintAlpha.toFloat() / 100f)
            setDraggable.invoke(lgView, false)
            setElastic.invoke(lgView, false)
            setTouch.invoke(lgView, false)

            // 6. Add LiquidGlassView ON TOP of card (index 1, drawn last)
            wrapper.addView(lgView)

            // 7. Insert wrapper back into parent at original position
            parent.addView(wrapper, cardIndex)

            // 8. Card background transparent — reveals dimmed content behind
            card.background = null

            // 9. Bind LiquidGlassView to the card content
            bind.invoke(lgView, card)

            XposedBridge.log("$TAG: LiquidGlassView power menu — sibling overlay, bound to card")
            true
        } catch (e: UnsatisfiedLinkError) {
            XposedBridge.log("$TAG: LiquidGlass native libs failed: ${e.message}")
            false
        } catch (e: ClassNotFoundException) {
            XposedBridge.log("$TAG: LiquidGlassView not found: ${e.message}")
            false
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: LiquidGlassView error on power menu: ${e.message}")
            XposedBridge.log(e)
            false
        }
    }
}
