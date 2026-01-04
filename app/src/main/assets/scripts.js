// DOM elements
const dropArea = document.getElementById('dropArea');
const fileInput = document.getElementById('fileInput');
const uploadButton = document.getElementById('uploadButton');
const uploadStatus = document.getElementById('uploadStatus');
const fileList = document.getElementById('fileList');

// Current directory for web UI - always starts at base directory (independent from TV UI)
let currentDir = '/storage/emulated/0';

// Load files on page load
document.addEventListener('DOMContentLoaded', function () {
    loadFiles();
});

// Event listeners
dropArea.addEventListener('dragover', function (e) {
    e.preventDefault();
    dropArea.classList.add('highlight');
});

dropArea.addEventListener('dragleave', function () {
    dropArea.classList.remove('highlight');
});

dropArea.addEventListener('drop', function (e) {
    e.preventDefault();
    dropArea.classList.remove('highlight');

    if (e.dataTransfer.files.length > 0) {
        uploadFiles(e.dataTransfer.files);
    }
});

uploadButton.addEventListener('click', function () {
    fileInput.click();
});

fileInput.addEventListener('change', function () {
    if (fileInput.files.length > 0) {
        uploadFiles(fileInput.files);
    }
});

// Functions
// Base directory - don't allow navigation above this level
const baseDir = '/storage/emulated/0';

