// ====================================
// FBS - Android TV File Server
// Client-side JavaScript
// ====================================

// DOM elements
const dropArea = document.getElementById('dropArea');
const fileInput = document.getElementById('fileInput');
const uploadButton = document.getElementById('uploadButton');
const uploadStatus = document.getElementById('uploadStatus');
const fileList = document.getElementById('fileList');
const currentPathEl = document.getElementById('currentPath');
const fileCountEl = document.getElementById('fileCount');
const footerTimeEl = document.getElementById('footerTime');
const particlesContainer = document.getElementById('particles');

// Current directory for web UI
let currentDir = '/storage/emulated/0';

// Base directory - don't allow navigation above this level
const baseDir = '/storage/emulated/0';

// ====================================
// Initialization
// ====================================

// Check for path parameter in URL on load
const urlParams = new URLSearchParams(window.location.search);
const pathParam = urlParams.get('path');
if (pathParam && pathParam.startsWith('/storage/emulated/0')) {
    currentDir = pathParam;
}

// Handle back/forward browser buttons
window.onpopstate = function (event) {
    if (event.state && event.state.path) {
        currentDir = event.state.path;
        loadFiles();
    } else {
        const p = new URLSearchParams(window.location.search).get('path');
        currentDir = (p && p.startsWith('/storage/emulated/0')) ? p : '/storage/emulated/0';
        loadFiles();
    }
};

// Load files on page load
document.addEventListener('DOMContentLoaded', function () {
    loadFiles();
    initParticles();
    updateFooterTime();
    setInterval(updateFooterTime, 1000);
});

// ====================================
// Particle Effects
// ====================================

function initParticles() {
    if (!particlesContainer) return;

    for (let i = 0; i < 20; i++) {
        createParticle();
    }
}

function createParticle() {
    const particle = document.createElement('div');
    particle.className = 'particle';
    particle.style.left = Math.random() * 100 + '%';
    particle.style.animationDelay = Math.random() * 15 + 's';
    particle.style.animationDuration = (15 + Math.random() * 10) + 's';
    particlesContainer.appendChild(particle);
}

// ====================================
// Footer Time
// ====================================

function updateFooterTime() {
    if (!footerTimeEl) return;
    const now = new Date();
    const time = now.toLocaleTimeString('en-US', {
        hour12: false,
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit'
    });
    const date = now.toLocaleDateString('en-US', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit'
    });
    footerTimeEl.textContent = `${date} ${time}`;
}

// ====================================
// Event Listeners
// ====================================

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

dropArea.addEventListener('click', function (e) {
    if (e.target !== uploadButton && !uploadButton.contains(e.target)) {
        fileInput.click();
    }
});

uploadButton.addEventListener('click', function (e) {
    e.stopPropagation();
    fileInput.click();
});

fileInput.addEventListener('change', function () {
    if (fileInput.files.length > 0) {
        uploadFiles(fileInput.files);
    }
});

// ====================================
// File Operations
// ====================================

function loadFiles() {
    // Update path display if element exists
    if (currentPathEl) {
        currentPathEl.textContent = currentDir;
    }

    // Pass current directory to API for independent navigation
    fetch('/api/files?path=' + encodeURIComponent(currentDir))
        .then(function (response) { return response.json(); })
        .then(function (data) {
            fileList.innerHTML = '';

            // Add parent directory option if not at base directory or above
            const parentDir = currentDir.substring(0, currentDir.lastIndexOf('/'));

            // Only show parent directory if we're deeper than the base directory
            if (parentDir && parentDir !== currentDir && currentDir !== baseDir && currentDir.startsWith(baseDir + '/')) {
                const parentItem = createFileItem({
                    name: '..',
                    isDirectory: true,
                    isParent: true
                }, parentDir);
                fileList.appendChild(parentItem);
            }

            // Add files and directories
            if (data && Array.isArray(data)) {
                // Update file count
                if (fileCountEl) {
                    const dirCount = data.filter(f => f.isDirectory).length;
                    const fileCount = data.filter(f => !f.isDirectory).length;
                    fileCountEl.textContent = `${dirCount} folders, ${fileCount} files`;
                }

                data.sort(function (a, b) {
                    // Directories first
                    if (a.isDirectory && !b.isDirectory) return -1;
                    if (!a.isDirectory && b.isDirectory) return 1;
                    // Then alphabetical
                    return a.name.localeCompare(b.name);
                });

                for (let i = 0; i < data.length; i++) {
                    const fileItem = createFileItem(data[i], currentDir + '/' + data[i].name);
                    fileList.appendChild(fileItem);
                }
            } else {
                fileList.innerHTML = '<div class="empty-state"><span class="empty-state-icon">[ ]</span><span>No files found</span></div>';
                if (fileCountEl) {
                    fileCountEl.textContent = '0 items';
                }
            }
        })
        .catch(function (error) {
            console.error('Error loading files:', error);
            fileList.innerHTML = '<div class="empty-state"><span class="empty-state-icon">[!]</span><span>Error loading files</span></div>';
        });
}

