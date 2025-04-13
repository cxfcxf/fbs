package com.cxfcxf.androidtvfileserver

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat

class PermissionManager(private val context: Context) {
    private val TAG = "PermissionManager"
    
    interface PermissionListener {
        fun onPermissionGranted()
        fun onPermissionDenied()
    }
    
    private var listener: PermissionListener? = null
    
    fun setListener(listener: PermissionListener) {
        this.listener = listener
    }
    
    fun checkPermissions(requestPermissionLauncher: ActivityResultLauncher<Array<String>>) {
        // For Android 11 (API 30) or higher, we need special handling for storage
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                // We have full storage access permission
                listener?.onPermissionGranted()
            } else {
                // Request for full storage access on Android 11+
                val requestStorageIntent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                requestStorageIntent.data = Uri.parse("package:${context.packageName}")
                
                try {
                    context.startActivity(requestStorageIntent)
                    Toast.makeText(context, 
                        "Please grant 'All Files Access' permission to browse all files", 
                        Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Log.e(TAG, "Error requesting all files access", e)
                    // Fallback to limited access
                    requestLegacyStoragePermissions(requestPermissionLauncher)
                }
                
                // For now, use app-specific storage until permission is granted
                listener?.onPermissionDenied()
            }
            return
        }
        
        // Legacy permission handling for Android 10 and below
        requestLegacyStoragePermissions(requestPermissionLauncher)
    }
    
    fun requestLegacyStoragePermissions(requestPermissionLauncher: ActivityResultLauncher<Array<String>>) {
        val permissions = mutableListOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        
        if (permissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }) {
            listener?.onPermissionGranted()
        } else {
            // Request the permissions
            try {
                requestPermissionLauncher.launch(permissions.toTypedArray())
            } catch (e: Exception) {
                Log.e(TAG, "Error requesting permissions", e)
                listener?.onPermissionDenied()
            }
        }
    }
    
    fun handlePermissionResult(permissions: Map<String, Boolean>) {
        if (permissions.entries.all { it.value }) {
            // All permissions granted
            listener?.onPermissionGranted()
        } else {
            // Some permissions denied
            listener?.onPermissionDenied()
        }
    }
    
    fun showPermissionsDialog() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // For Android 11+, show system dialog to request full storage access
            try {
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:${context.packageName}")
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error showing permissions dialog", e)
                Toast.makeText(context, "Cannot request permissions: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            // For older versions, suggest user to reinstall or clear app data
            androidx.appcompat.app.AlertDialog.Builder(context)
                .setTitle("Storage Permission Required")
                .setMessage("This app needs storage permissions to browse files. Please grant the permissions in the app settings.")
                .setPositiveButton("Open Settings") { _, _ ->
                    try {
                        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        intent.data = Uri.fromFile(android.os.Environment.getExternalStorageDirectory())
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Cannot open settings: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
} 