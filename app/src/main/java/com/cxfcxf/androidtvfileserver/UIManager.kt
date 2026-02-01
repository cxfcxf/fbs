package com.cxfcxf.androidtvfileserver

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cxfcxf.androidtvfileserver.databinding.ActivityMainBinding
import java.io.File

class UIManager(
    private val context: Context,
    private val binding: ActivityMainBinding,
    private val fileManager: FileManager
) {
    private lateinit var fileAdapter: FileAdapter
    private var cursorAnimator: ObjectAnimator? = null

    interface UIListener {
        fun onNavigateToParentDirectory()
        fun onToggleServer()
        fun onFileSelected(file: File)
        fun onFileDelete(file: File)
    }

    private var listener: UIListener? = null

    fun setListener(listener: UIListener) {
        this.listener = listener
    }

    fun setupUI() {
        setupFileList()
        setupNavigationButtons()
        setupTvNavigation()
        setupAnimations()

        // Initialize server status displays with stopped state
        updateServerStatus("Stopped", null)
    }

    private fun setupAnimations() {
        // Blinking cursor animation
        cursorAnimator = ObjectAnimator.ofFloat(binding.blinkingCursor, "alpha", 1f, 0f).apply {
            duration = 500
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }

        // Pulsing status dot animation
        ObjectAnimator.ofFloat(binding.statusDot, "alpha", 1f, 0.4f).apply {
            duration = 1000
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }
    
    fun updateCurrentPath(path: String) {
        binding.currentPathText.text = path
    }
    
    fun updateServerStatus(status: String, url: String?) {
        if (url != null) {
            binding.serverStatus.text = "ONLINE"
            binding.serverStatus.setTextColor(Color.parseColor("#00FF88"))
            binding.serverUrl.text = url
            binding.serverUrl.visibility = View.VISIBLE
            binding.toggleServer.text = "STOP"
            binding.toggleServer.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_stop, 0, 0, 0)
            // Update status dot to green
            binding.statusDot.setBackgroundResource(R.drawable.status_dot)
        } else {
            binding.serverStatus.text = "OFFLINE"
            binding.serverStatus.setTextColor(Color.parseColor("#FFAA00"))
            binding.serverUrl.visibility = View.GONE
            binding.toggleServer.text = "START"
            binding.toggleServer.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_play, 0, 0, 0)
            // Update status dot to amber
            binding.statusDot.setBackgroundResource(R.drawable.status_dot_offline)
        }
    }
    
    fun updateUploadProgress(filename: String, totalBytes: Long, mbPerSecond: Double) {
        binding.serverStatus.text = "↑ ${filename.take(15)}... ${totalBytes / (1024 * 1024)}MB"
    }
    
    private fun setupFileList() {
        fileAdapter = FileAdapter { file ->
            listener?.onFileSelected(file)
        }
        
        // Set up file deletion handler
        fileAdapter.setOnItemDeleteListener { file ->
            listener?.onFileDelete(file)
        }

        binding.fileList.apply {
            // Use GridLayoutManager with 10 columns for high-density TV display
            layoutManager = GridLayoutManager(context, 10)
            adapter = fileAdapter
            
            // TV remote navigation enhancements
            setHasFixedSize(true)
            isFocusable = true
            isFocusableInTouchMode = true
            
            // Make d-pad navigation smoother by setting focus behavior
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            
            // Set padding to see the focus highlight better
            setPadding(8, 4, 8, 4)
            clipToPadding = false
            
            // Handle key events to improve navigation
            setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN) {
                    // Handle UP navigation to buttons
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        val focusedView = findFocus()
                        if (focusedView != null) {
                            var viewParent = focusedView
                            while (viewParent != null && viewParent.parent != this) {
                                viewParent = viewParent.parent as? View
                            }
                            
                            if (viewParent != null) {
                                val pos = getChildAdapterPosition(viewParent)
                                if (pos != RecyclerView.NO_POSITION && pos < 10) {
                                    // Top row, decide which button to focus based on column
                                    if (pos % 10 < 5) {
                                        binding.parentDirButton.requestFocus()
                                    } else {
                                        binding.toggleServer.requestFocus()
                                    }
                                    return@setOnKeyListener true
                                }
                            }
                        }
                    }
                }
                false // Let other key events pass through
            }
            
            // Improve focus behavior by ensuring the focused item is visible
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    if (dy != 0 || dx != 0) {
                        // After scrolling, find the focused child and ensure it's visible
                        val focusedChild = recyclerView.findFocus()
                        if (focusedChild != null) {
                            // Find the parent ViewHolder to get the adapter position
                            var viewParent = focusedChild
                            while (viewParent != null && viewParent.parent != recyclerView) {
                                viewParent = viewParent.parent as? View
                            }
                            
                            if (viewParent != null) {
                                val position = recyclerView.getChildAdapterPosition(viewParent)
                                if (position != RecyclerView.NO_POSITION) {
                                    recyclerView.smoothScrollToPosition(position)
                                }
                            }
                        }
                    }
                }
                
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    if (newState == RecyclerView.SCROLL_STATE_IDLE && !recyclerView.hasFocus()) {
                        // If we've stopped scrolling and no item has focus, try to focus an item
                        val lm = recyclerView.layoutManager as GridLayoutManager
                        val firstVisiblePosition = lm.findFirstCompletelyVisibleItemPosition()
                        if (firstVisiblePosition != RecyclerView.NO_POSITION) {
                            val viewHolder = recyclerView.findViewHolderForAdapterPosition(firstVisiblePosition)
                            viewHolder?.itemView?.requestFocus()
                        }
                    }
                }
            })
        }
        
        // Set initial focus on first item after data is loaded
        fileAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                if (fileAdapter.itemCount > 0) {
                    binding.fileList.post {
                        // Focus on the first item when data changes
                        if (binding.fileList.childCount > 0 && !binding.fileList.hasFocus()) {
                            binding.fileList.getChildAt(0)?.requestFocus()
                        }
                    }
                }
            }
            
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                if (positionStart == 0 && itemCount > 0) {
                    binding.fileList.post {
                        // Focus on first item when new items are added
                        binding.fileList.getChildAt(0)?.requestFocus()
                    }
                }
            }
        })
    }
    
    fun updateFileList(files: List<File>) {
        fileAdapter.submitList(files)
    }

    private fun setupNavigationButtons() {
        binding.parentDirButton.setOnClickListener {
            listener?.onNavigateToParentDirectory()
        }
        binding.toggleServer.setOnClickListener {
            listener?.onToggleServer()
        }
    }
    
    fun setupPermissionButton() {
        binding.parentDirButton.setOnLongClickListener {
            listener != null
        }
    }

    private fun setupTvNavigation() {
        binding.parentDirButton.requestFocus()

        // Handle d-pad wrap-around navigation between buttons
        binding.root.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (binding.toggleServer.hasFocus()) {
                            binding.parentDirButton.requestFocus()
                            return@setOnKeyListener true
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (binding.parentDirButton.hasFocus()) {
                            binding.toggleServer.requestFocus()
                            return@setOnKeyListener true
                        }
                    }
                }
            }
            false
        }
    }
} 