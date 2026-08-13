package com.smart.gallery.cleaner.organizer.app.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import com.smart.gallery.cleaner.organizer.app.data.model.DuplicateGroup
import com.smart.gallery.cleaner.organizer.app.data.model.MediaFile
import com.smart.gallery.cleaner.organizer.app.data.model.MediaType
import com.smart.gallery.cleaner.organizer.app.data.model.SimilarGroup
import com.smart.gallery.cleaner.organizer.app.data.model.StorageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import kotlin.math.abs

class MediaRepository(private val context: Context) {

    private val contentResolver: ContentResolver get() = context.contentResolver

    suspend fun getStorageInfo(): StorageInfo = withContext(Dispatchers.IO) {
        val stat = StatFs(Environment.getExternalStorageDirectory().path)
        val total = stat.totalBytes
        val free = stat.freeBytes
        val used = total - free

        val imageSize = getSizeByUri(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        val videoSize = getSizeByUri(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        val photoCount = getCountByUri(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        val videoCount = getCountByUri(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)

        StorageInfo(
            totalSpace = total,
            usedSpace = used,
            freeSpace = free,
            imageSize = imageSize,
            videoSize = videoSize,
            downloadSize = getDownloadsSize(),
            whatsappSize = getWhatsAppSize(),
            photoCount = photoCount,
            videoCount = videoCount
        )
    }

    private fun getSizeByUri(uri: Uri): Long {
        var total = 0L
        val projection = arrayOf(MediaStore.MediaColumns.SIZE)
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val col = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            while (cursor.moveToNext()) total += cursor.getLong(col)
        }
        return total
    }

    private fun getCountByUri(uri: Uri): Int {
        contentResolver.query(uri, arrayOf(MediaStore.MediaColumns._ID),
            null, null, null)?.use { cursor -> return cursor.count }
        return 0
    }

    suspend fun getAllImages(): List<MediaFile> = withContext(Dispatchers.IO) {
        queryMediaFiles(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaType.IMAGE)
    }

    suspend fun getAllVideos(): List<MediaFile> = withContext(Dispatchers.IO) {
        queryMediaFiles(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, MediaType.VIDEO)
    }

    private fun queryMediaFiles(
        uri: Uri,
        mediaType: MediaType,
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        sortOrder: String = "${MediaStore.MediaColumns.DATE_ADDED} DESC"
    ): List<MediaFile> {
        val files = mutableListOf<MediaFile>()
        val useRelativePath = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.DATE_ADDED)
            add(MediaStore.MediaColumns.DATE_MODIFIED)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            add(MediaStore.MediaColumns.DATA)
            if (useRelativePath) add(MediaStore.MediaColumns.RELATIVE_PATH)
            if (mediaType == MediaType.IMAGE || mediaType == MediaType.VIDEO) {
                add(MediaStore.MediaColumns.WIDTH)
                add(MediaStore.MediaColumns.HEIGHT)
            }
            if (mediaType == MediaType.VIDEO) add(MediaStore.Video.Media.DURATION)
        }.toTypedArray()

        contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
            ?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val addedCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val modCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val albumCol = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val dataCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                val widthCol = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
                val durationCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)