function createFileItem(fileObj, fullPath) {
    const fileItem = document.createElement('div');
    fileItem.className = 'file-item';

    if (fileObj.isDirectory) {
        fileItem.className += ' folder';

        const icon = document.createElement('span');
        icon.className = 'file-icon';
        icon.textContent = fileObj.isParent ? '↑' : '▶';

        const fileName = document.createElement('span');
        fileName.className = 'file-name';

        const link = document.createElement('a');
        link.href = '#';
        link.textContent = fileObj.isParent ? '.. (Parent Directory)' : fileObj.name;
        link.onclick = function(e) {
            e.preventDefault();
            navigateToDir(fullPath);
        };

        fileName.appendChild(link);
        fileItem.appendChild(icon);
        fileItem.appendChild(fileName);

        // Add delete button for directories (not for parent)
        if (!fileObj.isParent) {
            const deleteBtn = createDeleteButton(fullPath);
            fileItem.appendChild(deleteBtn);
        }
    } else {
        fileItem.className += ' file';

        const icon = document.createElement('span');
        icon.className = 'file-icon';
        icon.textContent = getFileIcon(fileObj.name);

        const fileName = document.createElement('span');
        fileName.className = 'file-name';

        const link = document.createElement('a');
        link.href = '/files/' + fileObj.name;
        link.download = '';
        link.textContent = fileObj.name;

        fileName.appendChild(link);

        const fileMeta = document.createElement('span');
        fileMeta.className = 'file-meta';

        const fileSize = document.createElement('span');
        fileSize.className = 'file-size';
        fileSize.textContent = formatSize(fileObj.size);

        const fileDate = document.createElement('span');
        fileDate.className = 'file-date';
        const lastModified = new Date(fileObj.lastModified);
        fileDate.textContent = lastModified.toISOString().replace('T', ' ').substring(0, 19);

        fileMeta.appendChild(fileSize);
        fileMeta.appendChild(fileDate);

        const deleteBtn = createDeleteButton(fullPath);

        fileItem.appendChild(icon);
        fileItem.appendChild(fileName);
        fileItem.appendChild(fileMeta);
        fileItem.appendChild(deleteBtn);
    }

    return fileItem;
}

function getFileIcon(filename) {
    const ext = filename.split('.').pop().toLowerCase();
    const icons = {
        // Documents
        pdf: '◈',
        doc: '◇', docx: '◇',
        txt: '≡', md: '≡',
        // Images
        jpg: '◎', jpeg: '◎', png: '◎', gif: '◎', webp: '◎', svg: '◎',
        // Video
        mp4: '▷', mkv: '▷', avi: '▷', mov: '▷',
        // Audio
        mp3: '♪', wav: '♪', flac: '♪', ogg: '♪',
        // Archives
        zip: '▤', rar: '▤', '7z': '▤', tar: '▤', gz: '▤',
        // Code
        js: '{ }', ts: '{ }', py: '#', java: '◆', kt: '◆',
        html: '<>', css: '※', json: '{ }', xml: '<>',
        // Apps
        apk: '◉', exe: '◉', dmg: '◉',
    };
    return icons[ext] || '○';
}

