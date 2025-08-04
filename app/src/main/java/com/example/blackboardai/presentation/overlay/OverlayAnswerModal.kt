package com.example.blackboardai.presentation.overlay

import android.content.Context
import android.graphics.Color
import android.text.Html
import android.text.Spanned
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import com.example.blackboardai.R

class OverlayAnswerModal(
    context: Context,
    private val onClose: () -> Unit,
    private val onNewSelection: () -> Unit
) : FrameLayout(context) {
    
    private var isVisible = false
    private var currentResponse = ""
    private var isStreaming = false
    
    private val modalContainer: FrameLayout
    private val progressBar: ProgressBar
    private val closeButton: ImageButton
    private val newSelectionButton: ImageButton
    private val responseTextView: TextView
    private val scrollView: ScrollView
    
    init {
        // Set up the modal container
        setBackgroundColor(Color.parseColor("#80000000")) // Semi-transparent background
        
        // Set initial visibility to GONE
        visibility = View.GONE
        
        // Create modal container
        modalContainer = FrameLayout(context).apply {
            setBackgroundColor(Color.WHITE)
            elevation = 16f
        }
        
        // Create close button
        closeButton = ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setBackgroundColor(Color.RED)
            setColorFilter(Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(16, 16, 16, 16)
            
            setOnClickListener {
                onClose()
            }
        }
        
        // Create new selection button
        newSelectionButton = ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_menu_crop)
            setBackgroundColor(Color.BLUE)
            setColorFilter(Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(16, 16, 16, 16)
            
            setOnClickListener {
                onNewSelection()
            }
        }
        
        // Create response text view
        responseTextView = TextView(context).apply {
            textSize = 16f
            setTextColor(Color.BLACK)
            setPadding(24, 24, 24, 24)
            setLineSpacing(0f, 1.2f)
        }
        
        // Create scroll view for response
        scrollView = ScrollView(context).apply {
            addView(responseTextView)
        }
        
        // Create progress bar for loading state
        progressBar = ProgressBar(context).apply {
            visibility = View.GONE
        }
        
        setupLayout()
    }
    
    private fun setupLayout() {
        // Add modal container
        addView(modalContainer, LayoutParams(
            (context.resources.displayMetrics.widthPixels * 0.9).toInt(),
            (context.resources.displayMetrics.heightPixels * 0.5).toInt()
        ).apply {
            gravity = Gravity.CENTER
        })
        
        // Add close button to modal container
        modalContainer.addView(closeButton, LayoutParams(
            80,
            80
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            topMargin = 16
            rightMargin = 16
        })
        
        // Add new selection button to modal container
        modalContainer.addView(newSelectionButton, LayoutParams(
            80,
            80
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            bottomMargin = 16
            leftMargin = 16
        })
        
        // Add scroll view to modal container
        modalContainer.addView(scrollView, LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        ).apply {
            topMargin = 100 // Space for close button
            bottomMargin = 100 // Space for new selection button
            leftMargin = 16
            rightMargin = 16
        })
        
        // Add progress bar (centered)
        addView(progressBar, LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
        })
    }
    
    fun show() {
        isVisible = true
        visibility = View.VISIBLE
        progressBar.visibility = View.VISIBLE
        modalContainer.visibility = View.GONE
        updateResponseText("🧠 Analyzing your selection...\n\nPlease wait while I process the image and generate an analysis.")
    }
    
    fun hide() {
        isVisible = false
        visibility = View.GONE
        currentResponse = ""
        isStreaming = false
    }
    
    fun updateResponse(response: String, streaming: Boolean = false) {
        currentResponse = response
        isStreaming = streaming
        
        if (streaming) {
            progressBar.visibility = View.GONE
            modalContainer.visibility = View.VISIBLE
        }
        
        updateResponseText(response)
    }
    
    fun showError(message: String) {
        currentResponse = "Error: $message"
        isStreaming = false
        progressBar.visibility = View.GONE
        modalContainer.visibility = View.VISIBLE
        updateResponseText("❌ Error: $message")
    }
    
    /**
     * Get the current response text
     */
    fun getCurrentResponse(): String {
        return currentResponse
    }
    
    private fun updateResponseText(text: String) {
        val displayText = if (text.isBlank()) {
            "🧠 Analyzing your selection...\n\nPlease wait while I process the image and generate an analysis."
        } else {
            text
        }
        
        // Convert markdown-like formatting to HTML
        val htmlText = convertMarkdownToHtml(displayText)
        responseTextView.text = htmlText
        
        // Scroll to bottom for streaming responses
        if (isStreaming) {
            scrollView.post {
                scrollView.fullScroll(ScrollView.FOCUS_DOWN)
            }
        }
    }
    
    private fun convertMarkdownToHtml(markdown: String): Spanned {
        var html = markdown
        
        // Convert headers
        html = html.replace(Regex("^# (.+)$", RegexOption.MULTILINE), "<h1>$1</h1>")
        html = html.replace(Regex("^## (.+)$", RegexOption.MULTILINE), "<h2>$1</h2>")
        
        // Convert bold text
        html = html.replace(Regex("\\*\\*(.+?)\\*\\*"), "<b>$1</b>")
        
        // Convert bullet points
        html = html.replace(Regex("^- (.+)$", RegexOption.MULTILINE), "• $1")
        
        // Convert line breaks
        html = html.replace("\n", "<br>")
        
        return HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
    }
    
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
    }
} 