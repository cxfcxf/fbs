package com.example.androidtvfileserver

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
import com.example.androidtvfileserver.databinding.ActivityMainBinding
import com.google.gson.Gson
import com.koushikdutta.async.http.server.AsyncHttpServer
import com.koushikdutta.async.http.server.AsyncHttpServerRequest
import com.koushikdutta.async.http.server.AsyncHttpServerResponse
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var fileAdapter: FileAdapter
    private var currentDirectory = File("/")  // Start with a safe default
    private var server: AsyncHttpServer? = null
    private var serverPort = 8080
    private val gson = Gson()
    private val TAG = "MainActivity"

    // Track when the first back button was pressed
    private var backPressedTime: Long = 0
    private val backToExitPressedInterval: Long = 2000 // 2 seconds

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.all { it.value }) {
            // All permissions granted
            try {
                currentDirectory = Environment.getExternalStorageDirectory()
                loadFiles()
                Toast.makeText(this, "Permission granted. Accessing external storage.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Error accessing external storage after permissions granted", e)
                fallbackToAppStorage()
            }
        } else {
            // Some permissions denied
            fallbackToAppStorage()
            
            // Show dialog explaining how to access more files
            Toast.makeText(this, "Limited access: Long-press 'Common Directories' button to request full access", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize with app-specific directory
        currentDirectory = getExternalFilesDir(null) ?: filesDir

        // Initialize server status displays with stopped state
        binding.serverStatus.text = "Server Status: Stopped"
        binding.serverUrl.text = "Server URL: Not Started"
        binding.serverStatus.visibility = View.VISIBLE
        binding.serverUrl.visibility = View.VISIBLE
        
        setupFileList()
        setupNavigationButtons()  // Now includes server button functionality
        setupTvNavigation()
        
        // Check permissions first
        checkPermissions()
        
        // Don't auto-start the server
    }

    override fun onResume() {
        super.onResume()
        
        // Add permission request to long-press on parent directory button
        binding.parentDirButton.setOnLongClickListener {
            showPermissionsDialog()
            true
        }
        
        // Check if we gained the MANAGE_EXTERNAL_STORAGE permission while away
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            try {
                currentDirectory = Environment.getExternalStorageDirectory()
                loadFiles()
            } catch (e: Exception) {
                Log.e(TAG, "Error accessing storage in onResume", e)
            }
        }
    }

    private fun setupFileList() {
        fileAdapter = FileAdapter { file ->
            if (file.isDirectory) {
                currentDirectory = file
                loadFiles()
            } else if (file.extension.equals("apk", ignoreCase = true)) {
                installApk(file)
            }
        }
        
        // Set up file deletion handler
        fileAdapter.setOnItemDeleteListener { file ->
            deleteFile(file)
        }

        binding.fileList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
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

    private fun setupNavigationButtons() {
        // Add button to go to parent directory
        binding.parentDirButton.setOnClickListener {
            navigateToParentDirectory()
        }
        
        // Server toggle button is now in the top navigation area
        binding.toggleServer.setOnClickListener {
            if (server == null) {
                startServer()
                binding.toggleServer.text = "Stop Server"
            } else {
                stopServer()
                binding.toggleServer.text = "Start Server"
            }
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
                        android.graphics.Color.parseColor("#4CAF50") // Green color
                    )
                }
            } else {
                // Restore to gray when unfocused
                if (view is Button) {
                    view.backgroundTintList = android.content.res.ColorStateList.valueOf(
                        android.graphics.Color.parseColor("#757575") // Gray color
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

    private fun checkPermissions() {
        // Check if we're running on a TV device
        val isTvDevice = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        
        // For Android 11 (API 30) or higher, we need special handling for storage
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                // We have full storage access permission
                try {
                    currentDirectory = Environment.getExternalStorageDirectory()
                    loadFiles()
                } catch (e: Exception) {
                    Log.e(TAG, "Error accessing external storage", e)
                    fallbackToAppStorage()
                }
            } else {
                // Request for full storage access on Android 11+
                val requestStorageIntent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                requestStorageIntent.data = Uri.parse("package:$packageName")
                
                try {
                    startActivity(requestStorageIntent)
                    Toast.makeText(this, 
                        "Please grant 'All Files Access' permission to browse all files", 
                        Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Log.e(TAG, "Error requesting all files access", e)
                    // Fallback to limited access
                    requestLegacyStoragePermissions()
                }
                
                // For now, use app-specific storage until permission is granted
                fallbackToAppStorage()
            }
            return
        }
        
        // Legacy permission handling for Android 10 and below
        requestLegacyStoragePermissions()
    }
    
    private fun requestLegacyStoragePermissions() {
        val permissions = mutableListOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        
        if (permissions.all {
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }) {
            try {
                // We have the permissions, try to access external storage
                currentDirectory = Environment.getExternalStorageDirectory()
                loadFiles()
            } catch (e: Exception) {
                Log.e(TAG, "Error accessing external storage", e)
                fallbackToAppStorage()
            }
        } else {
            // Request the permissions
            try {
                requestPermissionLauncher.launch(permissions.toTypedArray())
            } catch (e: Exception) {
                Log.e(TAG, "Error requesting permissions", e)
                fallbackToAppStorage()
            }
        }
    }
    
    private fun fallbackToAppStorage() {
        currentDirectory = getExternalFilesDir(null) ?: filesDir
        loadFiles()
        Toast.makeText(this, 
            "Limited access mode: You can only see files in app-specific directories\n" +
            "Path: ${currentDirectory.absolutePath}", 
            Toast.LENGTH_LONG).show()
    }

    private fun loadFiles() {
        try {
            if (!canAccessDirectory(currentDirectory)) {
                // Show permission message
                Toast.makeText(
                    this,
                    "Cannot access ${currentDirectory.absolutePath}\n" +
                    "Long-press 'Parent' button to request more permissions",
                    Toast.LENGTH_LONG
                ).show()
                
                // Try to list available files anyway
                val files = currentDirectory.listFiles()?.toList() ?: emptyList()
                if (files.isEmpty()) {
                    binding.currentPathText.text = "Current: ${currentDirectory.absolutePath} (NO ACCESS)"
                    fileAdapter.submitList(emptyList())
                } else {
                    binding.currentPathText.text = "Current: ${currentDirectory.absolutePath}"
                    fileAdapter.submitList(files)
                }
                return
            }
            
            val files = currentDirectory.listFiles()?.toList() ?: emptyList()
            fileAdapter.submitList(files)
            
            // Update current path display
            binding.currentPathText.text = "Current: ${currentDirectory.absolutePath}"
            
            // If no files are visible, check if this is due to permissions
            if (files.isEmpty() && !Environment.isExternalStorageManager() && 
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // This might be a permission issue
                Toast.makeText(
                    this,
                    "No files visible. You may need more permissions.\n" +
                    "Long-press 'Parent' button for options",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading files", e)
            Toast.makeText(this, "Error loading files: ${e.message}", Toast.LENGTH_SHORT).show()
            fallbackToAppStorage()
        }
    }
    
    private fun canAccessDirectory(directory: File): Boolean {
        return directory.exists() && directory.canRead() && try {
            // Try to actually list files as an additional check
            directory.listFiles() != null
        } catch (e: Exception) {
            false
        }
    }

    private fun installApk(file: File) {
        try {
            // Create the intent to install the APK
            val intent = Intent(Intent.ACTION_VIEW)
            
            // For Android N and above (24+), we need to use FileProvider
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val apkUri = androidx.core.content.FileProvider.getUriForFile(
                    this,
                    applicationContext.packageName + ".provider",
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
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Install APK")
                .setMessage("Do you want to install ${file.name}?")
                .setPositiveButton("Install") { _, _ ->
                    try {
                        startActivity(intent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error starting APK install activity", e)
                        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
                
        } catch (e: Exception) {
            // Handle errors gracefully
            Log.e(TAG, "Error preparing APK installation", e)
            Toast.makeText(this, "Cannot install APK: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun startServer() {
        server = AsyncHttpServer().apply {
            // Serve the file browser HTML page
            get("/") { request: AsyncHttpServerRequest, response: AsyncHttpServerResponse ->
                val html = generateFileBrowserHtml()
                response.send("text/html", html)
            }
            
            // List files as JSON
            get("/api/files") { request: AsyncHttpServerRequest, response: AsyncHttpServerResponse ->
                val files = currentDirectory.listFiles()?.map { 
                    mapOf(
                        "name" to it.name,
                        "isDirectory" to it.isDirectory,
                        "size" to it.length(),
                        "lastModified" to it.lastModified()
                    )
                } ?: emptyList()
                
                response.send("application/json", gson.toJson(files))
            }
            
            // Handle directory navigation
            get("/navigate") { request: AsyncHttpServerRequest, response: AsyncHttpServerResponse ->
                val dirPath = request.query?.getString("path")
                
                if (dirPath != null) {
                    val targetDir = File(dirPath)
                    
                    if (targetDir.exists() && targetDir.isDirectory) {
                        currentDirectory = targetDir
                        runOnUiThread {
                            loadFiles()
                        }
                        response.send("application/json", gson.toJson(mapOf("success" to true, "path" to targetDir.absolutePath)))
                    } else {
                        response.code(404)
                        response.send("application/json", gson.toJson(mapOf("success" to false, "error" to "Directory not found")))
                    }
                } else {
                    response.code(400)
                    response.send("application/json", gson.toJson(mapOf("success" to false, "error" to "Missing path parameter")))
                }
            }
            
            // Serve static files
            get("/files/.*") { request: AsyncHttpServerRequest, response: AsyncHttpServerResponse ->
                val path = request.path.removePrefix("/files")
                val file = File(currentDirectory, path)
                
                if (file.exists() && file.isFile) {
                    response.setContentType(getMimeType(file.name))
                    response.sendFile(file)
                } else {
                    response.code(404)
                    response.send("File not found")
                }
            }

            // File upload endpoint with improved large file handling
            post("/upload-simple") { request: AsyncHttpServerRequest, response: AsyncHttpServerResponse ->
                try {
                    val filename = request.query?.getString("filename") ?: "upload_${System.currentTimeMillis()}"
                    Log.d(TAG, "Upload request received for file: $filename")
                    
                    // We'll reuse the same filename (overwrite existing files with the same name)
                    val file = File(currentDirectory, filename)
                    
                    // Make sure we have a clean start - delete any existing file with same name
                    if (file.exists()) {
                        file.delete()
                    }
                    
                    // Use a buffer to handle the data
                    val outputStream = FileOutputStream(file)
                    var totalBytes = 0L
                    var lastProgressUpdate = 0L
                    var uploadStartTime = System.currentTimeMillis()
                    var lastDataTime = System.currentTimeMillis() // Track when we last received data
                    var uploadActive = true
                    
                    // Setup a timeout handler to close hanging uploads
                    val handler = android.os.Handler(mainLooper)
                    val timeoutRunnable = object : Runnable {
                        override fun run() {
                            if (uploadActive) {
                                val idleTime = System.currentTimeMillis() - lastDataTime
                                if (idleTime > 60000) { // 60 seconds of inactivity
                                    Log.w(TAG, "Upload for $filename timed out after $idleTime ms inactivity")
                                    try {
                                        // Close the stream and process the partial file
                                        uploadActive = false
                                        try {
                                            outputStream.flush()
                                            outputStream.close()
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Error closing stream on timeout", e)
                                        }
                                        
                                        // Complete the upload with what we have
                                        if (file.exists() && file.length() > 0) {
                                            Log.d(TAG, "File partially uploaded (idle timeout): $filename (${file.length()} bytes)")
                                            runOnUiThread {
                                                Toast.makeText(this@MainActivity, 
                                                    "File partially uploaded (timeout): $filename", 
                                                    Toast.LENGTH_SHORT).show()
                                                loadFiles()
                                                
                                                // Reset UI status
                                                val ip = getLocalIpAddress()
                                                binding.serverStatus.text = if (ip != null) {
                                                    "Server Status: Running on port $serverPort" 
                                                } else {
                                                    "Server Status: Running on port $serverPort (IP unknown)"
                                                }
                                            }
                                        } else {
                                            // No useful data, delete the file
                                            file.delete()
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error handling upload timeout", e)
                                    }
                                } else {
                                    // Schedule next check
                                    handler.postDelayed(this, 5000) // Check every 5 seconds
                                }
                            }
                        }
                    }
                    
                    // Start the timeout checker
                    handler.postDelayed(timeoutRunnable, 10000) // First check after 10 seconds
                    
                    // Set header to try to ensure connection gets closed properly
                    response.getHeaders().add("Connection", "close")
                    
                    // Use a data callback to write the file data
                    request.setDataCallback { _, byteBuf ->
                        try {
                            // Update the last data receipt time
                            lastDataTime = System.currentTimeMillis()
                            
                            // Get the number of bytes available
                            val size = byteBuf.remaining()
                            if (size > 0) {
                                // Create a buffer to hold the data
                                val bytes = ByteArray(size)
                                // Read from the ByteBuffer
                                byteBuf.get(bytes)
                                // Write to the file
                                outputStream.write(bytes)
                                totalBytes += size
                                
                                // Show progress notification periodically (every 5MB or 10 seconds)
                                val now = System.currentTimeMillis()
                                val timeSinceLastUpdate = now - lastProgressUpdate
                                val shouldUpdateProgress = 
                                    totalBytes - lastProgressUpdate >= 5 * 1024 * 1024 || // 5MB
                                    timeSinceLastUpdate >= 10000 // 10 seconds
                                
                                if (shouldUpdateProgress || totalBytes % (1024 * 1024) == 0L) {
                                    lastProgressUpdate = totalBytes
                                    
                                    // Calculate MB/s
                                    val elapsedSeconds = (now - uploadStartTime) / 1000.0
                                    val mbPerSecond = if (elapsedSeconds > 0) 
                                        (totalBytes / (1024.0 * 1024.0)) / elapsedSeconds 
                                    else 
                                        0.0
                                    
                                    Log.d(TAG, "Upload progress: ${totalBytes / (1024 * 1024)} MB so far (${String.format("%.2f", mbPerSecond)} MB/s)")
                                    
                                    // Show progress on UI thread
                                    runOnUiThread {
                                        binding.serverStatus.text = "Server Status: Receiving upload (${totalBytes / (1024 * 1024)} MB @ ${String.format("%.1f", mbPerSecond)} MB/s)"
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error writing data chunk to file", e)
                            // We can't send a response or close the stream here as it would disrupt the upload
                            // Just log the error and continue, we'll handle cleanup in the end callback
                        }
                    }
                    
                    // Set an end callback that will be called when the request is complete
                    request.setEndCallback {
                        try {
                            // Mark upload as inactive and remove timeout handler
                            uploadActive = false
                            handler.removeCallbacks(timeoutRunnable)
                            
                            // Close the output stream
                            outputStream.flush()
                            outputStream.close()
                            
                            val fileSize = file.length()
                            
                            if (fileSize > 0) {
                                // Success - the file was uploaded
                                Log.d(TAG, "Upload completed: $filename ($fileSize bytes)")
                                
                                // IMPORTANT: Immediately show upload notification and refresh
                                // This ensures the notification shows even if the response fails
                                runOnUiThread {
                                    Toast.makeText(this@MainActivity, 
                                        "File uploaded: $filename ($fileSize bytes)", 
                                        Toast.LENGTH_SHORT).show()
                                    loadFiles()
                                    
                                    // Restore server status
                                    val ip = getLocalIpAddress()
                                    if (ip != null) {
                                        binding.serverStatus.text = "Server Status: Running on port $serverPort"
                                    } else {
                                        binding.serverStatus.text = "Server Status: Running on port $serverPort (IP unknown)"
                                    }
                                }
                                
                                // Forcibly reset UI after a short delay in case the client connection hangs
                                Handler(mainLooper).postDelayed({
                                    runOnUiThread {
                                        val ip = getLocalIpAddress()
                                        if (ip != null) {
                                            binding.serverStatus.text = "Server Status: Running on port $serverPort"
                                        } else {
                                            binding.serverStatus.text = "Server Status: Running on port $serverPort (IP unknown)"
                                        }
                                    }
                                }, 15000) // Reset after 15 seconds maximum
                                
                                // Try to send a success response, but it's ok if this fails
                                // because we've already handled the upload on the server side
                                try {
                                    val resultObj = mapOf(
                                        "success" to true,
                                        "filename" to filename,
                                        "size" to fileSize,
                                        "path" to file.absolutePath,
                                        "lastModified" to file.lastModified()
                                    )
                                    
                                    // Add connection close header to forcibly terminate connection
                                    response.getHeaders().set("Connection", "close")
                                    response.send("application/json", gson.toJson(resultObj))
                                    Log.d(TAG, "Upload success response sent")
                                } catch (e: Exception) {
                                    // If we can't send the response, that's ok - the file is already saved
                                    Log.e(TAG, "Failed to send success response, but file was saved", e)
                                }
                                
                            } else {
                                // No data was received
                                Log.e(TAG, "No data received for file: $filename")
                                file.delete() // Delete the empty file
                                
                                try {
                                    response.code(400)
                                    response.send("application/json", gson.toJson(mapOf(
                                        "success" to false,
                                        "error" to "No data received"
                                    )))
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to send error response", e)
                                }
                            }
                        } catch (e: Exception) {
                            // Error in completion handling
                            Log.e(TAG, "Error finalizing upload", e)
                            
                            try {
                                // Try to clean up
                                outputStream.close()
                            } catch (e2: Exception) {
                                // Ignore
                            }
                            
                            // Delete the incomplete file
                            file.delete()
                            
                            // Try to send error response
                            try {
                                response.code(500)
                                response.send("application/json", gson.toJson(mapOf(
                                    "success" to false,
                                    "error" to "Error finalizing upload: ${e.message}"
                                )))
                            } catch (e2: Exception) {
                                Log.e(TAG, "Failed to send error response", e2)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Handle setup errors
                    Log.e(TAG, "Error setting up upload", e)
                    response.code(500)
                    response.send("application/json", gson.toJson(mapOf(
                        "success" to false,
                        "error" to "Upload setup error: ${e.message}"
                    )))
                }
            }
        }

        server?.listen(serverPort)
        
        // Update UI with server information
        val ip = getLocalIpAddress()
        binding.serverStatus.text = "Server Status: Running on port $serverPort"
        binding.toggleServer.text = "Stop Server"
        
        // Display server URL
        if (ip != null) {
            val url = "http://$ip:$serverPort"
            binding.serverUrl.text = "Server URL: $url"
        } else {
            binding.serverUrl.text = "Server URL: http://localhost:$serverPort (IP unknown)"
        }
    }
    
    // Helper method to get the local IP address
    private fun getLocalIpAddress(): String? {
        try {
            val en = java.net.NetworkInterface.getNetworkInterfaces()
            while (en.hasMoreElements()) {
                val intf = en.nextElement()
                val enumIpAddr = intf.inetAddresses
                while (enumIpAddr.hasMoreElements()) {
                    val inetAddress = enumIpAddr.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress is java.net.Inet4Address) {
                        return inetAddress.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting IP address", e)
        }
        return null
    }
    
    private fun generateFileBrowserHtml(): String {
        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Android TV File Server</title>
            <style>
                body { font-family: Arial, sans-serif; margin: 0; padding: 20px; }
                h1 { color: #333; }
                .file-list { margin-top: 20px; }
                .file-item { 
                    padding: 10px; 
                    border-bottom: 1px solid #eee; 
                    display: flex; 
                    align-items: center;
                    justify-content: space-between; 
                }
                .file-item:hover { background-color: #f5f5f5; }
                .folder { color: #4285f4; font-weight: bold; }
                .file { color: #333; }
                .file-name { flex: 1; }
                .file-meta { color: #666; font-size: 0.9em; margin-left: 10px; white-space: nowrap; }
                .upload-area { margin: 20px 0; padding: 30px; border: 2px dashed #ccc; text-align: center; }
                .upload-area.highlight { border-color: #4285f4; background-color: #f0f8ff; }
                .upload-button { background-color: #4285f4; color: white; border: none; padding: 10px 20px; cursor: pointer; }
                .upload-button:hover { background-color: #3367d6; }
                .upload-status { margin-top: 10px; min-height: 24px; }
                .upload-progress { padding: 8px; background-color: #f8f8f8; border-radius: 4px; margin-bottom: 10px; }
                .success { color: #4CAF50; font-weight: bold; }
                .error { color: #F44336; font-weight: bold; }
                .warning { color: #FF9800; font-weight: bold; }
            </style>
        </head>
        <body>
            <h1>Android TV File Server</h1>
            <p>Current Directory: ${currentDirectory.absolutePath}</p>
            
            <div class="upload-area" id="dropArea">
                <p>Drag & drop files here or click to select files</p>
                <input type="file" id="fileInput" style="display: none;" multiple>
                <button class="upload-button" id="uploadButton">Choose Files</button>
                <div class="upload-status" id="uploadStatus"></div>
            </div>
            
            <div class="file-list" id="fileList">
                <!-- Files will be loaded here -->
            </div>
            
            <script>
                // DOM elements
                var dropArea = document.getElementById('dropArea');
                var fileInput = document.getElementById('fileInput');
                var uploadButton = document.getElementById('uploadButton');
                var uploadStatus = document.getElementById('uploadStatus');
                var fileList = document.getElementById('fileList');
                
                // Current directory
                var currentDir = '${currentDirectory.absolutePath}';
                
                // Load files on page load
                loadFiles();
                
                // Event listeners
                dropArea.addEventListener('dragover', function(e) {
                    e.preventDefault();
                    dropArea.classList.add('highlight');
                });
                
                dropArea.addEventListener('dragleave', function() {
                    dropArea.classList.remove('highlight');
                });
                
                dropArea.addEventListener('drop', function(e) {
                    e.preventDefault();
                    dropArea.classList.remove('highlight');
                    
                    if (e.dataTransfer.files.length > 0) {
                        uploadFile(e.dataTransfer.files[0]);
                    }
                });
                
                uploadButton.addEventListener('click', function() {
                    fileInput.click();
                });
                
                fileInput.addEventListener('change', function() {
                    if (fileInput.files.length > 0) {
                        uploadFile(fileInput.files[0]);
                    }
                });
                
                // Functions
                function loadFiles() {
                    fetch('/api/files')
                        .then(function(response) { return response.json(); })
                        .then(function(data) {
                            fileList.innerHTML = '';
                            
                            // Add parent directory option if not at root
                            var parentDir = currentDir.substring(0, currentDir.lastIndexOf('/'));
                            
                            if (parentDir && parentDir !== currentDir) {
                                var parentItem = document.createElement('div');
                                parentItem.className = 'file-item folder';
                                parentItem.innerHTML = '<span class="file-name">📁 <a href="#" onclick="navigateToDir(\'' + parentDir + '\'); return false;">..</a> (Parent Directory)</span>';
                                fileList.appendChild(parentItem);
                            }
                            
                            // Add files and directories
                            if (data && Array.isArray(data)) {
                                for (var i = 0; i < data.length; i++) {
                                    var fileObj = data[i];
                                    var fileItem = document.createElement('div');
                                    fileItem.className = 'file-item';
                                    
                                    if (fileObj.isDirectory) {
                                        fileItem.className += ' folder';
                                        var dirPath = currentDir + '/' + fileObj.name;
                                        fileItem.innerHTML = '📁 <span class="file-name"><a href="#" onclick="navigateToDir(\'' + dirPath + '\'); return false;">' + fileObj.name + '</a></span>';
                                    } else {
                                        fileItem.className += ' file';
                                        // Format the date for display
                                        var lastModified = new Date(fileObj.lastModified);
                                        var formattedDate = lastModified.toISOString().replace('T', ' ').substring(0, 19);
                                        fileItem.innerHTML = 
                                            '<span class="file-name">📄 <a href="/files/' + fileObj.name + '" download>' + fileObj.name + '</a></span>' +
                                            '<span class="file-meta">' + formatSize(fileObj.size) + ' - ' + formattedDate + '</span>';
                                    }
                                    
                                    fileList.appendChild(fileItem);
                                }
                            } else {
                                fileList.innerHTML = '<p>No files found</p>';
                            }
                        })
                        .catch(function(error) {
                            console.error('Error loading files:', error);
                            fileList.innerHTML = '<p>Error loading files</p>';
                        });
                }
                
                function uploadFile(file) {
                    if (!file) return;
                    
                    // Create upload progress element
                    var progressDiv = document.createElement('div');
                    progressDiv.className = 'upload-progress';
                    progressDiv.innerHTML = '<span>Preparing to upload ' + file.name + ' (' + formatSize(file.size) + ')</span>';
                    uploadStatus.innerHTML = '';
                    uploadStatus.appendChild(progressDiv);
                    
                    console.log('Uploading file:', file.name, 'Size:', file.size, 'bytes');
                    
                    // Reset the file input to allow uploading the same file again
                    fileInput.value = '';
                    
                    // Set a timer to show that the upload is still in progress
                    var uploadStartTime = Date.now();
                    var uploadTimer = setInterval(function() {
                        var elapsed = Math.floor((Date.now() - uploadStartTime) / 1000);
                        progressDiv.innerHTML = '<span>Uploading ' + file.name + ' (' + formatSize(file.size) + ') - ' + elapsed + 's elapsed</span>';
                        
                        // Show a timeout warning after 2 minutes
                        if (elapsed > 120) {
                            progressDiv.innerHTML += '<br><span class="warning">Upload is taking longer than expected. Please be patient for large files.</span>';
                        }
                    }, 1000);
                    
                    // Use binary upload with the actual file content and a longer timeout
                    var xhr = new XMLHttpRequest();
                    xhr.open('POST', '/upload-simple?filename=' + encodeURIComponent(file.name));
                    xhr.timeout = 300000; // 5 minute timeout for large files
                    
                    // Set a flag to track completion state
                    var uploadCompleted = false;
                    var forceRefreshTimer = null;
                    var forceCancelTimer = null; // New timer to forcibly abort hanging connections
                    
                    // Function to handle completion, regardless of how it happens
                    function handleCompletion(success, message) {
                        if (uploadCompleted) return; // Prevent double handling
                        uploadCompleted = true;
                        
                        // Clear timers
                        clearInterval(uploadTimer);
                        if (forceRefreshTimer) clearTimeout(forceRefreshTimer);
                        if (forceCancelTimer) clearTimeout(forceCancelTimer);
                        
                        if (success) {
                            progressDiv.innerHTML = '<span class="success">' + message + '</span>';
                        } else {
                            progressDiv.innerHTML = '<span class="error">' + message + '</span>';
                        }
                        
                        // Always refresh the file list
                        loadFiles();
                        
                        // Clear status after a delay
                        setTimeout(function() {
                            uploadStatus.innerHTML = '';
                        }, success ? 5000 : 8000);
                        
                        // Force xhr abort if it's still active
                        try {
                            if (xhr && xhr.readyState !== 4) {
                                console.log('Forcing XHR abort to clean up connection');
                                xhr.abort();
                            }
                        } catch (e) {
                            console.error('Error aborting XHR:', e);
                        }
                    }
                    
                    // Handle errors
                    xhr.onerror = function() {
                        console.error('Upload network error');
                        handleCompletion(false, 'Error: Network error during upload');
                    };
                    
                    // Handle timeouts
                    xhr.ontimeout = function() {
                        console.error('Upload timed out');
                        handleCompletion(false, 'Error: Upload timed out after 5 minutes');
                    };
                    
                    // Handle progress updates
                    xhr.upload.onprogress = function(e) {
                        if (e.lengthComputable) {
                            var percentComplete = Math.floor((e.loaded / e.total) * 100);
                            progressDiv.innerHTML = 
                                '<span>Uploading ' + file.name + ' (' + formatSize(file.size) + ') - ' + 
                                percentComplete + '%</span>';
                                
                            // If we reach 100%, but no completion callback happens within 1 second,
                            // force a refresh
                            if (percentComplete === 100 && !uploadCompleted && !forceRefreshTimer) {
                                console.log('Upload reached 100%, setting timers for completion');
                                
                                // Set a timer to force completion if we don't get a proper response
                                forceRefreshTimer = setTimeout(function() {
                                    console.log('Upload stuck at 100% - forcing completion');
                                    handleCompletion(true, 'Upload complete (force completed): ' + file.name);
                                }, 1000); // 1 second timeout after reaching 100%
                                
                                // Set a timer to forcibly cancel the request after 30 seconds 
                                // to clean up any hanging connections
                                forceCancelTimer = setTimeout(function() {
                                    console.log('Force-cancelling potentially hanging upload request');
                                    if (!uploadCompleted) {
                                        handleCompletion(true, 'Upload complete (connection terminated): ' + file.name);
                                    }
                                }, 30000); // 30 second timeout to force termination
                            }
                        }
                    };
                    
                    // Handle response
                    xhr.onload = function() {
                        try {
                            console.log('Server response received:', xhr.status, xhr.responseText);
                            var result;
                            try {
                                result = JSON.parse(xhr.responseText);
                            } catch (e) {
                                console.error('Error parsing response:', e);
                                result = { success: false, error: 'Invalid server response' };
                            }
                            
                            if (xhr.status === 200 && result.success) {
                                console.log('Upload success');
                                var successMsg = 'Upload complete: ' + 
                                    (result.filename || file.name) + 
                                    ' (' + formatSize(result.size || file.size) + ')';
                                    
                                handleCompletion(true, successMsg);
                            } else {
                                console.error('Server error:', result.error || 'Unknown error');
                                handleCompletion(false, 'Error: ' + (result.error || 'Unknown server error'));
                            }
                        } catch (error) {
                            console.error('Error handling response:', error);
                            handleCompletion(false, 'Error: Failed to process server response');
                        }
                    };
                    
                    // Handle ready state changes for debugging
                    xhr.onreadystatechange = function() {
                        console.log('XHR state:', xhr.readyState);
                        
                        // readyState 4 means DONE (even if we don't get a proper onload event)
                        if (xhr.readyState === 4 && !uploadCompleted) {
                            console.log('XHR completed via readyState change:', xhr.status);
                            // Check if we have a valid response, otherwise force completion
                            if (xhr.status === 0 || xhr.status >= 400) {
                                handleCompletion(false, 'Error: Server error (' + xhr.status + ')');
                            }
                        }
                    };
                    
                    // Send the file
                    try {
                        xhr.send(file);
                        console.log('Upload request sent');
                    } catch (e) {
                        console.error('Error sending upload:', e);
                        handleCompletion(false, 'Error: Failed to send upload');
                    }
                }
                
                function formatSize(bytes) {
                    if (bytes === 0) return '0 Bytes';
                    var k = 1024;
                    var sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
                    var i = Math.floor(Math.log(bytes) / Math.log(k));
                    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
                }

                function navigateToDir(dirPath) {
                    fetch('/navigate?path=' + encodeURIComponent(dirPath))
                        .then(function(response) { return response.json(); })
                        .then(function(result) {
                            if (result.success) {
                                currentDir = result.path;
                                loadFiles();
                            } else {
                                alert('Error: ' + (result.error || 'Unknown error'));
                            }
                        })
                        .catch(function(error) {
                            console.error('Navigation error:', error);
                            alert('Error navigating to directory');
                        });
                }
            </script>
        </body>
        </html>
        """.trimIndent()
    }
    
    private fun getMimeType(filename: String): String {
        return when {
            filename.endsWith(".html", true) -> "text/html"
            filename.endsWith(".css", true) -> "text/css"
            filename.endsWith(".js", true) -> "application/javascript"
            filename.endsWith(".jpg", true) || filename.endsWith(".jpeg", true) -> "image/jpeg"
            filename.endsWith(".png", true) -> "image/png"
            filename.endsWith(".gif", true) -> "image/gif"
            filename.endsWith(".txt", true) -> "text/plain"
            filename.endsWith(".mp4", true) -> "video/mp4"
            filename.endsWith(".mp3", true) -> "audio/mpeg"
            filename.endsWith(".pdf", true) -> "application/pdf"
            filename.endsWith(".apk", true) -> "application/vnd.android.package-archive"
            else -> "application/octet-stream"
        }
    }

    private fun stopServer() {
        server?.stop()
        server = null
        binding.serverStatus.text = "Server Status: Stopped"
        binding.serverUrl.text = "Server URL: Not Started"
        binding.toggleServer.text = "Start Server"
    }

    override fun onDestroy() {
        super.onDestroy()
        stopServer()
    }

    override fun onBackPressed() {
        val parentFile = currentDirectory.parentFile
        if (parentFile != null && parentFile.canRead()) {
            // If we can navigate up to parent directory, do that
            currentDirectory = parentFile
            loadFiles()
        } else {
            // We're at the root or can't navigate up, so handle exit behavior
            if (backPressedTime + backToExitPressedInterval > System.currentTimeMillis()) {
                // Second back press within the interval, actually exit
                super.onBackPressed()
            } else {
                // First back press, show toast and record time
                Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT).show()
                backPressedTime = System.currentTimeMillis()
            }
        }
    }

    private fun navigateToParentDirectory() {
        val parentFile = currentDirectory.parentFile
        if (parentFile != null && parentFile.canRead()) {
            currentDirectory = parentFile
            loadFiles()
        } else {
            Toast.makeText(this, "Cannot access parent directory", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDirectoryPicker() {
        val directories = mutableListOf<Pair<String, File>>()
        
        // Add system directories that might be accessible
        try {
            // Add more root-level directories that might be useful
            val rootLevelDirs = listOf(
                "/storage" to "Storage",
                "/storage/emulated/0" to "Internal Storage",
                "/storage/self/primary" to "Primary Storage",
                "/mnt" to "Mount Points",
                "/mnt/sdcard" to "SD Card",
                "/sdcard" to "SD Card (Alt)",
                "/data/media/0" to "Media",
                "/system" to "System",
                "/system/media" to "System Media"
            )
            
            for ((path, name) in rootLevelDirs) {
                val dir = File(path)
                if (dir.exists() && dir.canRead()) {
                    directories.add(name to dir)
                }
            }
            
            // Add standard Android directories
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val publicDirs = listOf(
                    Environment.DIRECTORY_DOCUMENTS to "Documents",
                    Environment.DIRECTORY_DOWNLOADS to "Downloads",
                    Environment.DIRECTORY_DCIM to "DCIM",
                    Environment.DIRECTORY_PICTURES to "Pictures",
                    Environment.DIRECTORY_MUSIC to "Music",
                    Environment.DIRECTORY_MOVIES to "Movies",
                    Environment.DIRECTORY_PODCASTS to "Podcasts",
                    Environment.DIRECTORY_RINGTONES to "Ringtones",
                    Environment.DIRECTORY_ALARMS to "Alarms",
                    Environment.DIRECTORY_NOTIFICATIONS to "Notifications",
                    Environment.DIRECTORY_AUDIOBOOKS to "Audiobooks",
                    Environment.DIRECTORY_RECORDINGS to "Recordings",
                    Environment.DIRECTORY_SCREENSHOTS to "Screenshots"
                )
                
                for ((type, name) in publicDirs) {
                    Environment.getExternalStoragePublicDirectory(type)?.let {
                        if (it.exists() && it.canRead()) {
                            directories.add(name to it)
                        }
                    }
                }
            } else {
                // Older Android versions
                Environment.getExternalStorageDirectory()?.let {
                    directories.add("External Storage" to it)
                }
                
                // Add common directories for older Android versions
                val extStorage = Environment.getExternalStorageDirectory().absolutePath
                val oldDirs = listOf(
                    "$extStorage/Download" to "Downloads",
                    "$extStorage/DCIM" to "DCIM",
                    "$extStorage/Pictures" to "Pictures",
                    "$extStorage/Music" to "Music",
                    "$extStorage/Movies" to "Movies"
                )
                
                for ((path, name) in oldDirs) {
                    val dir = File(path)
                    if (dir.exists() && dir.canRead()) {
                        directories.add(name to dir)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting system directories", e)
        }
        
        // Add app-specific directories
        directories.add("App Files" to filesDir)
        getExternalFilesDir(null)?.let {
            directories.add("App External Files" to it)
        }
        cacheDir.let {
            directories.add("App Cache" to it)
        }
        
        // Add root directory if we have permission
        try {
            val rootDir = File("/")
            if (rootDir.canRead()) {
                directories.add("Root (/)" to rootDir)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cannot access root directory", e)
        }
        
        // Show directory picker dialog
        val items = directories.map { it.first }.toTypedArray()
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Choose Directory")
            .setItems(items) { _, which ->
                val selectedDir = directories[which].second
                if (selectedDir.exists() && selectedDir.canRead()) {
                    currentDirectory = selectedDir
                    loadFiles()
                } else {
                    Toast.makeText(this, "Cannot access directory: ${selectedDir.absolutePath}", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPermissionsDialog() {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        
        builder.setTitle("File Access Permissions")
            .setMessage("For full access to all files, this app needs special permissions. " +
                    "Would you like to grant full storage access permission now?\n\n" +
                    "Note: For Android TV, you may need to use a USB keyboard/mouse to interact with system permission screens.")
            .setPositiveButton("Grant Permission") { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        intent.data = Uri.parse("package:$packageName")
                        startActivity(intent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error opening permission settings", e)
                        Toast.makeText(this, "Could not open permission settings", Toast.LENGTH_LONG).show()
                    }
                } else {
                    requestLegacyStoragePermissions()
                }
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Use Limited Access") { _, _ ->
                fallbackToAppStorage()
            }
            .show()
    }

    // Add file deletion method
    private fun deleteFile(file: File) {
        // Show confirmation dialog
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete File")
            .setMessage("Are you sure you want to delete ${file.name}?")
            .setPositiveButton("Delete") { _, _ ->
                try {
                    if (file.delete()) {
                        Toast.makeText(this, "${file.name} deleted successfully", Toast.LENGTH_SHORT).show()
                        // Refresh the file list
                        loadFiles()
                    } else {
                        Toast.makeText(this, "Failed to delete ${file.name}", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error deleting file", e)
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
} 