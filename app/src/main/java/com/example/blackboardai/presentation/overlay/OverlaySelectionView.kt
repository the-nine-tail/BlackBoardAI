package com.example.blackboardai.presentation.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.PathMeasure
import android.media.projection.MediaProjection
import android.util.AttributeSet
import android.util.Log
import android.view.*
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.blackboardai.R
import com.example.blackboardai.data.ai.GoogleAIService
import kotlinx.coroutines.*

class OverlaySelectionView @JvmOverloads constructor(
    context: Context,
    private val mediaProjection: MediaProjection?,
    private val googleAIService: GoogleAIService,
    private val onCloseListener: () -> Unit,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    
    companion object {
        private const val TAG = "[OverlaySelection]"
        private const val OVERLAY_ALPHA = 120 // Semi-transparent gray
    }
    
    private val selectionPath = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    private var isDrawing = false
    private var selectionBounds = RectF()
    private var hasSelection = false
    private var lastX = 0f
    private var lastY = 0f
    
    // UI Components
    private val instructionText: TextView
    private val solveButton: Button
    private val closeButton: ImageButton
    private val progressBar: ProgressBar
    private val answerModal: OverlayAnswerModal
    
    private var isProcessing = false
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    init {
        setWillNotDraw(false)
        setupPaints()
        
        // Create UI components
        instructionText = createInstructionText()
        solveButton = createSolveButton()
        closeButton = createCloseButton()
        progressBar = createProgressBar()
        answerModal = createAnswerModal()
        
        setupLayout()
    }
    
    private fun setupPaints() {
        // Paint for the overlay background
        paint.apply {
            color = Color.GRAY
            alpha = OVERLAY_ALPHA
            style = Paint.Style.FILL
        }
        
        // Paint for the selection outline
        strokePaint.apply {
            color = ContextCompat.getColor(context, R.color.purple_500)
            style = Paint.Style.STROKE
            strokeWidth = 4f
            pathEffect = DashPathEffect(floatArrayOf(10f, 5f), 0f)
        }
    }
    
    private fun createInstructionText(): TextView {
        return TextView(context).apply {
            text = "Draw a free-form shape around the content you want to analyze"
            textSize = 16f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.BLACK)
            setPadding(16, 8, 16, 8)
            gravity = Gravity.CENTER
        }
    }
    
    private fun createSolveButton(): Button {
        return Button(context).apply {
            text = "Solve"
            textSize = 18f
            setBackgroundColor(ContextCompat.getColor(context, R.color.purple_500))
            setTextColor(Color.WHITE)
            isEnabled = false
            alpha = 0.5f
            
            setOnClickListener {
                if (hasSelection && !isProcessing) {
                    captureAndAnalyze()
                }
            }
        }
    }
    
    private fun createCloseButton(): ImageButton {
        return ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setBackgroundColor(Color.RED)
            setColorFilter(Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(16, 16, 16, 16)
            
            setOnClickListener {
                onCloseListener()
            }
        }
    }
    
    private fun createProgressBar(): ProgressBar {
        return ProgressBar(context).apply {
            visibility = GONE
        }
    }
    
    private fun createAnswerModal(): OverlayAnswerModal {
        return OverlayAnswerModal(
            context = context,
            onClose = { hideAnswerModal() },
            onNewSelection = { startNewSelection() }
        )
    }
    
    private fun setupLayout() {
        // Add instruction text at top
        addView(instructionText, LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = 50
        })
        
        // Add close button at top right
        addView(closeButton, LayoutParams(
            80,
            80
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            topMargin = 50
            rightMargin = 50
        })
        
        // Add solve button at bottom center
        addView(solveButton, LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = 100
        })
        
        // Add progress bar (hidden initially)
        addView(progressBar, LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
        })
        
        // Add answer modal (hidden initially)
        addView(answerModal, LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        ).apply {
            gravity = Gravity.CENTER
        })
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Draw semi-transparent overlay
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        
        // Draw selection path if available
        if (!selectionPath.isEmpty) {
            // Clear the selected area
            val clearPaint = Paint().apply {
                color = Color.TRANSPARENT
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
            canvas.drawPath(selectionPath, clearPaint)
            
            // Draw selection outline
            canvas.drawPath(selectionPath, strokePaint)
        }
    }
    
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isProcessing) return true
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startSelection(event.x, event.y)
                return true
            }
            
            MotionEvent.ACTION_MOVE -> {
                continueSelection(event.x, event.y)
                return true
            }
            
            MotionEvent.ACTION_UP -> {
                finishSelection()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
    
    private fun startSelection(x: Float, y: Float) {
        selectionPath.reset()
        selectionPath.moveTo(x, y)
        lastX = x
        lastY = y
        selectionBounds.set(x, y, x, y)
        isDrawing = true
        hasSelection = false
        updateSolveButtonState()
    }
    
    private fun continueSelection(x: Float, y: Float) {
        if (isDrawing) {
            // Use quadratic curve for smoother free-form drawing
            val controlX = (lastX + x) / 2
            val controlY = (lastY + y) / 2
            selectionPath.quadTo(controlX, controlY, x, y)
            
            lastX = x
            lastY = y
            
            // Update bounds for screenshot capture
            selectionBounds.left = minOf(selectionBounds.left, x)
            selectionBounds.top = minOf(selectionBounds.top, y)
            selectionBounds.right = maxOf(selectionBounds.right, x)
            selectionBounds.bottom = maxOf(selectionBounds.bottom, y)
            
            invalidate()
        }
    }
    
    private fun finishSelection() {
        if (isDrawing) {
            selectionPath.close()
            isDrawing = false
            
            // Check if the selection path has enough points and area
            val pathMeasure = PathMeasure(selectionPath, false)
            val pathLength = pathMeasure.length
            
            // Calculate approximate area using path bounds
            val bounds = RectF()
            selectionPath.computeBounds(bounds, true)
            val area = bounds.width() * bounds.height()
            
            // More flexible selection criteria - just need a reasonable path
            hasSelection = pathLength > 50 && area > 1000 // Minimum path length and area
            
            updateSolveButtonState()
        }
    }
    
    private fun updateSolveButtonState() {
        solveButton.isEnabled = hasSelection && !isProcessing
        solveButton.alpha = if (solveButton.isEnabled) 1.0f else 0.5f
    }
    
    private fun captureAndAnalyze() {
        if (mediaProjection == null) {
            showError("Screenshot permission not available")
            return
        }
        
        isProcessing = true
        updateSolveButtonState()
        showProgress(true)
        showAnswerModal()
        
        coroutineScope.launch {
            try {
                instructionText.text = "Capturing screenshot..."
                
                // Capture screenshot using the selection path bounds
                val bounds = RectF()
                selectionPath.computeBounds(bounds, true)
                
                val screenshot = ScreenshotCapture.captureScreen(
                    mediaProjection,
                    bounds.toRect(),
                    context.resources.displayMetrics
                )
                
                if (screenshot != null) {
                    instructionText.text = "Processing with AI..."
                    
                    // Process with AI using streaming
                    googleAIService.analyzeImageStreaming(screenshot).collect { response ->
                        answerModal.updateResponse(response, streaming = true)
                    }
                    
                    // Analysis complete
                    instructionText.text = "Analysis complete!"
                    
                } else {
                    showError("Failed to capture screenshot")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during capture and analysis", e)
                showError("Error: ${e.message}")
            } finally {
                isProcessing = false
                updateSolveButtonState()
                showProgress(false)
            }
        }
    }
    
    private fun showProgress(show: Boolean) {
        progressBar.visibility = if (show) VISIBLE else GONE
    }
    
    private fun showAnswerModal() {
        answerModal.show()
    }
    
    private fun hideAnswerModal() {
        answerModal.hide()
        onCloseListener()
    }
    
    private fun startNewSelection() {
        // Hide the answer modal
        answerModal.hide()
        
        // Reset selection state
        selectionPath.reset()
        selectionBounds.setEmpty()
        hasSelection = false
        isDrawing = false
        
        // Update UI
        updateSolveButtonState()
        invalidate()
        
        // Reset instruction text
        instructionText.text = "Draw a free-form shape around the content you want to analyze"
        instructionText.setTextColor(Color.WHITE)
    }
    
    private fun showError(message: String) {
        instructionText.text = message
        instructionText.setTextColor(Color.RED)
        answerModal.showError(message)
        
        // Reset after 3 seconds
        coroutineScope.launch {
            delay(3000)
            instructionText.text = "Draw a free-form shape around the content you want to analyze"
            instructionText.setTextColor(Color.WHITE)
        }
    }
    
    private fun RectF.toRect(): Rect {
        return Rect(
            left.toInt(),
            top.toInt(),
            right.toInt(),
            bottom.toInt()
        )
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        coroutineScope.cancel()
    }
} 