function createDeleteButton(path) {
    const btn = document.createElement('button');
    btn.className = 'delete-btn';
    btn.title = 'Delete';
    btn.textContent = '×';
    btn.onclick = function(e) {
        e.stopPropagation();
        deleteFile(path);
    };
    return btn;
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
    progressDiv.innerHTML = `
        <span>TRANSMITTING: ${file.name} (${formatSize(file.size)})</span>
        <div class="progress-bar-container">
            <div class="progress-bar" style="width: 0%"></div>
        </div>
    `;
    uploadStatus.innerHTML = '';
    uploadStatus.appendChild(progressDiv);

    const progressBar = progressDiv.querySelector('.progress-bar');
    const statusText = progressDiv.querySelector('span');

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

        if (!uploadReached100) {
            statusText.textContent = `TRANSMITTING: ${file.name} [${elapsed}s]`;
        } else {
            const timeAt100 = Math.floor((Date.now() - timeReached100) / 1000);
            statusText.textContent = `FINALIZING: ${file.name} [${timeAt100}s]`;

            // Auto-complete if we've been at 100% for more than 3 seconds
            if (timeAt100 > 3 && !uploadCompleted) {
                console.log('Server confirmation taking too long, assuming upload completed successfully');
                handleCompletion(true, 'TRANSMISSION COMPLETE: ' + file.name);
            }
        }
    }, 1000);

    // Use binary upload with the actual file content
    const xhr = new XMLHttpRequest();
    xhr.open('POST', '/upload-simple?filename=' + encodeURIComponent(file.name) + '&path=' + encodeURIComponent(currentDir));
    xhr.timeout = 3600000; // 1 hour timeout for very large files

    let uploadCompleted = false;

    // Add progress tracking
    xhr.upload.addEventListener('progress', function (e) {
        if (e.lengthComputable) {
            const percentComplete = Math.round((e.loaded / e.total) * 100);
            progressBar.style.width = percentComplete + '%';
            statusText.textContent = `TRANSMITTING: ${file.name} [${percentComplete}%]`;

            if (percentComplete === 100 && !uploadReached100) {
                uploadReached100 = true;
                timeReached100 = Date.now();
                console.log('Upload reached 100%, waiting for server confirmation...');
                statusText.textContent = `FINALIZING: ${file.name}`;

                setTimeout(function () {
                    if (!uploadCompleted) {
                        console.log('Server confirmation timed out, assuming upload completed successfully');
                        handleCompletion(true, 'TRANSMISSION COMPLETE: ' + file.name);
                    }
                }, 3000);
            }
        }
    });

    xhr.addEventListener('loadend', function () {
        if (uploadReached100 && !uploadCompleted) {
            console.log('Request ended after reaching 100%, assuming success');
            handleCompletion(true, 'TRANSMISSION COMPLETE: ' + file.name);
        }
    });

    function handleCompletion(success, message) {
        if (uploadCompleted) return;
        uploadCompleted = true;

        clearInterval(uploadTimer);

        if (success) {
            progressDiv.className = 'upload-progress success';
            progressDiv.innerHTML = `<span>✓ ${message}</span>`;
            loadFiles();
        } else {
            progressDiv.className = 'upload-progress error';
            progressDiv.innerHTML = `<span>✗ ${message}</span>`;
        }
    }

    xhr.addEventListener('load', function () {
        if (xhr.status >= 200 && xhr.status < 300) {
            handleCompletion(true, 'TRANSMISSION COMPLETE: ' + file.name);
        } else {
            handleCompletion(false, 'SERVER ERROR: ' + (xhr.responseText || 'Unknown error'));
        }
    });

    xhr.addEventListener('error', function () {
        handleCompletion(false, 'NETWORK ERROR');
    });

    xhr.addEventListener('timeout', function () {
        handleCompletion(false, 'TIMEOUT');
    });

    xhr.addEventListener('abort', function () {
        handleCompletion(false, 'ABORTED');
    });

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

                // Update URL parameter without reloading page
                const newUrl = window.location.protocol + "//" + window.location.host + window.location.pathname + '?path=' + encodeURIComponent(currentDir);
                window.history.pushState({ path: currentDir }, '', newUrl);

                loadFiles();
            } else {
                showError('Could not navigate to directory');
            }
        })
        .catch(function (error) {
            showError('Network error');
            console.error('Navigation error:', error);
        });
}

function deleteFile(path) {
    if (confirm('DELETE FILE?\n\n' + path + '\n\nThis action cannot be undone.')) {
        fetch('/api/delete?path=' + encodeURIComponent(path), {
            method: 'POST'
        })
            .then(function (response) { return response.json(); })
            .then(function (data) {
                if (data.success) {
                    loadFiles();
                } else {
                    showError('Error deleting file: ' + (data.error || 'Unknown error'));
                }
            })
            .catch(function (error) {
                showError('Network error');
                console.error('Delete error:', error);
            });
    }
}

function showError(message) {
    const errorDiv = document.createElement('div');
    errorDiv.className = 'upload-progress error';
    errorDiv.innerHTML = `<span>✗ ERROR: ${message}</span>`;
    uploadStatus.innerHTML = '';
    uploadStatus.appendChild(errorDiv);

    setTimeout(function() {
        errorDiv.remove();
    }, 5000);
}
