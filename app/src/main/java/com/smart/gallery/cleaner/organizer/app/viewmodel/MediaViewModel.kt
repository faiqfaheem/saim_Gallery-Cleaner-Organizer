package com.smart.gallery.cleaner.organizer.app.viewmodel

import android.app.Application
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.IntentSender
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.smart.gallery.cleaner.organizer.app.ai.AiPhotoAnalyzer
import com.smart.gallery.cleaner.organizer.app.data.model.AiCategoryBucket
import com.smart.gallery.cleaner.organizer.app.data.model.AiCleanReport
import com.smart.gallery.cleaner.organizer.app.data.model.AlbumInfo
import com.smart.gallery.cleaner.organizer.app.data.model.DuplicateGroup
import com.smart.gallery.cleaner.organizer.app.data.model.MediaFile
import com.smart.gallery.cleaner.organizer.app.data.model.ScanProgress
import com.smart.gallery.cleaner.organizer.app.data.model.SimilarGroup
import com.smart.gallery.cleaner.organizer.app.data.repository.MediaRepository
import com.smart.gallery.cleaner.organizer.app.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class SortOrder { NEWEST, OLDEST, LARGEST, SMALLEST, NAME }

class PendingIntentException(val intentSender: IntentSender) :
    Exception("User consent required for file deletion")

class MediaViewModel(application: Application) : AndroidViewModel(application) {

    private val contentResolver get() = getApplication<Application>().contentResolver
    private val repo = MediaRepository(getApplication())
    private val settings = SettingsRepository(getApplication())

    private val _files = MutableLiveData<List<MediaFile>>(emptyList())
    val files: LiveData<List<MediaFile>> = _files

    private val _duplicateGroups = MutableLiveData<List<DuplicateGroup>>(emptyList())
    val duplicateGroups: LiveData<List<DuplicateGroup>> = _duplicateGroups

    private val _similarGroups = MutableLiveData<List<SimilarGroup>>(emptyList())
    val similarGroups: LiveData<List<SimilarGroup>> = _similarGroups

    private val _aiCategories = MutableLiveData<List<AiCategoryBucket>>(emptyList())
    val aiCategories: LiveData<List<AiCategoryBucket>> = _aiCategories

    private val _aiCleanReport = MutableLiveData<AiCleanReport?>()
    val aiCleanReport: LiveData<AiCleanReport?> = _aiCleanReport

    private val _aiScanProgress = MutableLiveData(0)
    val aiScanProgress: LiveData<Int> = _aiScanProgress

    private val _scanProgress = MutableLiveData(ScanProgress())
    val scanProgress: LiveData<ScanProgress> = _scanProgress

    private val _emptyAlbums = MutableLiveData<List<AlbumInfo>>(emptyList())
    val emptyAlbums: LiveData<List<AlbumInfo>> = _emptyAlbums

    private val _albumsLoading = MutableLiveData(false)
    val albumsLoading: LiveData<Boolean> = _albumsLoading

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _deleteResult = MutableLiveData<Result<Long>?>()
    val deleteResult: LiveData<Result<Long>?> = _deleteResult

    private val _selectedCount = MutableLiveData(0)
    val selectedCount: LiveData<Int> = _selectedCount

    private val _totalSelectedSize = MutableLiveData(0L)
    val totalSelectedSize: LiveData<Long> = _totalSelectedSize

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _confirmDeleteEvent = MutableLiveData<List<MediaFile>?>()
    val confirmDeleteEvent: LiveData<List<MediaFile>?> = _confirmDeleteEvent

    private val _previewBeforeDeleteEvent = MutableLiveData<List<MediaFile>?>()
    val previewBeforeDeleteEvent: LiveData<List<MediaFile>?> = _previewBeforeDeleteEvent

    private var currentSort = SortOrder.NEWEST

    // Rotation-surviving state flags so rotating the device never wipes scans or UI state
    var hasLoadedFiles: Boolean = false
        private set
    var hasLoadedDuplicates: Boolean = false
        private set
    var hasLoadedSimilar: Boolean = false
        private set
    var hasLoadedAiCategories: Boolean = false
        private set
    var hasLoadedAiReport: Boolean = false
        private set
    var activeAiCategory: com.smart.gallery.cleaner.organizer.app.data.model.AiCategory? = null
    var activeWhatsAppTab: Int = 0
    var activeSearchChipId: Int = -1
    var isAiSearchEnriched: Boolean = false

