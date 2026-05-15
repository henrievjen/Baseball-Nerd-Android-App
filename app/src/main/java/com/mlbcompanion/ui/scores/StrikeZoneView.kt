package com.baseballnerd.app.ui.scores

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.baseballnerd.app.R
import com.baseballnerd.app.data.model.PitchDetail

class StrikeZoneView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val zonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val pitchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 24f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private var pitches: List<PitchDetail> = emptyList()

    // Strike zone boundaries in "plate_x" and "plate_z" coordinates
    private val zoneLeft = -0.83
    private val zoneRight = 0.83
    
    // Dynamic vertical boundaries (fallback to standard 1.5 to 3.5)
    private var zoneBottom = 1.5
    private var zoneTop = 3.5

    init {
        updateColors()
    }

    private fun updateColors() {
        // Use text_secondary for the zone outline to ensure visibility in all themes
        zonePaint.color = ContextCompat.getColor(context, R.color.text_secondary)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        updateColors()
    }

    fun setPitches(newPitches: List<PitchDetail>) {
        pitches = newPitches
        
        // Update vertical strike zone boundaries from the most recent valid pitch data.
        newPitches.findLast { it.szTop != null && it.szBottom != null }?.let { p ->
            zoneTop = p.szTop!!
            zoneBottom = p.szBottom!!
        }
        
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return
        
        // Define drawing area for the zone (centered, slightly inset)
        val pitchRadius = 18f
        // Margin ensures that a pitch exactly at the boundary isn't cut off
        val margin = pitchRadius + 2f 
        val drawWidth = w - 2 * margin
        val drawHeight = h - 2 * margin

        // Map coordinate space to pixel space
        // Standard plate domain: X [-1.5, 1.5] (3.0 units), Z [0.0, 5.0] (5.0 units)
        val scaleX = drawWidth / 3.0f
        val scaleZ = drawHeight / 5.0f
        
        // Pitcher POV: xToPx uses -x to flip the coordinate system horizontally.
        fun xToPx(x: Double): Float = (w / 2f + ((-x).toFloat() * scaleX))
        
        // Maps z = 0.0 to h - margin, z = 5.0 to margin.
        fun zToPx(z: Double): Float = (h - margin - (z.toFloat() * scaleZ))

        // Draw the strike zone box
        val left = xToPx(zoneLeft)
        val right = xToPx(zoneRight)
        val top = zToPx(zoneTop)
        val bottom = zToPx(zoneBottom)
        
        val rLeft = minOf(left, right)
        val rRight = maxOf(left, right)
        canvas.drawRect(rLeft, top, rRight, bottom, zonePaint)

        // Draw pitches
        pitches.forEach { pitch ->
            val px = pitch.px ?: return@forEach
            val pz = pitch.pz ?: return@forEach

            val cx = xToPx(px)
            val cy = zToPx(pz)

            // Clamp coordinates to stay within view bounds
            val finalCx = cx.coerceIn(margin, w - margin)
            val finalCy = cy.coerceIn(margin, h - margin)

            // Determine color based on outcome
            pitchPaint.color = getPitchColor(pitch.callCode)
            
            canvas.drawCircle(finalCx, finalCy, pitchRadius, pitchPaint)
            
            // Draw pitch number
            val textY = finalCy - ((textPaint.descent() + textPaint.ascent()) / 2)
            canvas.drawText(pitch.pitchNumber.toString(), finalCx, textY, textPaint)
        }
    }

    private fun getPitchColor(code: String): Int {
        return when (code.uppercase()) {
            "B" -> ContextCompat.getColor(context, R.color.mlb_green_safe)
            "C", "S", "W" -> ContextCompat.getColor(context, R.color.mlb_red)
            "X", "D", "E", "H" -> ContextCompat.getColor(context, R.color.mlb_gold)
            else -> ContextCompat.getColor(context, R.color.text_secondary)
        }
    }
}
