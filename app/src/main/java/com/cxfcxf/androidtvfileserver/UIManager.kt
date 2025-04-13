package com.cxfcxf.androidtvfileserver

import android.content.Context
import android.graphics.Color
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cxfcxf.androidtvfileserver.databinding.ActivityMainBinding
import java.io.File

class UIManager(
    private val context: Context,
    private val binding: ActivityMainBinding,
    private val fileManager: FileManager
) {
    private lateinit var fileAdapter: FileAdapter
    
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
        
        // Initialize server status displays with stopped state
        updateServerStatus("Stopped", null)
    }
    
    fun updateCurrentPath(path: String) {
        binding.currentPathText.text = "Current: $path"
    }
    
    fun updateServerStatus(status: String, url: String?) {
        binding.serverStatus.text = "Server Status: $status"
        binding.serverStatus.visibility = View.VISIBLE
        
        if (url != null) {
            binding.serverUrl.text = "Server URL: $url"
            binding.toggleServer.text = "Stop Server"
        } else {
            binding.serverUrl.text = "Server URL: Not Started"
            binding.toggleServer.text = "Start Server"
        }
        binding.serverUrl.visibility = View.VISIBLE
    }
    
    fun updateUploadProgress(filename: String, totalBytes: Long, mbPerSecond: Double) {
        binding.serverStatus.text = "Server Status: Receiving upload '${filename}' (${totalBytes / (1024 * 1024)} MB @ ${String.format("%.1f", mbPerSecond)} MB/s)"
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
            layoutManager = LinearLayoutManager(context)
            adapter = fileAdapter
            
            // TV remote navigation enhancements
            setHasFixedSize(true)
            isFocusable = true
            isFocusableInTouchMode = true
            
            // Set explicit navigation paths for d-pad
            nextFocusUpId = R.id.parentDirButton
            
            // Make d-pad navigation smoother by setting focus behavior
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            
            // Set padding to see the focus highlight better
            setPadding(16, 8, 16, 8)
            clipToPadding = false
            
            // Handle key events to improve navigation
            setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN) {
                    // Handle UP navigation to buttons
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        val lm = layoutManager as LinearLayoutManager
                        // If we're at the top of the list, move focus to buttons
                        if (lm.findFirstCompletelyVisibleItemPosition() == 0) {
                            binding.parentDirButton.requestFocus()
                            return@setOnKeyListener true
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
                                    // Center the focused item
                                    val lm = layoutManager as LinearLayoutManager
                                    lm.scrollToPositionWithOffset(position, 
                                        recyclerView.height / 2 - viewParent.height / 2)
                                }
                            }
                        }
                    }
                }
                
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    if (newState == RecyclerView.SCROLL_STATE_IDLE && !recyclerView.hasFocus()) {
                        // If we've stopped scrolling and no item has focus, try to focus an item
                        val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                        val firstVisiblePosition = layoutManager.findFirstCompletelyVisibleItemPosition()
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
        // Add button to go to parent directory
        binding.parentDirButton.setOnClickListener {
            listener?.onNavigateToParentDirectory()
        }
        
        // Server toggle button is now in the top navigation area
        binding.toggleServer.setOnClickListener {
            listener?.onToggleServer()
        }
    }
    
    fun setupPermissionButton() {
        // Add permission request to long-press on parent directory button
        binding.parentDirButton.setOnLongClickListener {
            listener?.let {
                return@setOnLongClickListener true
            }
            false
        }
    }

    private fun setupTvNavigation() {
        // Set initial focus for Android TV
        binding.parentDirButton.requestFocus()
        
        // Make buttons highlight with green background when focused
        val focusListener = View.OnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                // Turn the button background green when focused
                if (view is Button) {
                    view.backgroundTintList = android.content.res.ColorStateList.valueOf(
                        Color.parseColor("#4CAF50") // Green color
                    )
                }
            } else {
                // Restore to gray when unfocused
                if (view is Button) {
                    view.backgroundTintList = android.content.res.ColorStateList.valueOf(
                        Color.parseColor("#757575") // Gray color
                    )
                }
            }
        }
        
        // Apply the focus listener to navigation buttons
        binding.parentDirButton.onFocusChangeListener = focusListener
        binding.toggleServer.onFocusChangeListener = focusListener
        
        // Set key listeners for d-pad rotation between buttons
        binding.root.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                // Loop navigation - wrap from last to first element and vice versa
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