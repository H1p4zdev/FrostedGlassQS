package com.lunaris.frostedglass

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable

object BlurUtils {

    // ==================== CONFIGURATION ====================
    
    // Blur settings (Android 12+ required for background blur)
    const val TILE_BLUR_RADIUS = 25       // Radius for tile background blur
    const val PANEL_BLUR_RADIUS = 15      // Radius for panel background blur
    const val HEADER_BLUR_RADIUS = 20     // Radius for header background blur
    
    // Tile appearance
    const val TILE_CORNER_RADIUS = 20f    // Corner radius in dp
    const val TILE_BG_COLOR = 0x40FFFFFF.toInt()      // 25% white (translucent)
    const val TILE_ACTIVE_COLOR = 0x60FFFFFF.toInt()   // 37% white (more opaque when active)
    const val TILE_BORDER_COLOR = 0x20FFFFFF.toInt()   // 12% white border
    const val TILE_BORDER_WIDTH = 1       // Border width in dp
    
    // Panel appearance
    const val PANEL_BG_COLOR = 0x1A000000.toInt()      // 10% black
    const val PANEL_CORNER_RADIUS = 0f    // Panel corners (0 = no rounding)
    
    // Header appearance
    const val HEADER_BG_COLOR = 0x33000000.toInt()     // 20% black

    // ==================== DRAWABLE FACTORY ====================

    /**
     * Creates a frosted glass drawable for tile backgrounds
     * 
     * @param context Android context for density
     * @param active Whether to use active state colors
     */
    fun createFrostedDrawable(context: Context, active: Boolean = false): GradientDrawable {
        val density = context.resources.displayMetrics.density
        val color = if (active) TILE_ACTIVE_COLOR else TILE_BG_COLOR
        val borderColor = if (active) TILE_ACTIVE_COLOR else TILE_BORDER_COLOR
        
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = TILE_CORNER_RADIUS * density
            setColor(color)
            setStroke((TILE_BORDER_WIDTH * density).toInt(), borderColor)
        }
    }

    /**
     * Creates panel background drawable with optional blur support
     */
    fun createPanelDrawable(context: Context): GradientDrawable {
        val density = context.resources.displayMetrics.density
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = PANEL_CORNER_RADIUS * density
            setColor(PANEL_BG_COLOR)
        }
    }

    /**
     * Creates header background drawable
     */
    fun createHeaderDrawable(context: Context): GradientDrawable {
        val density = context.resources.displayMetrics.density
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = PANEL_CORNER_RADIUS * density
            setColor(HEADER_BG_COLOR)
        }
    }

    // ==================== HELPER FUNCTIONS ====================

    /**
     * Converts dp to pixels
     */
    fun dpToPx(context: Context, dp: Float): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    /**
     * Checks if background blur is supported (API 31+)
     */
    fun isBackgroundBlurSupported(): Boolean {
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
    }

    /**
     * Returns the appropriate blur radius for the current API level
     * Returns 0 if blur is not supported
     */
    fun getBlurRadius(desiredRadius: Int): Int {
        return if (isBackgroundBlurSupported()) desiredRadius else 0
    }
}