                while (cursor.moveToNext()) {
                    val size = cursor.getLong(sizeCol)
                    if (size <= 0) continue
                    val mime = cursor.getString(mimeCol) ?: continue
                    val id = cursor.getLong(idCol)
                    val path = if (dataCol >= 0) cursor.getString(dataCol) ?: "" else ""
                    val actualType = when {
                        mime.contains("gif", ignoreCase = true) -> MediaType.GIF
                        else -> mediaType
                    }
                    files.add(
                        MediaFile(
                            id = id,
                            uri = ContentUris.withAppendedId(uri, id),
                            name = cursor.getString(nameCol) ?: "",
                            path = path,
                            size = size,
                            dateAdded = cursor.getLong(addedCol) * 1000L,
                            dateModified = cursor.getLong(modCol) * 1000L,
                            mimeType = mime,
                            mediaType = actualType,
                            album = if (albumCol >= 0) cursor.getString(albumCol) ?: "" else "",
                            width = if (widthCol >= 0) cursor.getInt(widthCol) else 0,
                            height = if (heightCol >= 0) cursor.getInt(heightCol) else 0,
                            duration = if (durationCol >= 0) cursor.getLong(durationCol) else 0L
                        )
                    )
                }
            }
        return files
    }

    suspend fun getLargeFiles(thresholdBytes: Long = 5_242_880L): List<MediaFile> =
        withContext(Dispatchers.IO) {
            val sel = "${MediaStore.MediaColumns.SIZE} > ?"
            val args = arrayOf(thresholdBytes.toString())
            val sort = "${MediaStore.MediaColumns.SIZE} DESC"
            (queryMediaFiles(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaType.IMAGE, sel, args, sort) +
                    queryMediaFiles(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, MediaType.VIDEO, sel, args, sort))
                .sortedByDescending { it.size }
        }

    suspend fun getScreenshots(): List<MediaFile> = withContext(Dispatchers.IO) {
        val pathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.MediaColumns.RELATIVE_PATH else MediaStore.MediaColumns.BUCKET_DISPLAY_NAME
        val keywords = listOf("screenshot", "screenshots", "screen record", "screenrecord")
        queryMediaFiles(
            uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            mediaType = MediaType.IMAGE,
            selection = keywords.joinToString(" OR ") { "LOWER($pathCol) LIKE ?" },
            selectionArgs = keywords.map { "%$it%" }.toTypedArray()
        )
    }

    suspend fun getDownloadedMedia(): List<MediaFile> = withContext(Dispatchers.IO) {
        val pathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.MediaColumns.RELATIVE_PATH else MediaStore.MediaColumns.DATA
        val sel = "LOWER($pathCol) LIKE ?"
        val args = arrayOf("%download%")
        (queryMediaFiles(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaType.IMAGE, sel, args) +
                queryMediaFiles(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, MediaType.VIDEO, sel, args))
            .sortedByDescending { it.dateAdded }
    }

    private fun getDownloadsSize(): Long {
        val pathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.MediaColumns.RELATIVE_PATH else MediaStore.MediaColumns.DATA
        val projection = arrayOf(MediaStore.MediaColumns.SIZE)
        val selection = "LOWER($pathCol) LIKE ?"
        val args = arrayOf("%download%")
        var total = 0L
        listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).forEach { uri ->
            contentResolver.query(uri, projection, selection, args, null)?.use { cursor ->
                val col = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                if (col >= 0) while (cursor.moveToNext()) total += cursor.getLong(col)
            }
        }
        return total
    }

    suspend fun getWhatsAppImages(): List<MediaFile> = withContext(Dispatchers.IO) {
        queryWhatsApp(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaType.IMAGE)
    }

    suspend fun getWhatsAppVideos(): List<MediaFile> = withContext(Dispatchers.IO) {
        queryWhatsApp(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, MediaType.VIDEO)
    }

    private fun queryWhatsApp(uri: Uri, mediaType: MediaType): List<MediaFile> {
        val pathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.MediaColumns.RELATIVE_PATH else MediaStore.MediaColumns.DATA
        val keywords = listOf("whatsapp/media", "whatsapp images", "whatsapp video", "com.whatsapp")
        return queryMediaFiles(
            uri = uri,
            mediaType = mediaType,
            selection = keywords.joinToString(" OR ") { "LOWER($pathCol) LIKE ?" },
            selectionArgs = keywords.map { "%$it%" }.toTypedArray()
        )
    }

    private fun getWhatsAppSize(): Long {
        val pathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.MediaColumns.RELATIVE_PATH else MediaStore.MediaColumns.DATA
        val keywords = listOf("whatsapp", "com.whatsapp")
        val selection = keywords.joinToString(" OR ") { "LOWER($pathCol) LIKE ?" }
        val args = keywords.map { "%$it%" }.toTypedArray()
        val projection = arrayOf(MediaStore.MediaColumns.SIZE)
        var total = 0L
        listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).forEach { uri ->
            contentResolver.query(uri, projection, selection, args, null)?.use { cursor ->
                val col = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                if (col >= 0) while (cursor.moveToNext()) total += cursor.getLong(col)
            }
        }
        return total
    }

    fun findDuplicates(files: List<MediaFile>): Flow<List<DuplicateGroup>> = flow {
        val hashMap = mutableMapOf<String, MutableList<MediaFile>>()
        files.forEach { file ->
            if (file.path.isBlank()) return@forEach
            val hash = computeMd5(file.path)
            if (hash.isNotEmpty()) {
                file.md5Hash = hash
                hashMap.getOrPut(hash) { mutableListOf() }.add(file)
            }
        }
        emit(hashMap.filterValues { it.size > 1 }.values.mapIndexed { i, list ->
            DuplicateGroup(groupId = "dup_$i", files = list)
        })
    }.flowOn(Dispatchers.IO)

    private fun computeMd5(path: String): String = try {
        val digest = MessageDigest.getInstance("MD5")
        val buffer = ByteArray(8192)
        FileInputStream(path).use { fis ->
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) digest.update(buffer, 0, read)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        ""
    }

    fun findSimilarPhotos(files: List<MediaFile>): Flow<List<SimilarGroup>> = flow {
        val imageFiles = files.filter { it.isImage && it.width > 0 && it.height > 0 }
        val groups = mutableListOf<SimilarGroup>()
        val processed = mutableSetOf<Long>()

        var groupIdx = 0
        for (i in imageFiles.indices) {
            val fileA = imageFiles[i]
            if (processed.contains(fileA.id)) continue

            val currentGroup = mutableListOf(fileA)
            val ratioA = fileA.width.toFloat() / fileA.height.toFloat()

            for (j in i + 1 until imageFiles.size) {
                val fileB = imageFiles[j]
                if (processed.contains(fileB.id)) continue

                val ratioB = fileB.width.toFloat() / fileB.height.toFloat()
                val isRatioSimilar = abs(ratioA - ratioB) < 0.05f
                val isSizeSimilar = abs(fileA.size - fileB.size) < (fileA.size * 0.2f)

                if (isRatioSimilar && isSizeSimilar) {
                    currentGroup.add(fileB)
                    processed.add(fileB.id)
                }
            }

            if (currentGroup.size > 1) {
                processed.add(fileA.id)
                groups.add(SimilarGroup(groupId = "similar_$groupIdx", files = currentGroup))
                groupIdx++
            }
        }
        emit(groups)
    }.flowOn(Dispatchers.IO)

    suspend fun findBlurryPhotos(files: List<MediaFile>, threshold: Float = 100f): List<MediaFile> =
        withContext(Dispatchers.IO) {
            files.filter { it.isImage && it.path.isNotBlank() }.onEach { file ->
                val score = computeBlurScore(file.path)
                file.blurScore = score
                file.isBlurry = score < threshold
            }.filter { it.isBlurry }
        }

    private fun computeBlurScore(path: String): Float = try {
        val file = File(path)
        if (!file.exists()) 0f else {
            val sizeKb = file.length() / 1024f
            (sizeKb * 0.5f).coerceIn(0f, 1000f)
        }
    } catch (e: Exception) {
        0f
    }

    suspend fun deleteFiles(files: List<MediaFile>): Result<Long> = withContext(Dispatchers.IO) {
        var freed = 0L
        var hadError = false
        files.forEach { file ->
            try {
                if (contentResolver.delete(file.uri, null, null) > 0) {
                    freed += file.size
                } else {
                    val f = File(file.path)
                    if (f.exists() && f.delete()) freed += file.size else hadError = true
                }
            } catch (e: Exception) {
                hadError = true
            }
        }
        if (hadError && freed == 0L) Result.failure(Exception("Failed to delete files"))
        else Result.success(freed)
    }

    suspend fun getRecentlyAdded(daysBack: Int): List<MediaFile> = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() / 1000L - (daysBack * 86_400L)
        val sel = "${MediaStore.MediaColumns.DATE_ADDED} >= ?"
        val args = arrayOf(cutoff.toString())
        (queryMediaFiles(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaType.IMAGE, sel, args) +
                queryMediaFiles(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, MediaType.VIDEO, sel, args))
            .sortedByDescending { it.dateAdded }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private fun queryTrashedFiles(uri: Uri, mediaType: MediaType): List<MediaFile> {
        val files = mutableListOf<MediaFile>()

        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.DATE_ADDED)
            add(MediaStore.MediaColumns.DATE_MODIFIED)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            if (mediaType == MediaType.VIDEO) add(MediaStore.Video.Media.DURATION)
        }.toTypedArray()

        val queryBundle = android.os.Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, "${MediaStore.MediaColumns.IS_TRASHED} = 1")
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")
        }

        contentResolver.query(uri, projection, queryBundle, null)?.use { cursor ->
            val idCol = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val addedCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
            val modCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
            val mimeCol = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
            val albumCol = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val durCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)

            while (cursor.moveToNext()) {
                if (idCol < 0) continue
                val id = cursor.getLong(idCol)
                val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                val mime = if (mimeCol >= 0) cursor.getString(mimeCol) ?: continue else continue
                val actualType = when {
                    mime.contains("gif", ignoreCase = true) -> MediaType.GIF
                    else -> mediaType
                }
                files.add(
                    MediaFile(
                        id = id,
                        uri = ContentUris.withAppendedId(uri, id),
                        name = if (nameCol >= 0) cursor.getString(nameCol) ?: "" else "",
                        path = "",
                        size = size,
                        dateAdded = if (addedCol >= 0) cursor.getLong(addedCol) * 1000L else 0L,
                        dateModified = if (modCol >= 0) cursor.getLong(modCol) * 1000L else 0L,
                        mimeType = mime,
                        mediaType = actualType,
                        album = if (albumCol >= 0) cursor.getString(albumCol) ?: "" else "",
                        duration = if (durCol >= 0) cursor.getLong(durCol) else 0L
                    )
                )
            }
        }
        return files
    }

    suspend fun getTrashedMedia(): List<MediaFile> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@withContext emptyList()
        val images = queryTrashedFiles(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaType.IMAGE)
        val videos = queryTrashedFiles(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, MediaType.VIDEO)
        (images + videos).sortedByDescending { it.dateModified }
    }

    suspend fun getAllMedia(): List<MediaFile> = withContext(Dispatchers.IO) {
        val images = queryMediaFiles(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaType.IMAGE).map { file ->
            if (file.mimeType.equals("image/gif", ignoreCase = true) || file.name.endsWith(".gif", ignoreCase = true))
                file.copy(mediaType = MediaType.GIF) else file
        }
        val videos = queryMediaFiles(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, MediaType.VIDEO)
        (images + videos).sortedByDescending { it.dateAdded }
    }

    suspend fun searchMedia(query: String): List<MediaFile> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val sel = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
        val args = arrayOf("%${query.trim()}%")
        val images = queryMediaFiles(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaType.IMAGE, sel, args).map { file ->
            if (file.mimeType.equals("image/gif", ignoreCase = true) || file.name.endsWith(".gif", ignoreCase = true))
                file.copy(mediaType = MediaType.GIF) else file
        }
        val videos = queryMediaFiles(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, MediaType.VIDEO, sel, args)
        (images + videos).sortedByDescending { it.dateAdded }
    }
}