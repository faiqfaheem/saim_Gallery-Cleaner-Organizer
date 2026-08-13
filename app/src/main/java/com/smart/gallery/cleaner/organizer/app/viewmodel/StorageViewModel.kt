package com.smart.gallery.cleaner.organizer.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.smart.gallery.cleaner.organizer.app.data.model.StorageInfo
import com.smart.gallery.cleaner.organizer.app.data.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class StorageViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = MediaRepository(application)

    private val _storageInfo = MutableLiveData<StorageInfo>()
    val storageInfo: LiveData<StorageInfo> = _storageInfo

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun loadStorageInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.postValue(true)
            try {
                val info = repo.getStorageInfo()
                _storageInfo.postValue(info)
            } catch (e: Exception) {
                _error.postValue(e.localizedMessage ?: "Failed to load storage info")
            } finally {
                _loading.postValue(false)
            }
        }
    }
}