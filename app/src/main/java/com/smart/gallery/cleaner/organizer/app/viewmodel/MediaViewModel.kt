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
import com.smart.gallery.cleaner.organizer.app.data.model.AlbumInfo
import com.smart.gallery.cleaner.organizer.app.data.model.DuplicateGroup
import com.smart.gallery.cleaner.organizer.app.data.model.MediaFile
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
    private val repo     = MediaRepository(getApplication())
    private val settings = SettingsRepository(getApplication())

    // ── LiveData ──────────────────────────────────────────────────────────────

    private val _files             = MutableLiveData<List<MediaFile>>(emptyList())
    val files: LiveData<List<MediaFile>> = _files

    private val _duplicateGroups   = MutableLiveData<List<DuplicateGroup>>(emptyList())
    val duplicateGroups: LiveData<List<DuplicateGroup>> = _duplicateGroups

    private val _emptyAlbums        = MutableLiveData<List<AlbumInfo>>(emptyList())
    val emptyAlbums: LiveData<List<AlbumInfo>> = _emptyAlbums

    private val _albumsLoading = MutableLiveData(false)
    val albumsLoading: LiveData<Boolean> = _albumsLoading

    private val _loading           = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _deleteResult      = MutableLiveData<Result<Long>?>()
    val deleteResult: LiveData<Result<Long>?> = _deleteResult

    private val _selectedCount     = MutableLiveData(0)
    val selectedCount: LiveData<Int> = _selectedCount

    private val _totalSelectedSize = MutableLiveData(0L)
    val totalSelectedSize: LiveData<Long> = _totalSelectedSize

    private val _error             = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _confirmDeleteEvent = MutableLiveData<List<MediaFile>?>()
    val confirmDeleteEvent: LiveData<List<MediaFile>?> = _confirmDeleteEvent

    private val _previewBeforeDeleteEvent = MutableLiveData<List<MediaFile>?>()
    val previewBeforeDeleteEvent: LiveData<List<MediaFile>?> = _previewBeforeDeleteEvent

    private var currentSort = SortOrder.NEWEST

    @Volatile private var allFiles          = listOf<MediaFile>()
    @Volatile private var pendingDeleteFiles = listOf<MediaFile>()

    fun loadLargeFiles(thresholdBytes: Long = 5_242_880L) =
        loadFiles { repo.getLargeFiles(thresholdBytes) }
    fun loadScreenshots()    = loadFiles { repo.getScreenshots() }
    fun loadVideos()         = loadFiles { repo.getAllVideos() }
    fun loadDownloads()      = loadFiles { repo.getDownloadedMedia() }
    fun loadWhatsAppImages() = loadFiles { repo.getWhatsAppImages() }
    fun loadWhatsAppVideos() = loadFiles { repo.getWhatsAppVideos() }
    fun loadAllImages()      = loadFiles { repo.getAllImages() }
    fun loadAllMedia()       = loadFiles { repo.getAllMedia() }
    fun loadTrashedMedia()   = loadFiles { repo.getTrashedMedia() }
    fun loadRecentlyAdded(daysBack: Int) = loadFiles { repo.getRecentlyAdded(daysBack) }

    fun loadBlurryPhotos() {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.postValue(true)
            try {
                val images = repo.getAllImages()
                val blurry = repo.findBlurryPhotos(images)
                allFiles = blurry
                val sorted = sort(blurry)
                _files.postValue(sorted)
                postCounts(sorted)
            } catch (e: Exception) {
                _error.postValue(e.localizedMessage ?: "Failed to find blurry photos")
            } finally { _loading.postValue(false) }
        }
    }

    fun loadDuplicates() {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.postValue(true)
            try {
                val images = repo.getAllImages()
                repo.findDuplicates(images).collect { groups ->
                    _duplicateGroups.postValue(groups)
                }
            } catch (e: Exception) {
                _error.postValue(e.localizedMessage ?: "Failed to find duplicates")
            } finally { _loading.postValue(false) }
        }
    }

    private fun loadFiles(loader: suspend () -> List<MediaFile>) {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.postValue(true)
            try {
                val result = loader()
                allFiles   = result
                val sorted = sort(result)
                _files.postValue(sorted)
                postCounts(sorted)
            } catch (e: Exception) {
                _error.postValue(e.localizedMessage ?: "Failed to load media")
            } finally { _loading.postValue(false) }
        }
    }

    private fun sort(list: List<MediaFile>): List<MediaFile> = when (currentSort) {
        SortOrder.NEWEST   -> list.sortedByDescending { it.dateAdded }
        SortOrder.OLDEST   -> list.sortedBy { it.dateAdded }
        SortOrder.LARGEST  -> list.sortedByDescending { it.size }
        SortOrder.SMALLEST -> list.sortedBy { it.size }
        SortOrder.NAME     -> list.sortedBy { it.name.lowercase() }
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
            allFiles = updated; _files.postValue(sort(updated)); postCounts(updated)
        }
    }

    fun deselectAll() {
        viewModelScope.launch(Dispatchers.Default) {
            val updated = allFiles.map { it.copy(isSelected = false) }
            allFiles = updated; _files.postValue(sort(updated)); postCounts(updated)
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

                if (safe && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val uris = filesToDelete.map { f ->
                        ContentUris.withAppendedId(
                            if (f.isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                            else           MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            f.id
                        )
                    }
                    val pi = MediaStore.createTrashRequest(contentResolver, uris, true)
                    pendingDeleteFiles = filesToDelete
                    _deleteResult.postValue(Result.failure(PendingIntentException(pi.intentSender)))
                    return@launch
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val uris = filesToDelete.map { f ->
                        ContentUris.withAppendedId(
                            if (f.isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                            else           MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            f.id
                        )
                    }
                    val pi = MediaStore.createDeleteRequest(contentResolver, uris)
                    pendingDeleteFiles = filesToDelete
                    _deleteResult.postValue(Result.failure(PendingIntentException(pi.intentSender)))
                    return@launch
                }

                var freed    = 0L
                var hadError = false
                for (file in filesToDelete) {
                    val uri = ContentUris.withAppendedId(
                        if (file.isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        else              MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        file.id
                    )
                    try {
                        val rows = contentResolver.delete(uri, null, null)
                        if (rows > 0) freed += file.size else hadError = true
                    } catch (se: SecurityException) {
                        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                            val rec = se as? RecoverableSecurityException
                            if (rec != null) {
                                pendingDeleteFiles = filesToDelete
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
                removeDeletedFromState(filesToDelete)
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
        if (pending.isEmpty()) return
        viewModelScope.launch(Dispatchers.Default) {
            val freed = pending.sumOf { it.size }
            removeDeletedFromState(pending)
            pendingDeleteFiles = emptyList()
            _deleteResult.postValue(Result.success(freed))
        }
    }

    private fun removeDeletedFromState(deleted: List<MediaFile>) {
        val ids      = deleted.map { it.id }.toSet()
        val remaining = allFiles.filterNot { it.id in ids }
        allFiles = remaining
        _files.postValue(sort(remaining))
        postCounts(remaining)
        val groups = _duplicateGroups.value.orEmpty()
        if (groups.isNotEmpty()) {
            _duplicateGroups.postValue(
                groups
                    .map { g -> g.copy(files = g.files.filterNot { it.id in ids }) }
                    .filter { it.files.size > 1 }
            )
        }
    }

    fun searchFiles(query: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.postValue(true)
            try {
                val results = repo.searchMedia(query)
                allFiles    = results
                val sorted  = sort(results)
                _files.postValue(sorted)
                postCounts(sorted)
            } catch (e: Exception) {
                _error.postValue(e.localizedMessage ?: "Search failed")
            } finally { _loading.postValue(false) }
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
                        else           MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
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
                            else           MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
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
                            else              MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
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

    fun clearDeleteResult()           { _deleteResult.postValue(null) }
    fun clearError()                  { _error.postValue(null) }
    fun clearConfirmDeleteEvent()     { _confirmDeleteEvent.postValue(null) }
}