    @Volatile private var allFiles = listOf<MediaFile>()
    @Volatile private var pendingDeleteFiles = listOf<MediaFile>()

    fun loadLargeFiles() = loadFiles {
        val threshold = settings.largeFileThreshold.first()
        repo.getLargeFiles(threshold)
    }

    fun loadScreenshots() = loadFiles { repo.getScreenshots() }
    fun loadVideos() = loadFiles { repo.getAllVideos() }
    fun loadDownloads() = loadFiles { repo.getDownloadedMedia() }
    fun loadWhatsAppImages() = loadFiles { repo.getWhatsAppImages() }
    fun loadWhatsAppVideos() = loadFiles { repo.getWhatsAppVideos() }
    fun loadAllImages() = loadFiles { repo.getAllImages() }
    fun loadAllMedia() = loadFiles { repo.getAllMedia() }
    fun loadTrashedMedia() = loadFiles { repo.getTrashedMedia() }
    fun loadRecentlyAdded(daysBack: Int) = loadFiles { repo.getRecentlyAdded(daysBack) }

    fun loadBlurryPhotos() {
        _loading.value = true
        _scanProgress.value = ScanProgress()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val images = repo.getAllImages()
                val blurry = repo.findBlurryPhotos(images) { done, total ->
                    val p = ScanProgress(done, total)
                    _scanProgress.postValue(p)
                    _aiScanProgress.postValue(p.percent)
                }
                allFiles = blurry
                hasLoadedFiles = true
                val sorted = sort(blurry)
                _files.postValue(sorted)
                postCounts(sorted)
            } catch (e: Exception) {
                _error.postValue(e.localizedMessage ?: "Failed to find blurry photos")
            } finally {
                _loading.postValue(false)
            }
        }
    }

    fun loadDarkBadPhotos() {
        _loading.value = true
        _scanProgress.value = ScanProgress()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val images = repo.getAllImages()
                val darkPhotos = repo.findDarkOrBadLightingPhotos(images) { done, total ->
                    val p = ScanProgress(done, total)
                    _scanProgress.postValue(p)
                    _aiScanProgress.postValue(p.percent)
                }
                allFiles = darkPhotos
                hasLoadedFiles = true
                val sorted = sort(darkPhotos)
                _files.postValue(sorted)
                postCounts(sorted)
            } catch (e: Exception) {
                _error.postValue(e.localizedMessage ?: "Failed to analyze lighting")
            } finally {
                _loading.postValue(false)
            }
        }
    }

    fun loadDuplicates() {
        _loading.value = true
        _scanProgress.value = ScanProgress()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val images = repo.getAllImages()
                repo.findDuplicates(images) { done, total ->
                    val p = ScanProgress(done, total)
                    _scanProgress.postValue(p)
                    _aiScanProgress.postValue(p.percent)
                }.collect { groups ->
                    hasLoadedDuplicates = true
                    _duplicateGroups.postValue(groups)
                }
            } catch (e: Exception) {
                _error.postValue(e.localizedMessage ?: "Failed to find duplicates")
            } finally {
                _loading.postValue(false)
            }
        }
    }

    fun setDuplicateGroupKeeper(groupId: String, fileId: Long) {
        val current = _duplicateGroups.value.orEmpty()
        val updated = current.map { group ->
            if (group.groupId == groupId) {
                val keeperSize = group.files.firstOrNull { it.id == fileId }?.size ?: 0L
                group.copy(
                    keepFileId = fileId,
                    wastedSize = (group.totalSize - keeperSize).coerceAtLeast(0L)
                )
            } else group
        }
        _duplicateGroups.postValue(updated)
    }

    fun loadSimilarPhotos() {
        _loading.value = true
        _scanProgress.value = ScanProgress()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val images = repo.getAllImages()
                repo.findSimilarPhotos(images) { done, total ->
                    val p = ScanProgress(done, total)
                    _scanProgress.postValue(p)
                    _aiScanProgress.postValue(p.percent)
                }.collect { groups ->
                    hasLoadedSimilar = true
                    _similarGroups.postValue(groups)
                }
            } catch (e: Exception) {
                _error.postValue(e.localizedMessage ?: "Failed to find similar photos")
            } finally {
                _loading.postValue(false)
            }
        }
    }

    fun setSimilarGroupKeeper(groupId: String, fileId: Long) {
        val current = _similarGroups.value.orEmpty()
        val updated = current.map { group ->
            if (group.groupId == groupId) {
                val keeperSize = group.files.firstOrNull { it.id == fileId }?.size ?: 0L
                group.copy(
                    keepFileId = fileId,
                    wastedSize = (group.totalSize - keeperSize).coerceAtLeast(0L)
                )
            } else group
        }
        _similarGroups.postValue(updated)
    }

    fun loadAiSmartCategories() {
        _loading.value = true
        _aiScanProgress.value = 0
        _scanProgress.value = ScanProgress()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val buckets = repo.getAiSmartCategories { done, total ->
                    val p = ScanProgress(done, total)
                    _scanProgress.postValue(p)
                    _aiScanProgress.postValue(p.percent)
                }
                hasLoadedAiCategories = true
                _aiCategories.postValue(buckets)
            } catch (e: Exception) {
                _error.postValue(e.localizedMessage ?: "AI categorization failed")
            } finally {
                _loading.postValue(false)
            }
        }
    }

    fun loadAiSmartCleanReport() {
        _loading.value = true
        _aiScanProgress.value = 0
        _scanProgress.value = ScanProgress()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val report = repo.buildAiSmartCleanReport { done, total ->
                    val p = ScanProgress(done, total)
                    _scanProgress.postValue(p)
                    _aiScanProgress.postValue(p.percent)
                }
                hasLoadedAiReport = true
                _aiCleanReport.postValue(report)
            } catch (e: Exception) {
                _error.postValue(e.localizedMessage ?: "AI scan failed")
            } finally {
                _loading.postValue(false)
            }
        }
    }

    fun setDirectFiles(list: List<MediaFile>) {
        allFiles = list
        hasLoadedFiles = true
        val sorted = sort(list)
        _files.postValue(sorted)
        postCounts(sorted)
    }

    fun enrichMediaWithAiTags(filesToEnrich: List<MediaFile>) {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val enriched = filesToEnrich.map { file ->
                if (file.isImage && !file.isGif && file.aiTags.isEmpty()) {
                    val analysis = AiPhotoAnalyzer.analyzePhoto(ctx, file, runFaceDetection = false)
                    if (analysis != null) AiPhotoAnalyzer.applyAnalysisToFile(file, analysis) else file
                } else file
            }
            allFiles = enriched
            _files.postValue(sort(enriched))
        }
    }

    private fun loadFiles(loader: suspend () -> List<MediaFile>) {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.postValue(true)
            try {
                val result = loader()
                allFiles = result
                hasLoadedFiles = true
                val sorted = sort(result)
                _files.postValue(sorted)
                postCounts(sorted)
            } catch (e: Exception) {
                _error.postValue(e.localizedMessage ?: "Failed to load media")
            } finally {
                _loading.postValue(false)
            }
        }
    }

    private fun sort(list: List<MediaFile>): List<MediaFile> = when (currentSort) {
        SortOrder.NEWEST -> list.sortedByDescending { it.dateAdded }
        SortOrder.OLDEST -> list.sortedBy { it.dateAdded }
        SortOrder.LARGEST -> list.sortedByDescending { it.size }
        SortOrder.SMALLEST -> list.sortedBy { it.size }
        SortOrder.NAME -> list.sortedBy { it.name.lowercase() }
    }

    fun setSortOrder(order: SortOrder) {
        currentSort = order
        viewModelScope.launch(Dispatchers.Default) { _files.postValue(sort(allFiles)) }
    }

    fun toggleSelection(file: MediaFile) {
        viewModelScope.launch(Dispatchers.Default) {
            val updated = allFiles.map {
                if (it.id == file.id) it.copy(isSelected = !it.isSelected) else it
            }
            allFiles = updated
            _files.postValue(sort(updated))
            postCounts(updated)
        }
    }

    fun selectAll() {
        viewModelScope.launch(Dispatchers.Default) {
            val updated = allFiles.map { it.copy(isSelected = true) }
            allFiles = updated
            _files.postValue(sort(updated))
            postCounts(updated)
        }
    }

    fun deselectAll() {
        viewModelScope.launch(Dispatchers.Default) {
            val updated = allFiles.map { it.copy(isSelected = false) }
            allFiles = updated
            _files.postValue(sort(updated))
            postCounts(updated)
        }
    }

    private fun postCounts(list: List<MediaFile>) {
        val selected = list.filter { it.isSelected }
        _selectedCount.postValue(selected.size)
        _totalSelectedSize.postValue(selected.sumOf { it.size })
    }

    fun getSelectedFiles(): List<MediaFile> = allFiles.filter { it.isSelected }

    fun deleteSelected() {
        val selected = getSelectedFiles()
        if (selected.isEmpty()) return
        deleteFiles(selected)
    }

    fun deleteFile(file: MediaFile) = deleteFiles(listOf(file))

    fun deleteFiles(filesToDelete: List<MediaFile>) {
        if (filesToDelete.isEmpty()) return
        viewModelScope.launch {
            val preview = settings.previewBeforeDelete.first()
            if (preview) {
                _previewBeforeDeleteEvent.postValue(filesToDelete)
                return@launch
            }
            val confirm = settings.confirmDelete.first()
            if (confirm) {
                _confirmDeleteEvent.postValue(filesToDelete)
            } else {
                executeDelete(filesToDelete)
            }
        }
    }

    fun executeDeleteAfterPreview(filesToDelete: List<MediaFile>) {
        if (filesToDelete.isEmpty()) return
        viewModelScope.launch {
            val confirm = settings.confirmDelete.first()
            if (confirm) {
                _confirmDeleteEvent.postValue(filesToDelete)
            } else {
                executeDelete(filesToDelete)
            }
        }
    }

    fun executeDelete(filesToDelete: List<MediaFile>) {
        if (filesToDelete.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            _loading.postValue(true)
            try {
                val safe = settings.safeDelete.first()
                val uniqueFiles = filesToDelete.distinctBy { it.id }

                if (safe && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val uris = uniqueFiles.map { f ->
                        ContentUris.withAppendedId(
                            if (f.isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                            else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            f.id
                        )
                    }
                    val pi = MediaStore.createTrashRequest(contentResolver, uris, true)
                    pendingDeleteFiles = uniqueFiles
                    _deleteResult.postValue(Result.failure(PendingIntentException(pi.intentSender)))
                    return@launch
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val uris = uniqueFiles.map { f ->
                        ContentUris.withAppendedId(
                            if (f.isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                            else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            f.id
                        )
                    }
                    val pi = MediaStore.createDeleteRequest(contentResolver, uris)
                    pendingDeleteFiles = uniqueFiles
                    _deleteResult.postValue(Result.failure(PendingIntentException(pi.intentSender)))
                    return@launch
                }

                var freed = 0L
                var hadError = false
                for (file in uniqueFiles) {
                    val uri = ContentUris.withAppendedId(
                        if (file.isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        file.id
                    )
                    try {
                        val rows = contentResolver.delete(uri, null, null)
                        if (rows > 0) freed += file.size else hadError = true
                    } catch (se: SecurityException) {
                        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                            val rec = se as? RecoverableSecurityException
                            if (rec != null) {
                                pendingDeleteFiles = uniqueFiles
                                _deleteResult.postValue(
                                    Result.failure(
                                        PendingIntentException(rec.userAction.actionIntent.intentSender)
                                    )
                                )
                                return@launch
                            }
                        }
                        hadError = true
                    }
                }
                removeDeletedFromState(uniqueFiles)
                _deleteResult.postValue(
                    if (!hadError || freed > 0) Result.success(freed)
                    else Result.failure(Exception("Delete failed"))
                )
            } catch (e: Exception) {
                _deleteResult.postValue(Result.failure(e))
                _error.postValue(e.localizedMessage ?: "Delete error")
            } finally {
                _loading.postValue(false)
            }
        }
    }

    fun onPermissionGrantedForDelete() {
        val pending = pendingDeleteFiles
        _loading.postValue(false)
        if (pending.isEmpty()) return
        viewModelScope.launch(Dispatchers.Default) {
            val freed = pending.sumOf { it.size }
            removeDeletedFromState(pending)
            pendingDeleteFiles = emptyList()
            _deleteResult.postValue(Result.success(freed))
        }
    }

    private fun removeDeletedFromState(deleted: List<MediaFile>) {
        val ids = deleted.map { it.id }.toSet()
        val remaining = allFiles.filterNot { it.id in ids }
        allFiles = remaining
        _files.postValue(sort(remaining))
        postCounts(remaining)

        val dupGroups = _duplicateGroups.value.orEmpty()
        if (dupGroups.isNotEmpty()) {
            _duplicateGroups.postValue(
                dupGroups.mapNotNull { g ->
                    val left = g.files.filterNot { it.id in ids }
                    if (left.size > 1) {
                        val keeper = if (left.any { it.id == g.keepFileId }) g.keepFileId else left.first().id
                        val total = left.sumOf { it.size }
                        val keeperSize = left.firstOrNull { it.id == keeper }?.size ?: 0L
                        g.copy(files = left, keepFileId = keeper, totalSize = total, wastedSize = (total - keeperSize).coerceAtLeast(0L))
                    } else null
                }
            )
        }

        val simGroups = _similarGroups.value.orEmpty()
        if (simGroups.isNotEmpty()) {
            _similarGroups.postValue(
                simGroups.mapNotNull { g ->
                    val left = g.files.filterNot { it.id in ids }
                    if (left.size > 1) {
                        val keeper = if (left.any { it.id == g.keepFileId }) g.keepFileId else left.first().id
                        val total = left.sumOf { it.size }
                        val keeperSize = left.firstOrNull { it.id == keeper }?.size ?: 0L
                        g.copy(files = left, keepFileId = keeper, totalSize = total, wastedSize = (total - keeperSize).coerceAtLeast(0L))
                    } else null
                }
            )
        }

        val currentCategories = _aiCategories.value.orEmpty()
        if (currentCategories.isNotEmpty()) {
            _aiCategories.postValue(
                currentCategories.map { bucket ->
                    val left = bucket.files.filterNot { it.id in ids }
                    bucket.copy(files = left, totalSize = left.sumOf { it.size })
                }.sortedByDescending { it.files.size }
            )
        }

        val currentReport = _aiCleanReport.value
        if (currentReport != null) {
            val updatedRecs = currentReport.recommendations.mapNotNull { rec ->
                val left = rec.files.filterNot { it.id in ids }
                if (left.isNotEmpty()) rec.copy(files = left, reclaimableBytes = left.sumOf { it.size })
                else null
            }
            _aiCleanReport.postValue(
                currentReport.copy(
                    recommendations = updatedRecs,
                    totalReclaimableBytes = updatedRecs.sumOf { it.reclaimableBytes },
                    totalJunkFiles = updatedRecs.sumOf { it.files.size },
                    healthScore = if (updatedRecs.isEmpty()) 100 else currentReport.healthScore
                )
            )
        }
    }

    fun searchFiles(query: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.postValue(true)
            try {
                val results = repo.searchMedia(query)
                allFiles = results
                val sorted = sort(results)
                _files.postValue(sorted)
                postCounts(sorted)
            } catch (e: Exception) {
                _error.postValue(e.localizedMessage ?: "Search failed")
            } finally {
                _loading.postValue(false)
            }
        }
    }

    fun restoreFromTrash(filesToRestore: List<MediaFile>) {
        if (filesToRestore.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@launch
            try {
                val uris = filesToRestore.map { f ->
                    ContentUris.withAppendedId(
                        if (f.isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        f.id
                    )
                }
                val pi = MediaStore.createTrashRequest(
                    getApplication<Application>().contentResolver, uris, false
                )
                pendingDeleteFiles = filesToRestore
                _deleteResult.postValue(Result.failure(PendingIntentException(pi.intentSender)))
            } catch (e: Exception) {
                _error.postValue(e.localizedMessage ?: "Restore failed")
            }
        }
    }

    fun permanentlyDelete(filesToDelete: List<MediaFile>) {
        if (filesToDelete.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            _loading.postValue(true)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val uris = filesToDelete.map { f ->
                        ContentUris.withAppendedId(
                            if (f.isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                            else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            f.id
                        )
                    }
                    val pi = MediaStore.createDeleteRequest(
                        getApplication<Application>().contentResolver, uris
                    )
                    pendingDeleteFiles = filesToDelete
                    _deleteResult.postValue(Result.failure(PendingIntentException(pi.intentSender)))
                } else {
                    var freed = 0L
                    for (file in filesToDelete) {
                        val uri = ContentUris.withAppendedId(
                            if (file.isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                            else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            file.id
                        )
                        val rows = getApplication<Application>().contentResolver.delete(uri, null, null)
                        if (rows > 0) freed += file.size
                    }
                    removeDeletedFromState(filesToDelete)
                    _deleteResult.postValue(Result.success(freed))
                }
            } catch (e: Exception) {
                _error.postValue(e.localizedMessage ?: "Delete failed")
            } finally {
                _loading.postValue(false)
            }
        }
    }

    fun clearDeleteResult() {
        _loading.postValue(false)
        _deleteResult.postValue(null)
    }
    fun clearError() { _error.postValue(null) }
    fun clearConfirmDeleteEvent() { _confirmDeleteEvent.postValue(null) }
}
