package com.cxfcxf.androidtvfileserver

import android.content.Context
import android.os.Handler
import android.util.Log
import android.widget.Toast
import com.google.gson.Gson
import com.koushikdutta.async.http.server.AsyncHttpServer
import com.koushikdutta.async.http.server.AsyncHttpServerRequest
import com.koushikdutta.async.http.server.AsyncHttpServerResponse
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.NetworkInterface

class WebServer(private val context: Context, private val fileManager: FileManager) {
    private val TAG = "WebServer"
    private var server: AsyncHttpServer? = null
    private var serverPort = 8080
    private val gson = Gson()
    
    interface ServerListener {
        fun onServerStarted(serverUrl: String)
        fun onServerStopped()
        fun onServerError(message: String)
        fun onUploadProgress(filename: String, totalBytes: Long, mbPerSecond: Double)
        fun onUploadComplete(filename: String, fileSize: Long)
    }
    
    private var listener: ServerListener? = null
    
    fun setListener(listener: ServerListener) {
        this.listener = listener
    }
    
    fun isRunning(): Boolean {
        return server != null
    }
    
    fun startServer() {
        server = AsyncHttpServer().apply {
            // Serve the file browser HTML page
            get("/") { _: AsyncHttpServerRequest, response: AsyncHttpServerResponse ->
                val htmlTemplate = loadAssetAsString("browser.html")
                // Replace placeholders
                val html = htmlTemplate.replace("\${currentDir}", fileManager.getCurrentDirectory().absolutePath)
                response.send("text/html", html)
            }
            
            // Serve CSS
            get("/styles.css") { _: AsyncHttpServerRequest, response: AsyncHttpServerResponse ->
                response.setContentType("text/css")
                response.send(loadAssetAsString("styles.css"))
            }
            
            // Serve JavaScript
            get("/scripts.js") { _: AsyncHttpServerRequest, response: AsyncHttpServerResponse ->
                // Replace currentDir placeholder in JS
                val jsTemplate = loadAssetAsString("scripts.js")
                val js = jsTemplate.replace("\${currentDir}", fileManager.getCurrentDirectory().absolutePath)
                response.setContentType("application/javascript")
                response.send(js)
            }
            
            // List files as JSON
            get("/api/files") { _: AsyncHttpServerRequest, response: AsyncHttpServerResponse ->
                val files = fileManager.getCurrentDirectory().listFiles()?.map { 
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
                        fileManager.setCurrentDirectory(targetDir)
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
                val file = File(fileManager.getCurrentDirectory(), path)
                
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
                    val file = File(fileManager.getCurrentDirectory(), filename)
                    
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
                    val handler = android.os.Handler(context.mainLooper)
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
                                            Toast.makeText(context, 
                                                "File partially uploaded (timeout): $filename", 
                                                Toast.LENGTH_SHORT).show()
                                            fileManager.loadFiles()
                                            
                                            // Reset UI status
                                            val ip = getLocalIpAddress()
                                            listener?.onServerStarted("http://$ip:$serverPort")
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
                                    
                                    // Notify listener about progress
                                    listener?.onUploadProgress(filename, totalBytes, mbPerSecond)
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
                                
                                // Notify listener about complete upload
                                listener?.onUploadComplete(filename, fileSize)
                                
                                // Refresh file list
                                fileManager.loadFiles()
                                
                                // Restore server status after a short delay
                                Handler(context.mainLooper).postDelayed({
                                    val ip = getLocalIpAddress()
                                    if (ip != null) {
                                        listener?.onServerStarted("http://$ip:$serverPort")
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

        try {
            server?.listen(serverPort)
            
            // Get server URL
            val ip = getLocalIpAddress()
            if (ip != null) {
                val url = "http://$ip:$serverPort"
                listener?.onServerStarted(url)
            } else {
                listener?.onServerStarted("http://localhost:$serverPort")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting server", e)
            listener?.onServerError("Failed to start server: ${e.message}")
        }
    }
    
    fun stopServer() {
        try {
            server?.stop()
            server = null
            listener?.onServerStopped()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping server", e)
        }
    }
    
    // Helper method to get the local IP address
    fun getLocalIpAddress(): String? {
        try {
            val en = NetworkInterface.getNetworkInterfaces()
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
    
    // Helper method to load asset file as string
    private fun loadAssetAsString(fileName: String): String {
        return try {
            context.assets.open(fileName).bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            Log.e(TAG, "Error loading asset: $fileName", e)
            "Error loading $fileName: ${e.message}"
        }
    }
    
    private fun getMimeType(fileName: String): String {
        return when {
            fileName.endsWith(".html", true) -> "text/html"
            fileName.endsWith(".css", true) -> "text/css"
            fileName.endsWith(".js", true) -> "application/javascript"
            fileName.endsWith(".jpg", true) -> "image/jpeg"
            fileName.endsWith(".jpeg", true) -> "image/jpeg"
            fileName.endsWith(".png", true) -> "image/png"
            fileName.endsWith(".gif", true) -> "image/gif"
            fileName.endsWith(".txt", true) -> "text/plain"
            fileName.endsWith(".pdf", true) -> "application/pdf"
            fileName.endsWith(".mp4", true) -> "video/mp4"
            fileName.endsWith(".mp3", true) -> "audio/mpeg"
            fileName.endsWith(".apk", true) -> "application/vnd.android.package-archive"
            else -> "application/octet-stream"
        }
    }
} 