package com.smart.gallery.cleaner.organizer.app.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.smart.gallery.cleaner.organizer.app.data.model.ScanResult
import com.smart.gallery.cleaner.organizer.app.data.model.ScanState
import com.smart.gallery.cleaner.organizer.app.data.model.StorageInfo
import com.smart.gallery.cleaner.organizer.app.data.repository.MediaRepository
import com.smart.gallery.cleaner.organizer.app.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val mediaRepo    = MediaRepository(application)
    private val settingsRepo = SettingsRepository(application)

    private val _storageInfo = MutableLiveData<StorageInfo>()
    val storageInfo: LiveData<StorageInfo> = _storageInfo

    private val _scanState = MutableLiveData<ScanState>(ScanState.Idle)
    val scanState: LiveData<ScanState> = _scanState

    private val _scanResult = MutableLiveData<ScanResult?>()
    val scanResult: LiveData<ScanResult?> = _scanResult

    private val _trashedCount = MutableLiveData(0)
    val trashedCount: LiveData<Int> = _trashedCount

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun loadStorageInfo() {
        viewModelScope.launch {
            try {
                val info = mediaRepo.getStorageInfo()
                _storageInfo.value = info
            } catch (e: Exception) {
                Log.e("HomeViewModel", "loadStorageInfo error: ${e.message}", e)
                _error.value = "Could not read storage: ${e.message}"
            }
        }
    }

    fun startScan() {
        if (_scanState.value is ScanState.Scanning) return

        viewModelScope.launch {
            try {
                postScanState(ScanState.Scanning(0, "Loading photos…"))
                val images = mediaRepo.getAllImages()

                postScanState(ScanState.Scanning(15, "Loading videos…"))
                val videos = mediaRepo.getAllVideos()

                postScanState(ScanState.Scanning(30, "Finding large files…"))
                val largeFiles = mediaRepo.getLargeFiles()

                postScanState(ScanState.Scanning(45, "Scanning screenshots…"))
                val screenshots = mediaRepo.getScreenshots()

                postScanState(ScanState.Scanning(55, "Scanning downloads…"))
                val downloads = mediaRepo.getDownloadedMedia()

                postScanState(ScanState.Scanning(65, "Scanning WhatsApp media…"))
                val whatsapp = mediaRepo.getWhatsAppImages() + mediaRepo.getWhatsAppVideos()

                postScanState(ScanState.Scanning(75, "Finding duplicates…"))
                var duplicateCount = 0
                var duplicateSize  = 0L
                try {
                    mediaRepo.findDuplicates(images).collect { groups ->
                        duplicateCount = groups.sumOf { it.files.size - 1 }
                        duplicateSize  = groups.sumOf { it.wastedSize }
                    }
                } catch (e: Exception) {
                    Log.e("HomeViewModel", "findDuplicates error: ${e.message}", e)
                }

                postScanState(ScanState.Scanning(85, "Finding similar photos…"))
                var similarCount = 0
                var similarSize  = 0L
                try {
                    mediaRepo.findSimilarPhotos(images).collect { groups ->
                        similarCount = groups.sumOf { it.files.size - 1 }
                        similarSize  = groups.sumOf { it.wastedSize }
                    }
                } catch (e: Exception) {
                    Log.e("HomeViewModel", "findSimilarPhotos error: ${e.message}", e)
                }

                postScanState(ScanState.Scanning(92, "Detecting blurry photos…"))
                val blurry = mediaRepo.findBlurryPhotos(images)

                postScanState(ScanState.Scanning(98, "Finalizing scan…"))

                val result = ScanResult(
                    totalPhotos      = images.size,
                    totalVideos      = videos.size,
                    largeFilesCount  = largeFiles.size,
                    largeFilesSize   = largeFiles.sumOf { it.size },
                    duplicatesCount  = duplicateCount,
                    duplicatesSize   = duplicateSize,
                    similarCount     = similarCount,
                    similarSize      = similarSize,
                    blurryCount      = blurry.size,
                    blurrySize       = blurry.sumOf { it.size },
                    screenshotsCount = screenshots.size,
                    screenshotsSize  = screenshots.sumOf { it.size },
                    videosSize       = videos.sumOf { it.size },
                    downloadsCount   = downloads.size,
                    downloadsSize    = downloads.sumOf { it.size },
                    whatsappCount    = whatsapp.size,
                    whatsappSize     = whatsapp.sumOf { it.size }
                )

                settingsRepo.setLastScanTime(System.currentTimeMillis())

                val updatedStorage = mediaRepo.getStorageInfo()
                withContext(Dispatchers.Main) {
                    _storageInfo.value  = updatedStorage
                    _scanResult.value   = result
                    _scanState.value    = ScanState.Complete(result)
                }

            } catch (e: Exception) {
                Log.e("HomeViewModel", "startScan error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _scanState.value = ScanState.Error(e.message ?: "Scan failed")
                }
            }
        }
    }

    fun loadTrashedCount() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return
        viewModelScope.launch {
            try {
                val count = mediaRepo.getTrashedMedia().size
                _trashedCount.value = count
            } catch (_: Exception) { }
        }
    }

    private suspend fun postScanState(state: ScanState) {
        withContext(Dispatchers.Main) { _scanState.value = state }
    }
}