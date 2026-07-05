package com.lunaris.frostedglass

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
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
        val cornerRadius: Int = 20,
        val powerMenu: Boolean = true,
        val lockscreen: Boolean = true,
        val liquidGlass: Boolean = false,
        val refractionHeight: Int = 20,
        val refractionOffset: Int = 70,
        val dispersion: Int = 50,
        val tintAlpha: Int = 0
    )

    private fun readSettings(): FrostedSettings {
        return try {
            val file = File(SHARED_PATH)
            if (!file.exists()) return FrostedSettings()
            val xml = file.readText()
            FrostedSettings(
                enabled = parseBool(xml, "enabled", true),
                blurRadius = parseInt(xml, "blur_radius", 20),
                tileOpacity = parseInt(xml, "tile_opacity", 19),
                panelBlur = parseBool(xml, "panel_blur", true),
                cornerRadius = parseInt(xml, "corner_radius", 20),
                powerMenu = parseBool(xml, "power_menu", true),
                lockscreen = parseBool(xml, "lockscreen", true),
                liquidGlass = parseBool(xml, "liquid_glass", false),
                refractionHeight = parseInt(xml, "refraction_height", 20),
                refractionOffset = parseInt(xml, "refraction_offset", 70),
                dispersion = parseInt(xml, "dispersion", 50),
                tintAlpha = parseInt(xml, "tint_alpha", 0)
            )
        } catch (e: Throwable) {
            FrostedSettings()
        }
    }

    private fun parseBool(xml: String, key: String, default: Boolean): Boolean {
        val match = Regex("<boolean name=\"$key\" value=\"(true|false)\"").find(xml) ?: return default
        return match.groupValues[1] == "true"
    }

    private fun parseInt(xml: String, key: String, default: Int): Int {
        val match = Regex("<int name=\"$key\" value=\"(-?\\d+)\"").find(xml) ?: return default
        return match.groupValues[1].toIntOrNull() ?: default
    }

    // ==================== QS TILES ====================

    fun hookTiles(classLoader: ClassLoader) {
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
            XposedBridge.log("$TAG: Hooked QSTileViewImpl.init()")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: init hook failed: ${e.message}")
        }

        try {
            val tileViewClass = XposedHelpers.findClass(
                "com.android.systemui.qs.tileimpl.QSTileViewImpl", classLoader
            )
            XposedHelpers.findAndHookMethod(
                tileViewClass, "onAttachedToWindow",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        applyTileEffect(param.thisObject as View, readSettings())
                    }
                }
            )
            XposedBridge.log("$TAG: Hooked QSTileViewImpl.onAttachedToWindow()")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: onAttachedToWindow hook failed: ${e.message}")
        }
    }

    fun hookPanel(classLoader: ClassLoader) {
        val moduleLoader = TileBlurHook::class.java.classLoader
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
                            if (!settings.enabled) return
                            val panel = param.thisObject as View
                            applyPanelEffect(panel, settings, moduleLoader)
                            walkAndApplyTiles(panel, settings, moduleLoader)
                        }
                    }
                )
                XposedBridge.log("$TAG: Hooked $name.onLayout()")
                return
            } catch (_: Throwable) {}
        }
        XposedBridge.log("$TAG: WARNING - No panel hooks succeeded!")
    }

    // ========================================================================
    // POWER MENU — OEM Redesign (HyperOS / iOS / ColorOS style)
    // ========================================================================

    fun hookPowerMenu(classLoader: ClassLoader) {
        val moduleLoader = TileBlurHook::class.java.classLoader

        // Hook initializeLayout for window-level blur + dim
        try {
            val dialogClass = XposedHelpers.findClass(
                "com.android.systemui.globalactions.GlobalActionsDialogLite\$ActionsDialogLite",
                classLoader
            )
            XposedHelpers.findAndHookMethod(
                dialogClass, "initializeLayout",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val settings = readSettings()
                        if (!settings.enabled || !settings.powerMenu) return
                        try {
                            val dialog = param.thisObject
                            val getWindow = dialog.javaClass.getMethod("getWindow")
                            val window = getWindow.invoke(dialog) as? Window ?: return

                            // If liquid glass is enabled, DON'T add FLAG_BLUR_BEHIND
                            // (LiquidGlassView handles its own sampling)
                            if (settings.liquidGlass) {
                                // Keep window transparent, no system blur
                                window.setDimAmount(0.7f)
                                window.setBackgroundDrawable(null)
                                XposedBridge.log("$TAG: Power menu window setup (liquid glass mode)")
                            } else {
                                // Heavy blur behind (like iOS)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                                    window.attributes.blurBehindRadius = 120
                                }
                                // Dark dim
                                window.setDimAmount(0.7f)
                                // Transparent window background (removes ScrimDrawable)
                                window.setBackgroundDrawable(null)
                                XposedBridge.log("$TAG: Power menu OEM window setup done")
                            }
                        } catch (e: Throwable) {
                            XposedBridge.log("$TAG: Power menu window error: ${e.message}")
                        }
                    }
                }
            )
            XposedBridge.log("$TAG: Hooked GlobalActionsDialogLite.initializeLayout()")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Power menu dialog hook failed: ${e.message}")
        }

        // Hook onLayout for card + item restyling
        try {
            val layoutClass = XposedHelpers.findClass(
                "com.android.systemui.globalactions.GlobalActionsLayoutLite", classLoader
            )
            XposedHelpers.findAndHookMethod(
                layoutClass, "onLayout",
                Boolean::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val settings = readSettings()
                        if (!settings.enabled || !settings.powerMenu) return
                        val layout = param.thisObject as ViewGroup
                        applyPowerMenuOemStyle(layout, settings, moduleLoader)
                    }
                }
            )
            XposedBridge.log("$TAG: Hooked GlobalActionsLayoutLite.onLayout()")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Power menu layout hook failed: ${e.message}")
        }
    }

    private fun applyPowerMenuOemStyle(layout: ViewGroup, settings: FrostedSettings, moduleLoader: ClassLoader) {
        try {
            val density = layout.resources.displayMetrics.density
            val cardBgColor = 0xE00F0F1E.toInt()  // dark glass card
            val cardCorner = (36 * density).toInt()

            // Find the card container via getListView()
            val listView = XposedHelpers.callMethod(layout, "getListView") as? ViewGroup ?: return

            // === 1. CARD BACKGROUND ===
            if (settings.liquidGlass) {
                val lgSuccess = LiquidGlassHook.applyToSurface(listView, settings, moduleLoader)
                if (lgSuccess) {
                    XposedBridge.log("$TAG: LiquidGlassView applied to power menu")
                } else {
                    XposedBridge.log("$TAG: LiquidGlassView failed, using fallback")
                    listView.background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        setCornerRadius(cardCorner.toFloat())
                        setColor(cardBgColor)
                    }
                }
            } else {
                listView.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setCornerRadius(cardCorner.toFloat())
                    setColor(cardBgColor)
                }
            }

            // Add internal padding for items
            listView.setPadding(
                (20 * density).toInt(),
                (24 * density).toInt(),
                (20 * density).toInt(),
                (24 * density).toInt()
            )

            // === 2. STYLE EACH ACTION ITEM ===
            val icons = arrayOf(
                "ic_power", "ic_restart", "ic_screenshot", "ic_emergency",
                "ic_lock", "ic_airplane", "ic_settings", "ic_user"
            )

            walkViews(listView) { child ->
                if (child.javaClass.name.contains("GlobalActionsItem")) {
                    val item = child as ViewGroup

                    // Style the item container
                    item.setPadding(
                        (6 * density).toInt(), (6 * density).toInt(),
                        (6 * density).toInt(), (6 * density).toInt()
                    )
                    item.setBackgroundColor(Color.TRANSPARENT)

                    // Find icon + text
                    val icon = item.findViewById<ImageView>(android.R.id.icon)
                    val text = item.findViewById<TextView>(android.R.id.message)

                    if (icon != null) {
                        val iconSize = (52 * density).toInt()
                        val iconCorner = (14 * density).toInt()

                        // Assign a vibrant accent color based on item type
                        val accentColor = when {
                            text == null -> 0xFF6C63FF.toInt()
                            text.text?.contains("off", true) == true -> 0xFFFF4B4B.toInt()
                            text.text?.contains("restart", true) == true -> 0xFF4CAF50.toInt()
                            text.text?.contains("screenshot", true) == true -> 0xFF2196F3.toInt()
                            text.text?.contains("emergency", true) == true -> 0xFFFF9800.toInt()
                            else -> 0xFF6C63FF.toInt()
                        }

                        // Modern glass button background
                        icon.background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            setCornerRadius(iconCorner.toFloat())
                            setColor(accentColor and 0x66FFFFFF.toInt() or 0x22000000.toInt())
                        }

                        // Larger icon, white tinted
                        icon.layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                            gravity = Gravity.CENTER_HORIZONTAL
                            bottomMargin = (4 * density).toInt()
                        }
                        icon.setPadding(
                            (14 * density).toInt(), (14 * density).toInt(),
                            (14 * density).toInt(), (14 * density).toInt()
                        )
                        icon.setColorFilter(Color.WHITE)
                        icon.scaleType = ImageView.ScaleType.CENTER_CROP
                    }

                    if (text != null) {
                        text.setTextColor(Color.WHITE)
                        text.textSize = 13f
                        text.gravity = Gravity.CENTER
                        text.ellipsize = null
                        text.maxLines = 1
                    }

                    XposedBridge.log("$TAG: Power menu OEM-styled")
                }
                false
            }

            XposedBridge.log("$TAG: Power menu OEM redesign applied")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Power menu OEM error: ${e.message}")
            XposedBridge.log(e)
        }
    }

    // ==================== LOCKSCREEN ====================

    fun hookLockscreen(classLoader: ClassLoader) {
        val moduleLoader = TileBlurHook::class.java.classLoader

        try {
            val statusBarClass = XposedHelpers.findClass(
                "com.android.systemui.statusbar.phone.KeyguardStatusBarView", classLoader
            )
            XposedHelpers.findAndHookMethod(
                statusBarClass, "onFinishInflate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val settings = readSettings()
                        if (!settings.enabled || !settings.lockscreen) return
                        applyLockscreenHeaderEffect(param.thisObject as View, settings, moduleLoader)
                    }
                }
            )
            XposedBridge.log("$TAG: Hooked KeyguardStatusBarView.onFinishInflate()")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Lockscreen header hook failed: ${e.message}")
        }

        try {
            val indicationClass = XposedHelpers.findClass(
                "com.android.systemui.keyguard.ui.view.KeyguardIndicationArea", classLoader
            )
            XposedHelpers.findAndHookMethod(
                indicationClass, "onFinishInflate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val settings = readSettings()
                        if (!settings.enabled || !settings.lockscreen) return
                        applyLockscreenFooterEffect(param.thisObject as View, settings, moduleLoader)
                    }
                }
            )
            XposedBridge.log("$TAG: Hooked KeyguardIndicationArea.onFinishInflate()")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Lockscreen footer hook failed: ${e.message}")
        }

        try {
            val rootClass = XposedHelpers.findClass(
                "com.android.systemui.keyguard.ui.view.KeyguardRootView", classLoader
            )
            XposedHelpers.findAndHookMethod(
                rootClass, "onLayout",
                Boolean::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val settings = readSettings()
                        if (!settings.enabled || !settings.lockscreen) return
                        val root = param.thisObject as View
                        walkViews(root) { child ->
                            val cn = child.javaClass.name
                            if (cn.contains("KeyguardStatusBar") || cn.contains("IndicationArea")) {
                                applyFrostedBackground(child, settings, moduleLoader)
                            }
                            false
                        }
                    }
                }
            )
            XposedBridge.log("$TAG: Hooked KeyguardRootView.onLayout()")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: KeyguardRootView hook failed: ${e.message}")
        }
    }

    private fun applyLockscreenHeaderEffect(view: View, settings: FrostedSettings, moduleLoader: ClassLoader) {
        try {
            if (settings.liquidGlass && view is ViewGroup) {
                if (LiquidGlassHook.applyToSurface(view, settings, moduleLoader)) return
            }
            view.setBackgroundColor(0x18FFFFFF.toInt())
            XposedBridge.log("$TAG: Lockscreen header styled")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Lockscreen header error: ${e.message}")
        }
    }

    private fun applyLockscreenFooterEffect(view: View, settings: FrostedSettings, moduleLoader: ClassLoader) {
        try {
            if (settings.liquidGlass && view is ViewGroup) {
                if (LiquidGlassHook.applyToSurface(view, settings, moduleLoader)) return
            }
            val density = view.resources.displayMetrics.density
            val opacity = settings.tileOpacity.toFloat() / 100f
            val bgColor = ((255 * opacity * 0.5f).toInt() shl 24) or 0x1A1A2E
            val cornerRadius = settings.cornerRadius * density

            view.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setCornerRadius(cornerRadius)
                setColor(bgColor)
            }
            XposedBridge.log("$TAG: Lockscreen footer styled")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Lockscreen footer error: ${e.message}")
        }
    }

    private fun applyFrostedBackground(view: View, settings: FrostedSettings, moduleLoader: ClassLoader) {
        try {
            if (settings.liquidGlass && view is ViewGroup) {
                if (LiquidGlassHook.applyToSurface(view, settings, moduleLoader)) return
            }
            val density = view.resources.displayMetrics.density
            val bgColor = 0x10FFFFFF
            val cornerRadius = settings.cornerRadius * density
            view.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setCornerRadius(cornerRadius)
                setColor(bgColor)
            }
        } catch (_: Throwable) {}
    }

    // ==================== DIALOGS ====================

    fun hookDialogs(classLoader: ClassLoader) {
        val moduleLoader = TileBlurHook::class.java.classLoader
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Dialog", classLoader, "show",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val dialog = param.thisObject
                            val window = dialog.javaClass.getMethod("getWindow").invoke(dialog) as? Window ?: return
                            val ctx = window.context ?: return
                            if (ctx.packageName != "com.android.systemui") return
                            val view = window.decorView as? ViewGroup ?: return
                            val settings = readSettings()
                            if (!settings.enabled || !settings.liquidGlass) return
                            LiquidGlassHook.applyToSurface(view, settings, moduleLoader)
                        } catch (_: Throwable) {}
                    }
                }
            )
            XposedBridge.log("$TAG: Hooked Dialog.show()")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: Dialog hook failed: ${e.message}")
        }
    }

    // ==================== SHARED UTILS ====================

    private fun walkViews(view: View, visitor: (View) -> Boolean) {
        if (visitor(view)) return
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                walkViews(view.getChildAt(i), visitor)
            }
        }
    }

    private fun walkAndApplyTiles(view: View, settings: FrostedSettings, moduleLoader: ClassLoader) {
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                val className = child.javaClass.name
                if (className.contains("QSTileView") || className.contains("TileView")) {
                    applyTileEffect(child, settings)
                }
                if (child is ViewGroup) {
                    walkAndApplyTiles(child, settings, moduleLoader)
                }
            }
        }
    }

    private fun applyPanelEffect(panel: View, settings: FrostedSettings, moduleLoader: ClassLoader) {
        if (!settings.panelBlur) return
        if (settings.liquidGlass && panel is ViewGroup) {
            if (LiquidGlassHook.applyToSurface(panel, settings, moduleLoader)) return
        }
        try {
            panel.setBackgroundColor(0x08000000.toInt())
        } catch (_: Throwable) {}
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
                            XposedBridge.log("$TAG: Tile modified (opacity=${settings.tileOpacity}%, corners=${settings.cornerRadius}dp)")
                        }
                    }
                } catch (e: Throwable) {
                    XposedBridge.log("$TAG: BG modify error: ${e.message}")
                }
            }

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
                        XposedBridge.log("$TAG: Blur applied (radius=${settings.blurRadius})")
                    }
                } catch (e: Throwable) {
                    XposedBridge.log("$TAG: Blur error: ${e.message}")
                }
            }
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: applyTileEffect error: ${e.message}")
        }
    }
}
