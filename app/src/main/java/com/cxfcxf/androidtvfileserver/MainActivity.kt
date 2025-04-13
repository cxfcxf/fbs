package com.cxfcxf.androidtvfileserver

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cxfcxf.androidtvfileserver.databinding.ActivityMainBinding
import com.google.gson.Gson
import com.koushikdutta.async.http.server.AsyncHttpServer
import com.koushikdutta.async.http.server.AsyncHttpServerRequest
import com.koushikdutta.async.http.server.AsyncHttpServerResponse
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : AppCompatActivity(), 
                    FileManager.FileOperationListener,
                    PermissionManager.PermissionListener,
                    WebServer.ServerListener,
                    UIManager.UIListener {
                        
    private lateinit var binding: ActivityMainBinding
    private lateinit var fileManager: FileManager
    private lateinit var permissionManager: PermissionManager
    private lateinit var webServer: WebServer
    private lateinit var uiManager: UIManager
    
    private val TAG = "MainActivity"
    
    // Track when the first back button was pressed
    private var backPressedTime: Long = 0
    private val backToExitPressedInterval: Long = 2000 // 2 seconds

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissionManager.handlePermissionResult(permissions)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize components
        initializeComponents()
        
        // Set up UI
        uiManager.setupUI()
        
        // Check permissions
        permissionManager.checkPermissions(requestPermissionLauncher)
    }

    private fun initializeComponents() {
        // Initialize file manager
        fileManager = FileManager(this)
        fileManager.setListener(this)
        
        // Initialize permission manager
        permissionManager = PermissionManager(this)
        permissionManager.setListener(this)
        
        // Initialize UI manager
        uiManager = UIManager(this, binding, fileManager)
        uiManager.setListener(this)
        
        // Initialize web server
        webServer = WebServer(this, fileManager)
        webServer.setListener(this)
    }

    override fun onResume() {
        super.onResume()
        
        // Setup permission button
        uiManager.setupPermissionButton()
        
        // Check if we gained the MANAGE_EXTERNAL_STORAGE permission while away
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            try {
                fileManager.setCurrentDirectory(Environment.getExternalStorageDirectory())
            } catch (e: Exception) {
                Log.e(TAG, "Error accessing storage in onResume", e)
            }
        }
    }
    
    override fun onBackPressed() {
        // Handle back button press
        val currentDir = fileManager.getCurrentDirectory()
        val parent = currentDir.parentFile
        
        if (parent != null && fileManager.getCurrentDirectory().absolutePath != "/") {
            // If we can navigate up, do that instead of exiting
            fileManager.navigateToParentDirectory()
        } else {
            // Otherwise handle back to exit
            if (backPressedTime + backToExitPressedInterval > System.currentTimeMillis()) {
                super.onBackPressed()
            } else {
                Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT).show()
                backPressedTime = System.currentTimeMillis()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Stop server when activity is destroyed
        if (webServer.isRunning()) {
            webServer.stopServer()
        }
    }
    
    // FileManager.FileOperationListener implementation
    override fun onDirectoryLoaded(files: List<File>) {
        runOnUiThread {
            uiManager.updateFileList(files)
            uiManager.updateCurrentPath(fileManager.getCurrentDirectory().absolutePath)
        }
    }
    
    override fun onError(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
    
    // PermissionManager.PermissionListener implementation
    override fun onPermissionGranted() {
        try {
            fileManager.setCurrentDirectory(Environment.getExternalStorageDirectory())
            Toast.makeText(this, "Permission granted. Accessing external storage.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error accessing external storage after permissions granted", e)
            fileManager.fallbackToAppStorage()
        }
    }
    
    override fun onPermissionDenied() {
        fileManager.fallbackToAppStorage()
        Toast.makeText(this, "Limited access: Long-press 'Parent' button to request full access", Toast.LENGTH_LONG).show()
    }
    
    // WebServer.ServerListener implementation
    override fun onServerStarted(serverUrl: String) {
        runOnUiThread {
            uiManager.updateServerStatus("Running", serverUrl)
        }
    }
    
    override fun onServerStopped() {
        runOnUiThread {
            uiManager.updateServerStatus("Stopped", null)
        }
    }
    
    override fun onServerError(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            uiManager.updateServerStatus("Error", null)
        }
    }
    
    override fun onUploadProgress(filename: String, totalBytes: Long, mbPerSecond: Double) {
        runOnUiThread {
            uiManager.updateUploadProgress(filename, totalBytes, mbPerSecond)
        }
    }
    
    override fun onUploadComplete(filename: String, fileSize: Long) {
        runOnUiThread {
            Toast.makeText(this, 
                "File uploaded: $filename ($fileSize bytes)", 
                Toast.LENGTH_SHORT).show()
        }
    }
    
    // UIManager.UIListener implementation
    override fun onNavigateToParentDirectory() {
        fileManager.navigateToParentDirectory()
    }
    
    override fun onToggleServer() {
        if (webServer.isRunning()) {
            webServer.stopServer()
        } else {
            webServer.startServer()
        }
    }
    
    override fun onFileSelected(file: File) {
        if (file.isDirectory) {
            fileManager.setCurrentDirectory(file)
        } else if (file.extension.equals("apk", ignoreCase = true)) {
            fileManager.installApk(file)
        }
    }
    
    override fun onFileDelete(file: File) {
        // Show confirmation dialog
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete File")
            .setMessage("Are you sure you want to delete ${file.name}?")
            .setPositiveButton("Delete") { _, _ ->
                if (fileManager.deleteFile(file)) {
                    Toast.makeText(this, "File deleted: ${file.name}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed to delete file", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
} 