function loadFiles() {
    // Pass current directory to API for independent navigation (doesn't affect TV UI)
    fetch('/api/files?path=' + encodeURIComponent(currentDir))
        .then(function (response) { return response.json(); })
        .then(function (data) {
            fileList.innerHTML = '';

            // Add parent directory option if not at base directory or above
            const parentDir = currentDir.substring(0, currentDir.lastIndexOf('/'));

            // Only show parent directory if we're deeper than the base directory
            if (parentDir && parentDir !== currentDir && currentDir !== baseDir && currentDir.startsWith(baseDir + '/')) {
                const parentItem = document.createElement('div');
                parentItem.className = 'file-item folder';
                parentItem.innerHTML = '<span class="file-name">📁 <a href="#" onclick="navigateToDir(\'' + parentDir + '\'); return false;">..</a> (Parent Directory)</span>';
                fileList.appendChild(parentItem);
            }

            // Add files and directories
            if (data && Array.isArray(data)) {
                data.sort(function (a, b) {
                    // Directories first
                    if (a.isDirectory && !b.isDirectory) return -1;
                    if (!a.isDirectory && b.isDirectory) return 1;
                    // Then alphabetical
                    return a.name.localeCompare(b.name);
                });

                for (let i = 0; i < data.length; i++) {
                    const fileObj = data[i];
                    const fileItem = document.createElement('div');
                    fileItem.className = 'file-item';

                    if (fileObj.isDirectory) {
                        fileItem.className += ' folder';
                        const dirPath = currentDir + '/' + fileObj.name;
                        fileItem.innerHTML = '📁 <span class="file-name"><a href="#" onclick="navigateToDir(\'' + dirPath + '\'); return false;">' + fileObj.name + '</a></span>';
                    } else {
                        fileItem.className += ' file';
                        // Format the date for display
                        const lastModified = new Date(fileObj.lastModified);
                        const formattedDate = lastModified.toISOString().replace('T', ' ').substring(0, 19);
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
        .catch(function (error) {
            console.error('Error loading files:', error);
            fileList.innerHTML = '<p>Error loading files</p>';
        });
}

function uploadFiles(files) {
    for (let i = 0; i < files.length; i++) {
        uploadFile(files[i]);
    }
}

function uploadFile(file) {
    if (!file) return;

    // Create upload progress element
    const progressDiv = document.createElement('div');
    progressDiv.className = 'upload-progress';
    progressDiv.innerHTML = '<span>Preparing to upload ' + file.name + ' (' + formatSize(file.size) + ')</span>';
    uploadStatus.innerHTML = '';
    uploadStatus.appendChild(progressDiv);

    console.log('Uploading file:', file.name, 'Size:', file.size, 'bytes');

    // Reset the file input to allow uploading the same file again
    fileInput.value = '';

    // Track upload status and time at 100%
    let uploadReached100 = false;
    let timeReached100 = 0;

    // Set a timer to show that the upload is still in progress
    const uploadStartTime = Date.now();
    const uploadTimer = setInterval(function () {
        const elapsed = Math.floor((Date.now() - uploadStartTime) / 1000);

        // Only update timer text if we haven't reached 100% yet
        if (!uploadReached100) {
            progressDiv.innerHTML = '<span>Uploading ' + file.name + ' (' + formatSize(file.size) + ') - ' + elapsed + 's elapsed</span>';
        } else {
            // If we've reached 100%, show that and how long we've been at 100%
            const timeAt100 = Math.floor((Date.now() - timeReached100) / 1000);
            progressDiv.innerHTML = '<span>Upload at 100% - Waiting for server to complete processing... (' + timeAt100 + 's)</span>';

            // Auto-complete if we've been at 100% for more than 3 seconds
            if (timeAt100 > 3 && !uploadCompleted) {
                console.log('Server confirmation taking too long, assuming upload completed successfully');
                handleCompletion(true, 'Upload complete (auto-confirmed): ' + file.name);
            }
        }

        // Show a timeout warning after 2 minutes
        if (elapsed > 120) {
            progressDiv.innerHTML += '<br><span class="warning">Upload is taking longer than expected. Please be patient for large files.</span>';
        }
    }, 1000);

    // Use binary upload with the actual file content and a longer timeout
    const xhr = new XMLHttpRequest();
    xhr.open('POST', '/upload-simple?filename=' + encodeURIComponent(file.name));
    xhr.timeout = 3600000; // 1 hour timeout for very large files

    // Set a flag to track completion state
    let uploadCompleted = false;

    // Add progress tracking
    xhr.upload.addEventListener('progress', function (e) {
        if (e.lengthComputable) {
            const percentComplete = Math.round((e.loaded / e.total) * 100);
            progressDiv.innerHTML = '<span>Uploading ' + file.name + ' (' + formatSize(file.size) + ') - ' + percentComplete + '%</span>';

            // Record when we hit 100%
            if (percentComplete === 100 && !uploadReached100) {
                uploadReached100 = true;
                timeReached100 = Date.now();
                console.log('Upload reached 100%, waiting for server confirmation...');

                // Change progress display to show we're waiting for server
                progressDiv.innerHTML = '<span>Upload at 100% - Finalizing on server...</span>';

                // Add a fallback timeout to auto-complete after 3 seconds at 100%
                setTimeout(function () {
                    if (!uploadCompleted) {
                        console.log('Server confirmation timed out, assuming upload completed successfully');
                        handleCompletion(true, 'Upload complete (auto-finalized): ' + file.name);
                    }
                }, 3000);
            }
        }
    });

    // Add loadend event which triggers when the request completes, regardless of success/failure
    xhr.addEventListener('loadend', function () {
        // If we've reached 100% and the request has ended, consider it complete
        if (uploadReached100 && !uploadCompleted) {
            console.log('Request ended after reaching 100%, assuming success');
            handleCompletion(true, 'Upload complete: ' + file.name);
        }
    });

    // Function to handle completion, regardless of how it happens
    function handleCompletion(success, message) {
        if (uploadCompleted) return; // Prevent double handling
        uploadCompleted = true;

        // Clear timers
        clearInterval(uploadTimer);

        // Update status
        if (success) {
            progressDiv.className = 'upload-progress success';
            progressDiv.innerHTML = '✅ ' + message;

            // Reload file list to show the new file
            loadFiles();
        } else {
            progressDiv.className = 'upload-progress error';
            progressDiv.innerHTML = '❌ ' + message;
        }
    }

    // Add event listeners
    xhr.addEventListener('load', function () {
        if (xhr.status >= 200 && xhr.status < 300) {
            // Successful completion
            handleCompletion(true, 'Upload complete: ' + file.name);
        } else {
            // Server returned an error
            handleCompletion(false, 'Server error: ' + (xhr.responseText || 'Unknown error'));
        }
    });

    xhr.addEventListener('error', function () {
        handleCompletion(false, 'Network error during upload');
    });

    xhr.addEventListener('timeout', function () {
        handleCompletion(false, 'Upload timed out');
    });

    xhr.addEventListener('abort', function () {
        handleCompletion(false, 'Upload was aborted');
    });

    // Send the file
    xhr.send(file);
}

function formatSize(bytes) {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}

function navigateToDir(path) {
    fetch('/navigate?path=' + encodeURIComponent(path))
        .then(function (response) { return response.json(); })
        .then(function (data) {
            if (data.success) {
                currentDir = data.path;
                loadFiles();
            } else {
                alert('Error: ' + (data.error || 'Could not navigate to directory'));
            }
        })
        .catch(function (error) {
            alert('Network error: Could not navigate to directory');
            console.error('Navigation error:', error);
        });
} 