package com.example.blackboardai.presentation.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.Log
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.example.blackboardai.R

class FloatingButtonView(
    context: Context,
    private val onClickListener: () -> Unit
) : FrameLayout(context) {
    
    companion object {
        private const val TAG = "[FloatingButton]"
        private const val BUTTON_SIZE_DP = 56
        private const val CLICK_THRESHOLD = 10f
    }
    
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var windowLayoutParams: WindowManager.LayoutParams? = null
    
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var initialX = 0
    private var initialY = 0
    private var isDragging = false
    
    private val buttonSize = (BUTTON_SIZE_DP * context.resources.displayMetrics.density).toInt()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconView: ImageView
    
    init {
        setupView()
        iconView = createIconView()
        addView(iconView)
    }
    
    private fun setupView() {
        // Set a minimum size for the view
        minimumWidth = buttonSize
        minimumHeight = buttonSize
        
        // Make it circular and elevated
        setWillNotDraw(false)
        elevation = 12f
        
        // Set a solid background color for better visibility
        setBackgroundColor(ContextCompat.getColor(context, R.color.purple_500))
        
        Log.d(TAG, "🎯 FloatingButtonView setup completed. Size: ${buttonSize}px")
    }
    
    private fun createIconView(): ImageView {
        return ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            layoutParams = LayoutParams(
                (buttonSize * 0.6).toInt(),
                (buttonSize * 0.6).toInt()
            ).apply {
                gravity = Gravity.CENTER
            }
        }
    }
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Force the view to be square with our button size
        val size = MeasureSpec.makeMeasureSpec(buttonSize, MeasureSpec.EXACTLY)
        super.onMeasure(size, size)
        Log.d(TAG, "📏 FloatingButtonView measured: ${buttonSize}x${buttonSize}px")
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = width / 2f - 4f
        
        // Draw shadow for better visibility
        paint.apply {
            color = Color.BLACK
            alpha = 80
        }
        canvas.drawCircle(centerX + 3f, centerY + 6f, radius, paint)
        
        // Draw main button background
        paint.apply {
            color = ContextCompat.getColor(context, R.color.purple_500)
            alpha = 255
        }
        canvas.drawCircle(centerX, centerY, radius, paint)
        
        // Draw border for better definition
        paint.apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2f
            alpha = 200
        }
        canvas.drawCircle(centerX, centerY, radius, paint)
    }
    
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                
                windowLayoutParams?.let { params ->
                    initialX = params.x
                    initialY = params.y
                }
                
                isDragging = false
                return true
            }
            
            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.rawX - initialTouchX
                val deltaY = event.rawY - initialTouchY
                
                // Check if this is a drag gesture
                if (!isDragging && (Math.abs(deltaX) > CLICK_THRESHOLD || Math.abs(deltaY) > CLICK_THRESHOLD)) {
                    isDragging = true
                }
                
                if (isDragging) {
                    windowLayoutParams?.let { params ->
                        params.x = initialX + deltaX.toInt()
                        params.y = initialY + deltaY.toInt()
                        
                        // Keep button within screen bounds
                        val displayMetrics = context.resources.displayMetrics
                        params.x = params.x.coerceIn(0, displayMetrics.widthPixels - buttonSize)
                        params.y = params.y.coerceIn(0, displayMetrics.heightPixels - buttonSize)
                        
                        try {
                            windowManager.updateViewLayout(this, params)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to update view layout", e)
                        }
                    }
                }
                return true
            }
            
            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    // This was a click, not a drag
                    performClick()
                    onClickListener()
                }
                isDragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }
    
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
    
    /**
     * Set the window layout params after the view is added to the window manager
     */
    fun setWindowLayoutParams(params: WindowManager.LayoutParams) {
        windowLayoutParams = params
    }
} 