package com.baseballnerd.app.ui.scores

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.baseballnerd.app.R

/**
 * Draws a baseball base diamond showing the three bases
 * (1st, 2nd, 3rd) as perfect squares rotated 45°.
 * 
 * Optimized for high visibility in both Light and Dark modes.
 */
class BaseDiamondView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var onFirst = false
    private var onSecond = false
    private var onThird = false

    private val occupiedFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFD300.toInt() // MLB Yellow
        style = Paint.Style.FILL
    }

    private val emptyFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val borderStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val basePath = Path()

    init {
        updateColors()
    }

    private fun updateColors() {
        // Use text_primary for the border to ensure maximum contrast (White in Dark, Dark in Light).
        // Use surface_elevated for the fill, which provides a subtle difference from the card background.
        emptyFill.color = ContextCompat.getColor(context, R.color.surface_elevated)
        borderStroke.color = ContextCompat.getColor(context, R.color.text_primary)
        
        // Use a substantial density-aware stroke width
        borderStroke.strokeWidth = 2.2f * resources.displayMetrics.density
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        updateColors()
    }

    fun setRunners(onFirst: Boolean, onSecond: Boolean, onThird: Boolean) {
        this.onFirst = onFirst
        this.onSecond = onSecond
        this.onThird = onThird
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Re-sync colors for theme changes
        updateColors()

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val size = minOf(w, h)
        val cx = w / 2f
        val cy = h / 2f
        
        // Adjust size to fit within the view while leaving room for the stroke
        val baseHalf = size * 0.18f
        val D = baseHalf * 1.5f // Ensure gap between bases
        val yOffset = (D / 2.5f) // Center the cluster vertically

        // 2nd base (Top)
        drawBase(canvas, cx, cy - D + yOffset, baseHalf, onSecond)
        // 1st base (Right)
        drawBase(canvas, cx + D, cy + yOffset, baseHalf, onFirst)
        // 3rd base (Left)
        drawBase(canvas, cx - D, cy + yOffset, baseHalf, onThird)
    }

    private fun drawBase(canvas: Canvas, cx: Float, cy: Float, half: Float, occupied: Boolean) {
        basePath.reset()
        basePath.moveTo(cx,          cy - half)
        basePath.lineTo(cx + half,   cy)
        basePath.lineTo(cx,          cy + half)
        basePath.lineTo(cx - half,   cy)
        basePath.close()
        
        if (occupied) {
            canvas.drawPath(basePath, occupiedFill)
        } else {
            canvas.drawPath(basePath, emptyFill)
        }
        
        // Always draw the border stroke to ensure visibility on all backgrounds
        canvas.drawPath(basePath, borderStroke)
    }
}
