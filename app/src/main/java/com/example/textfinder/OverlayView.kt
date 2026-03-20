package com.example.textfinder

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

/**
 * OverlayView draws red bounding boxes over matched text regions.
 *
 * Coordinate mapping:
 *  - ML Kit returns bounding boxes in the coordinate space of the input image
 *    (after rotation is applied, so width/height are already "display-oriented").
 *  - PreviewView uses scaleType="fillCenter" (equivalent to CENTER_CROP):
 *    the image is scaled uniformly to fill the view, and any excess is cropped.
 *  - We replicate that transform here so boxes align with what the user sees.
 */
class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Stroke paint — red, 4dp wide
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 4f * context.resources.displayMetrics.density  // 4dp in px
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    // Fill paint — semi-transparent red
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(50, 255, 0, 0)   // ~20% opacity red
        style = Paint.Style.FILL
    }

    // Corner radius for rounded rectangles (optional, looks nicer)
    private val cornerRadius = 6f * context.resources.displayMetrics.density

    // Data from the last recognition pass
    private var boxes: List<RectF> = emptyList()
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

    // Reusable mapped rect to avoid allocations in onDraw
    private val mappedRect = RectF()

    /**
     * Update the overlay with new recognition results.
     * @param boxes      Bounding boxes in ML Kit image coordinates.
     * @param imageWidth Width of the image passed to ML Kit (rotation-corrected).
     * @param imageHeight Height of the image passed to ML Kit (rotation-corrected).
     */
    fun setResults(boxes: List<RectF>, imageWidth: Int, imageHeight: Int) {
        this.boxes = boxes
        this.imageWidth = imageWidth.coerceAtLeast(1)
        this.imageHeight = imageHeight.coerceAtLeast(1)
        invalidate()
    }

    /** Remove all boxes (e.g. when search query is cleared). */
    fun clearBoxes() {
        boxes = emptyList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (boxes.isEmpty()) return

        val viewW = width.toFloat()
        val viewH = height.toFloat()
        if (viewW <= 0f || viewH <= 0f) return

        // --- Replicate PreviewView "fillCenter" (CENTER_CROP) transform ---
        //
        // Scale factor: the larger of (viewW/imgW, viewH/imgH) so the image
        // fills the view completely (same as CENTER_CROP / fillCenter).
        val scaleX = viewW / imageWidth.toFloat()
        val scaleY = viewH / imageHeight.toFloat()
        val scale = maxOf(scaleX, scaleY)

        // After scaling, the image may be larger than the view on one axis.
        // The excess is split equally on both sides (centered).
        val scaledImgW = imageWidth * scale
        val scaledImgH = imageHeight * scale
        val offsetX = (viewW - scaledImgW) / 2f   // negative when image wider than view
        val offsetY = (viewH - scaledImgH) / 2f   // negative when image taller than view

        for (box in boxes) {
            // Map from image coords → view coords
            mappedRect.set(
                box.left   * scale + offsetX,
                box.top    * scale + offsetY,
                box.right  * scale + offsetX,
                box.bottom * scale + offsetY
            )

            // Skip boxes that are entirely outside the view
            if (mappedRect.right < 0 || mappedRect.bottom < 0 ||
                mappedRect.left > viewW || mappedRect.top > viewH
            ) continue

            // Draw semi-transparent fill first, then stroke on top
            canvas.drawRoundRect(mappedRect, cornerRadius, cornerRadius, fillPaint)
            canvas.drawRoundRect(mappedRect, cornerRadius, cornerRadius, strokePaint)
        }
    }
}
