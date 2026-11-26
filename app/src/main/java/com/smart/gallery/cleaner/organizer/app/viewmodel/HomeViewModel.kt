package com.smart.gallery.cleaner.organizer.app.viewmodel

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.smart.gallery.cleaner.organizer.app.R
import com.smart.gallery.cleaner.organizer.app.ai.AiPhotoAnalyzer
import com.smart.gallery.cleaner.organizer.app.data.model.ScanResult
import com.smart.gallery.cleaner.organizer.app.data.model.ScanState
import com.smart.gallery.cleaner.organizer.app.data.model.StorageInfo
import com.smart.gallery.cleaner.organizer.app.data.repository.MediaRepository
import com.smart.gallery.cleaner.organizer.app.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val mediaRepo = MediaRepository(application)
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

    private var hasTriggeredInitialScan = false

    private fun str(resId: Int): String = getApplication<Application>().getString(resId)

    fun loadStorageInfo() {
        viewModelScope.launch {
            try {
                val info = mediaRepo.getStorageInfo()
                _storageInfo.value = info
            } catch (e: Exception) {
                Log.e("HomeViewModel", "loadStorageInfo error: ${e.message}", e)
                _error.value = e.message
            }
        }
    }

    /**
     * Immediately populates storage info & fast MediaStore counts on first launch,
     * then automatically runs the full AI scan so Total Size & Total Files show right away.
     */
    fun loadInitialDataAndScan() {
        loadStorageInfo()
        loadTrashedCount()
        if (!hasTriggeredInitialScan || _scanResult.value == null) {
            hasTriggeredInitialScan = true
            startScan()
        }
    }

    fun startScan() {
        if (_scanState.value is ScanState.Scanning) return

        viewModelScope.launch {
            try {
                val thresholdBytes = settingsRepo.largeFileThreshold.first()

                postScanState(ScanState.Scanning(5, str(R.string.scan_step_photos), 0, 0))
                val images = mediaRepo.getAllImages()

                postScanState(ScanState.Scanning(12, str(R.string.scan_step_videos), images.size, images.size))
                val videos = mediaRepo.getAllVideos()
                val totalMedia = (images.size + videos.size).coerceAtLeast(1)

                postScanState(ScanState.Scanning(20, str(R.string.scan_step_large_files), (totalMedia * 0.20f).toInt(), totalMedia))
                val largeFiles = mediaRepo.getLargeFiles(thresholdBytes)

                postScanState(ScanState.Scanning(28, str(R.string.scan_step_screenshots), (totalMedia * 0.28f).toInt(), totalMedia))
                val screenshots = mediaRepo.getScreenshots()

                postScanState(ScanState.Scanning(34, str(R.string.scan_step_downloads), (totalMedia * 0.34f).toInt(), totalMedia))
                val downloads = mediaRepo.getDownloadedMedia()

                postScanState(ScanState.Scanning(40, str(R.string.scan_step_whatsapp), (totalMedia * 0.40f).toInt(), totalMedia))
                val whatsapp = mediaRepo.getWhatsAppImages() + mediaRepo.getWhatsAppVideos()

                // Publish quick MediaStore stats & live gallery preview thumbnails immediately!
                val quickResult = ScanResult(
                    totalPhotos = images.size,
                    totalVideos = videos.size,
                    largeFilesCount = largeFiles.size,
                    largeFilesSize = largeFiles.sumOf { it.size },
                    screenshotsCount = screenshots.size,
                    screenshotsSize = screenshots.sumOf { it.size },
                    videosSize = videos.sumOf { it.size },
                    downloadsCount = downloads.size,
                    downloadsSize = downloads.sumOf { it.size },
                    whatsappCount = whatsapp.size,
                    whatsappSize = whatsapp.sumOf { it.size },
                    recentPhotoUris = images.take(6).map { it.uri },
                    largeFilePreviewUris = largeFiles.take(3).map { it.uri },
                    screenshotPreviewUris = screenshots.take(3).map { it.uri },
                    videoPreviewUris = videos.take(3).map { it.uri },
                    downloadPreviewUris = downloads.take(3).map { it.uri },
                    whatsappPreviewUris = whatsapp.take(3).map { it.uri }
                )
                withContext(Dispatchers.Main) {
                    _scanResult.value = quickResult
                }

                val dupStepMsg = str(R.string.scan_step_duplicates)
                postScanState(ScanState.Scanning(45, dupStepMsg, (totalMedia * 0.45f).toInt(), totalMedia))
                var duplicateCount = 0
                var duplicateSize = 0L
                var duplicatePreviews = emptyList<android.net.Uri>()
                try {
                    mediaRepo.findDuplicates(images) { done, total ->
                        val frac = if (total > 0) done.toFloat() / total else 1f
                        val pct = (45 + (frac * 18f)).toInt().coerceIn(45, 63)
                        _scanState.postValue(
                            ScanState.Scanning(pct, dupStepMsg, (totalMedia * pct / 100), totalMedia)
                        )
                    }.collect { groups ->
                        duplicateCount = groups.sumOf { it.files.size - 1 }
                        duplicateSize = groups.sumOf { it.wastedSize }
                        duplicatePreviews = groups.flatMap { it.files }.take(3).map { it.uri }
                    }
                } catch (e: Exception) {
                    Log.e("HomeViewModel", "findDuplicates error: ${e.message}", e)
                }

                val simStepMsg = str(R.string.scan_step_similar)
                postScanState(ScanState.Scanning(65, simStepMsg, (totalMedia * 0.65f).toInt(), totalMedia))
                var similarCount = 0
                var similarSize = 0L
                var similarPreviews = emptyList<android.net.Uri>()
                try {
                    mediaRepo.findSimilarPhotos(images) { done, total ->
                        val frac = if (total > 0) done.toFloat() / total else 1f
                        val pct = (65 + (frac * 15f)).toInt().coerceIn(65, 80)
                        _scanState.postValue(
                            ScanState.Scanning(pct, simStepMsg, (totalMedia * pct / 100), totalMedia)
                        )
                    }.collect { groups ->
                        similarCount = groups.sumOf { it.files.size - 1 }
                        similarSize = groups.sumOf { it.wastedSize }
                        similarPreviews = groups.flatMap { it.files }.take(3).map { it.uri }
                    }
                } catch (e: Exception) {
                    Log.e("HomeViewModel", "findSimilarPhotos error: ${e.message}", e)
                }

                val blurStepMsg = str(R.string.scan_step_blurry)
                val blurry = mediaRepo.findBlurryPhotos(images) { done, total ->
                    val frac = if (total > 0) done.toFloat() / total else 1f
                    val pct = (80 + (frac * 10f)).toInt().coerceIn(80, 90)
                    _scanState.postValue(
                        ScanState.Scanning(pct, blurStepMsg, (totalMedia * pct / 100), totalMedia)
                    )
                }

                val darkStepMsg = str(R.string.scan_step_dark_photos)
                val darkBad = mediaRepo.findDarkOrBadLightingPhotos(images) { done, total ->
                    val frac = if (total > 0) done.toFloat() / total else 1f
                    val pct = (90 + (frac * 8f)).toInt().coerceIn(90, 98)
                    _scanState.postValue(
                        ScanState.Scanning(pct, darkStepMsg, (totalMedia * pct / 100), totalMedia)
                    )
                }

                postScanState(ScanState.Scanning(99, str(R.string.scan_step_finalizing), totalMedia, totalMedia))

                val thirtyDaysAgo = System.currentTimeMillis() - (30L * 86_400_000L)
                val oldScreenshots = screenshots.count { it.dateAdded < thirtyDaysAgo }

                val healthScore = AiPhotoAnalyzer.calculateHealthScore(
                    totalPhotos = images.size,
                    duplicateExtras = duplicateCount,
                    similarExtras = similarCount,
                    blurryCount = blurry.size,
                    badLightingCount = darkBad.size,
                    oldScreenshotsCount = oldScreenshots
                )

                val result = ScanResult(
                    totalPhotos = images.size,
                    totalVideos = videos.size,
                    largeFilesCount = largeFiles.size,
                    largeFilesSize = largeFiles.sumOf { it.size },
                    duplicatesCount = duplicateCount,
                    duplicatesSize = duplicateSize,
                    similarCount = similarCount,
                    similarSize = similarSize,
                    blurryCount = blurry.size,
                    blurrySize = blurry.sumOf { it.size },
                    darkBadCount = darkBad.size,
                    darkBadSize = darkBad.sumOf { it.size },
                    screenshotsCount = screenshots.size,
                    screenshotsSize = screenshots.sumOf { it.size },
                    videosSize = videos.sumOf { it.size },
                    downloadsCount = downloads.size,
                    downloadsSize = downloads.sumOf { it.size },
                    whatsappCount = whatsapp.size,
                    whatsappSize = whatsapp.sumOf { it.size },
                    healthScore = healthScore,
                    recentPhotoUris = images.take(6).map { it.uri },
                    largeFilePreviewUris = largeFiles.take(3).map { it.uri },
                    duplicatePreviewUris = duplicatePreviews,
                    similarPreviewUris = similarPreviews,
                    blurryPreviewUris = blurry.take(3).map { it.uri },
                    darkBadPreviewUris = darkBad.take(3).map { it.uri },
                    screenshotPreviewUris = screenshots.take(3).map { it.uri },
                    videoPreviewUris = videos.take(3).map { it.uri },
                    downloadPreviewUris = downloads.take(3).map { it.uri },
                    whatsappPreviewUris = whatsapp.take(3).map { it.uri }
                )

                settingsRepo.setLastScanTime(System.currentTimeMillis())

                val updatedStorage = mediaRepo.getStorageInfo()
                withContext(Dispatchers.Main) {
                    _storageInfo.value = updatedStorage
                    _scanResult.value = result
                    _scanState.value = ScanState.Complete(result)
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        viewModelScope.launch {
            try {
                val count = mediaRepo.getTrashedMedia().size
                _trashedCount.value = count
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun postScanState(state: ScanState) {
        withContext(Dispatchers.Main) { _scanState.value = state }
    }
}
