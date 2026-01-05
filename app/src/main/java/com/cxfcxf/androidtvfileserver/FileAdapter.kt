package com.cxfcxf.androidtvfileserver

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cxfcxf.androidtvfileserver.databinding.ItemFileBinding
import com.cxfcxf.androidtvfileserver.R
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
            // Set click listener - opens file/folder
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
            
            // Set long-press listener - shows action menu (delete)
            binding.root.setOnLongClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val file = getItem(position)
                    // Show delete confirmation directly on long press
                    onItemDelete?.invoke(file)
                }
                true
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
            // Set file name
            binding.fileName.text = file.name
            
            // Special handling for directories vs files
            if (file.isDirectory) {
                binding.fileName.setTypeface(null, android.graphics.Typeface.BOLD)
                binding.fileDetails.text = ""
            } else {
                binding.fileName.setTypeface(null, android.graphics.Typeface.NORMAL)
                binding.fileDetails.text = formatFileSize(file.length())
            }
            
            binding.fileName.setTextColor(Color.WHITE)
            binding.fileDetails.setTextColor(Color.GRAY)
            
            // Enable marquee for long text
            binding.fileName.isSingleLine = true
            binding.fileName.isSelected = binding.root.isFocused
            binding.fileName.ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
            binding.fileName.marqueeRepeatLimit = -1  // Forever
            
            // Set icon based on file type - using custom colorful icons
            binding.fileIcon.setImageResource(
                if (file.isDirectory) {
                    R.drawable.ic_folder
                } else {
                    // Choose icon based on file extension
                    when (file.extension.lowercase()) {
                        "apk" -> R.drawable.ic_apk
                        "jpg", "jpeg", "png", "gif", "webp", "bmp" -> R.drawable.ic_image
                        "mp4", "3gp", "mkv", "avi", "mov", "webm" -> R.drawable.ic_video
                        else -> R.drawable.ic_file
                    }
                }
            )
            
            // Simple focus handler - just enable marquee for long text when focused
            // Selection box background handles the visual focus indication
            binding.root.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                binding.fileName.isSelected = hasFocus
            }
            
            // Force a check of the current focus state
            binding.root.onFocusChangeListener.onFocusChange(binding.root, binding.root.isFocused)

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