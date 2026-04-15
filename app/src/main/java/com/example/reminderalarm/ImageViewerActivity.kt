package com.example.reminderalarm

import android.graphics.Matrix
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.reminderalarm.databinding.ActivityImageViewerBinding
import kotlin.math.max
import kotlin.math.min

/**
 * Full-screen photo viewer for reminder attachments. Pinch to zoom,
 * drag to pan, double-tap to reset. Tap ✕ or the system back button
 * to close.
 *
 * The bitmap is loaded at a generous 1600 px max dimension — big
 * enough to look sharp on any modern phone without blowing up memory
 * for a 40 MP camera shot.
 */
class ImageViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImageViewerBinding
    private val matrix = Matrix()
    private val savedMatrix = Matrix()

    // Simple fling / pan state
    private var mode = NONE
    private var startX = 0f
    private var startY = 0f
    private var minScale = 1f
    private var maxScale = 6f

    private lateinit var scaleDetector: ScaleGestureDetector
    private lateinit var tapDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImageViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val path = intent.getStringExtra(EXTRA_PATH) ?: run { finish(); return }
        val bitmap = ImageLoader.loadSampled(this, path, 1600) ?: run { finish(); return }
        binding.image.setImageBitmap(bitmap)
        binding.image.imageMatrix = matrix

        binding.btnClose.setOnClickListener { finish() }

        scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scale = detector.scaleFactor
                val values = FloatArray(9)
                matrix.getValues(values)
                val current = values[Matrix.MSCALE_X]
                val target = (current * scale).coerceIn(minScale, maxScale)
                val applied = target / current
                matrix.postScale(applied, applied, detector.focusX, detector.focusY)
                binding.image.imageMatrix = matrix
                return true
            }
        })

        tapDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                // Reset to fit-screen on double-tap
                fitToScreen()
                return true
            }
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                binding.hint.visibility = if (binding.hint.visibility == View.VISIBLE)
                    View.GONE else View.VISIBLE
                return true
            }
        })

        binding.image.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            tapDetector.onTouchEvent(event)
            handlePan(event)
            true
        }

        binding.image.post { fitToScreen() }
    }

    private fun handlePan(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                savedMatrix.set(matrix)
                startX = event.x; startY = event.y
                mode = DRAG
            }
            MotionEvent.ACTION_POINTER_DOWN -> mode = ZOOM
            MotionEvent.ACTION_MOVE -> if (mode == DRAG) {
                matrix.set(savedMatrix)
                matrix.postTranslate(event.x - startX, event.y - startY)
                binding.image.imageMatrix = matrix
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> mode = NONE
        }
    }

    private fun fitToScreen() {
        val drawable = binding.image.drawable ?: return
        val bw = drawable.intrinsicWidth.toFloat()
        val bh = drawable.intrinsicHeight.toFloat()
        val vw = binding.image.width.toFloat()
        val vh = binding.image.height.toFloat()
        if (bw <= 0 || bh <= 0 || vw <= 0 || vh <= 0) return
        val scale = min(vw / bw, vh / bh)
        minScale = scale * 0.5f
        maxScale = scale * 8f
        matrix.reset()
        matrix.postScale(scale, scale)
        val dx = (vw - bw * scale) / 2f
        val dy = (vh - bh * scale) / 2f
        matrix.postTranslate(dx, dy)
        binding.image.imageMatrix = matrix
    }

    companion object {
        const val EXTRA_PATH = "path"
        private const val NONE = 0
        private const val DRAG = 1
        private const val ZOOM = 2
    }
}
