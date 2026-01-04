package com.cxfcxf.androidtvfileserver

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

class FileManager(private val context: Context) {
    private val TAG = "FileManager"
    private var currentDirectory = File("/")
    
    interface FileOperationListener {
        fun onDirectoryLoaded(files: List<File>)
        fun onError(message: String)
    }
    
    private var listener: FileOperationListener? = null
    
    fun setListener(listener: FileOperationListener) {
        this.listener = listener
    }
    
    fun getCurrentDirectory(): File {
        return currentDirectory
    }
    
    fun setCurrentDirectory(directory: File) {
        if (directory.exists() && directory.isDirectory) {
            currentDirectory = directory
            loadFiles()
        } else {
            listener?.onError("Cannot access directory: ${directory.absolutePath}")
        }
    }
    
    // Base directory - don't allow navigation above this level
    private val baseDir = "/storage/emulated/0"
    
    fun navigateToParentDirectory() {
        // Check if we're already at or above the base directory
        if (currentDirectory.absolutePath == baseDir || !currentDirectory.absolutePath.startsWith("$baseDir/")) {
            android.widget.Toast.makeText(context, "Already at the top level directory", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        
        val parent = currentDirectory.parentFile
        if (parent != null && parent.absolutePath.startsWith(baseDir)) {
            setCurrentDirectory(parent)
        } else {
            android.widget.Toast.makeText(context, "Cannot navigate above $baseDir", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    fun loadFiles() {
        try {
            if (!canAccessDirectory(currentDirectory)) {
                listener?.onError("Cannot access ${currentDirectory.absolutePath}")
                val files = currentDirectory.listFiles()?.toList() ?: emptyList()
                listener?.onDirectoryLoaded(files)
                return
            }
            
            val files = currentDirectory.listFiles()?.toList() ?: emptyList()
            listener?.onDirectoryLoaded(files)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading files", e)
            listener?.onError("Error loading files: ${e.message}")
            fallbackToAppStorage()
        }
    }
    
    fun deleteFile(file: File): Boolean {
        return try {
            val result = file.delete()
            loadFiles() // Refresh the file list
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting file", e)
            false
        }
    }
    
    fun installApk(file: File) {
        try {
            // Create the intent to install the APK
            val intent = Intent(Intent.ACTION_VIEW)
            
            // For Android N and above (24+), we need to use FileProvider
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val apkUri = FileProvider.getUriForFile(
                    context,
                    context.applicationContext.packageName + ".provider",
                    file
                )
                intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            } else {
                // For older Android versions
                val apkUri = Uri.fromFile(file)
                intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            // Show confirmation dialog before starting the installation
            androidx.appcompat.app.AlertDialog.Builder(context)
                .setTitle("Install APK")
                .setMessage("Do you want to install ${file.name}?")
                .setPositiveButton("Install") { _, _ ->
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error starting APK install activity", e)
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
                
        } catch (e: Exception) {
            // Handle errors gracefully
            Log.e(TAG, "Error preparing APK installation", e)
            Toast.makeText(context, "Cannot install APK: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    fun fallbackToAppStorage() {
        currentDirectory = context.getExternalFilesDir(null) ?: context.filesDir
        loadFiles()
        Toast.makeText(context, 
            "Limited access mode: You can only see files in app-specific directories\n" +
            "Path: ${currentDirectory.absolutePath}", 
            Toast.LENGTH_LONG).show()
    }
    
    private fun canAccessDirectory(directory: File): Boolean {
        return directory.exists() && directory.canRead() && try {
            // Try to actually list files as an additional check
            directory.listFiles() != null
        } catch (e: Exception) {
            false
        }
    }
} 