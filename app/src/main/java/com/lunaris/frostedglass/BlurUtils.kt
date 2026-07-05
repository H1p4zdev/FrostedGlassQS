package com.lunaris.frostedglass

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RoundRectShape

object BlurUtils {

    // Configuration
    const val TILE_CORNER_RADIUS = 20f
    const val TILE_BG_COLOR = 0x40FFFFFF.toInt()      // 25% white
    const val TILE_ACTIVE_COLOR = 0x60FFFFFF.toInt()   // 37% white
    const val TILE_BORDER_COLOR = 0x20FFFFFF.toInt()   // 12% white
    const val PANEL_BG_COLOR = 0x1A000000.toInt()      // 10% black

    /**
     * Creates a frosted glass drawable for tile backgrounds
     */
    fun createFrostedDrawable(context: Context): LayerDrawable {
        // Background shape (rounded rectangle)
        val bgShape = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = TILE_CORNER_RADIUS
            setColor(TILE_BG_COLOR)
        }

        // Border shape
        val borderShape = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = TILE_CORNER_RADIUS
            setStroke(2, TILE_BORDER_COLOR)
            setColor(0x00000000.toInt()) // Transparent
        }

        // Layer them together
        return LayerDrawable(arrayOf(bgShape, borderShape))
    }

    /**
     * Creates an active state frosted drawable
     */
    fun createActiveFrostedDrawable(context: Context): LayerDrawable {
        val bgShape = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = TILE_CORNER_RADIUS
            setColor(TILE_ACTIVE_COLOR)
        }

        val borderShape = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = TILE_CORNER_RADIUS
            setStroke(2, TILE_ACTIVE_COLOR)
            setColor(0x00000000.toInt())
        }

        return LayerDrawable(arrayOf(bgShape, borderShape))
    }

    /**
     * Creates panel background drawable
     */
    fun createPanelDrawable(context: Context): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(PANEL_BG_COLOR)
        }
    }
}
