package com.smart.gallery.cleaner.organizer.app.data.model

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Locale

enum class MediaType { IMAGE, VIDEO, GIF, AUDIO, DOCUMENT, APK, OTHER }

enum class FileCategory {
    LARGE_FILE, DUPLICATE, SIMILAR, BLURRY, SCREENSHOT, VIDEO, DOWNLOAD, WHATSAPP, EMPTY_ALBUM, ALL
}

@Parcelize
data class AlbumInfo(
    val name: String,
    val path: String,
    val parent: String = "",
    val count: Int = 0,
    val size: Long = 0L
) : Parcelable

@Parcelize
data class MediaFile(
    val id: Long,
    val uri: Uri,
    val name: String,
    val path: String,
    val size: Long,
    val dateAdded: Long,
    val dateModified: Long,
    val mimeType: String,
    val mediaType: MediaType,
    val album: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val duration: Long = 0L,
    var isSelected: Boolean = false,
    var isBlurry: Boolean = false,
    var blurScore: Float = 0f,
    var duplicateGroupId: String = "",
    var similarGroupId: String = "",
    var md5Hash: String = ""
) : Parcelable {

    val formattedSize: String get() = formatSize(size)
    val isImage: Boolean get() = mediaType == MediaType.IMAGE
    val isVideo: Boolean get() = mediaType == MediaType.VIDEO
    val isGif: Boolean get() = mediaType == MediaType.GIF

    companion object {
        fun formatSize(bytes: Long): String = when {
            bytes >= 1_073_741_824L -> String.format(Locale.US, "%.2f GB", bytes / 1_073_741_824.0)
            bytes >= 1_048_576L -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
            bytes >= 1_024L -> String.format(Locale.US, "%.1f KB", bytes / 1_024.0)
            else -> "$bytes B"
        }
    }
}

data class DeleteResult(
    val deletedFiles: List<MediaFile>,
    val failedFiles: List<MediaFile>,
    val freedSpace: Long
)

@Parcelize
data class DuplicateGroup(
    val groupId: String,
    val files: List<MediaFile>,
    val totalSize: Long = files.sumOf { it.size },
    val wastedSize: Long = if (files.isNotEmpty()) totalSize - (files.maxOfOrNull { it.size } ?: 0L) else 0L
) : Parcelable

@Parcelize
data class SimilarGroup(
    val groupId: String,
    val files: List<MediaFile>,
    val totalSize: Long = files.sumOf { it.size },
    val wastedSize: Long = if (files.size > 1) totalSize - (files.firstOrNull()?.size ?: 0L) else 0L
) : Parcelable

data class StorageInfo(
    val totalSpace: Long,
    val usedSpace: Long,
    val freeSpace: Long,
    val imageSize: Long = 0L,
    val videoSize: Long = 0L,
    val downloadSize: Long = 0L,
    val whatsappSize: Long = 0L,
    val otherSize: Long = 0L,
    val photoCount: Int = 0,
    val videoCount: Int = 0
) {
    val usagePercent: Int
        get() = if (totalSpace > 0) ((usedSpace.toFloat() / totalSpace) * 100).toInt().coerceIn(0, 100) else 0
    val formattedTotal: String get() = MediaFile.formatSize(totalSpace)
    val formattedUsed: String get() = MediaFile.formatSize(usedSpace)
    val formattedFree: String get() = MediaFile.formatSize(freeSpace)
}

data class ScanResult(
    val totalPhotos: Int = 0,
    val totalVideos: Int = 0,
    val largeFilesCount: Int = 0,
    val largeFilesSize: Long = 0L,
    val duplicatesCount: Int = 0,
    val duplicatesSize: Long = 0L,
    val similarCount: Int = 0,
    val similarSize: Long = 0L,
    val blurryCount: Int = 0,
    val blurrySize: Long = 0L,
    val screenshotsCount: Int = 0,
    val screenshotsSize: Long = 0L,
    val videosSize: Long = 0L,
    val downloadsCount: Int = 0,
    val downloadsSize: Long = 0L,
    val whatsappCount: Int = 0,
    val whatsappSize: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
) {
    val totalReclaimable: Long
        get() = largeFilesSize + duplicatesSize + similarSize + blurrySize + screenshotsSize + downloadsSize + whatsappSize

    val junkCount: Int
        get() = largeFilesCount + duplicatesCount + similarCount + blurryCount + screenshotsCount + downloadsCount + whatsappCount
}

sealed class ScanState {
    data object Idle : ScanState()
    data class Scanning(val progress: Int = 0, val message: String = "") : ScanState()
    data class Complete(val result: ScanResult) : ScanState()
    data class Error(val message: String) : ScanState()
}