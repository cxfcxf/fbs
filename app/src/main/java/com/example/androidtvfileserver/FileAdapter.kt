package com.example.androidtvfileserver

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.androidtvfileserver.databinding.ItemFileBinding
import com.example.androidtvfileserver.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class FileAdapter(private val onFileClick: (File) -> Unit) :
    ListAdapter<File, FileAdapter.FileViewHolder>(FileDiffCallback()) {

    private var onItemDelete: ((File) -> Unit)? = null
    
    fun setOnItemDeleteListener(listener: (File) -> Unit) {
        onItemDelete = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val binding = ItemFileBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        
        // Set explicit navigation for TV d-pad
        binding.root.apply {
            // Specify next focus for d-pad navigation
            nextFocusUpId = R.id.parentDirButton
            // Other directions handled by default
        }
        
        return FileViewHolder(binding, onFileClick, onItemDelete)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FileViewHolder(
        private val binding: ItemFileBinding,
        private val onItemClick: (File) -> Unit,
        private val onItemDelete: ((File) -> Unit)?
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            // Set click listener
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }

            // Set focus listener for TV remote control
            binding.root.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                // The selector drawable will handle the background color changes
                // This code can handle additional focus effects if needed
                if (hasFocus) {
                    // Item is focused
                    binding.fileName.isSelected = true  // Enable text marquee if text is too long
                } else {
                    // Item lost focus
                    binding.fileName.isSelected = false
                }
            }
        }

        fun bind(file: File) {
            // Special handling for directories to make them stand out
            if (file.isDirectory) {
                // Make folder names more distinctive - just the plain name without emoji
                binding.fileName.text = file.name
                binding.fileName.setTypeface(null, android.graphics.Typeface.BOLD)
                binding.fileName.setTextColor(Color.WHITE) // Make folder names white for better visibility
                binding.fileDetails.text = "Directory"
                binding.fileDetails.setTextColor(Color.LTGRAY) // Light gray for details
            } else {
                // Regular files with last modified time
                val lastModified = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                    .format(java.util.Date(file.lastModified()))
                binding.fileName.text = file.name
                binding.fileName.setTypeface(null, android.graphics.Typeface.NORMAL)
                binding.fileName.setTextColor(Color.WHITE) // Make file names white too
                
                // Calculate file size
                val fileSize = formatFileSize(file.length())
                binding.fileDetails.text = "$fileSize - $lastModified"
                binding.fileDetails.setTextColor(Color.LTGRAY) // Light gray for details
            }
            
            // Enable marquee for long text
            binding.fileName.isSingleLine = true
            binding.fileName.isSelected = binding.root.isFocused
            binding.fileName.ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
            binding.fileName.marqueeRepeatLimit = -1  // Forever
            
            // Set icon based on file type
            binding.fileIcon.setImageResource(
                if (file.isDirectory) {
                    android.R.drawable.ic_dialog_dialer  // Folder icon
                } else {
                    // Choose icon based on file extension
                    when (file.extension.lowercase()) {
                        "apk" -> android.R.drawable.ic_menu_upload
                        "jpg", "jpeg", "png", "gif" -> android.R.drawable.ic_menu_gallery
                        "mp4", "3gp", "mkv" -> android.R.drawable.ic_media_play
                        else -> android.R.drawable.ic_menu_save
                    }
                }
            )
            
            // Ensure the item shows prominent focus when navigating with remote
            binding.root.onFocusChangeListener = View.OnFocusChangeListener { view, hasFocus ->
                // Setup text for focused state
                binding.fileName.isSelected = hasFocus
                
                if (hasFocus) {
                    // Make focused item stand out with animation
                    view.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150).start()
                    binding.fileName.setTextColor(Color.WHITE)
                    
                    // Show cursor-like indicator by adding yellow border in drawable
                    // This is handled by the selector drawable
                    
                    // Make the icon bigger and change its tint for focus
                    binding.fileIcon.animate().scaleX(1.2f).scaleY(1.2f).setDuration(150).start()
                    binding.fileIcon.setColorFilter(Color.YELLOW)
                } else {
                    // Reset to default for unfocused state
                    view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                    binding.fileName.setTextColor(Color.WHITE) // Keep text white even when unfocused
                    
                    // Reset icon
                    binding.fileIcon.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                    binding.fileIcon.clearColorFilter()
                }
            }
            
            // Force a check of the current focus state
            binding.root.onFocusChangeListener.onFocusChange(binding.root, binding.root.isFocused)

            // Hide delete button initially
            binding.deleteButton.visibility = View.GONE

            // Delete button click listener
            binding.deleteButton.setOnClickListener {
                onItemDelete?.invoke(file)
            }

            // Handle key events for showing delete button
            binding.root.setOnKeyListener { _, keyCode, event ->
                if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                    when (keyCode) {
                        android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            // Show delete button when pressing right
                            binding.deleteButton.visibility = View.VISIBLE
                            binding.deleteButton.requestFocus()
                            return@setOnKeyListener true
                        }
                        android.view.KeyEvent.KEYCODE_BACK -> {
                            // Hide delete button when pressing back
                            if (binding.deleteButton.visibility == View.VISIBLE) {
                                binding.deleteButton.visibility = View.GONE
                                binding.root.requestFocus()
                                return@setOnKeyListener true
                            }
                        }
                    }
                }
                false
            }

            // Handle deletion button focus change
            binding.deleteButton.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    // Hide the delete button when focus is lost
                    binding.deleteButton.visibility = View.GONE
                }
            }
        }
    }

    private fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}

class FileDiffCallback : DiffUtil.ItemCallback<File>() {
    override fun areItemsTheSame(oldItem: File, newItem: File): Boolean {
        return oldItem.absolutePath == newItem.absolutePath
    }

    override fun areContentsTheSame(oldItem: File, newItem: File): Boolean {
        return oldItem.lastModified() == newItem.lastModified() &&
                oldItem.length() == newItem.length()
    }
} 