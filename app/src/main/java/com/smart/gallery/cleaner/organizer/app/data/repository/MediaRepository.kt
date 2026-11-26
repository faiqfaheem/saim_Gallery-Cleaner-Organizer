package com.smart.gallery.cleaner.organizer.app.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import com.smart.gallery.cleaner.organizer.app.ai.AiPhotoAnalyzer
import com.smart.gallery.cleaner.organizer.app.data.model.AiCategoryBucket
import com.smart.gallery.cleaner.organizer.app.data.model.AiCleanRecommendation
import com.smart.gallery.cleaner.organizer.app.data.model.AiCleanRecommendationType
import com.smart.gallery.cleaner.organizer.app.data.model.AiCleanReport
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
import java.security.MessageDigest

class MediaRepository(private val context: Context) {

    private val contentResolver: ContentResolver get() = context.contentResolver

    suspend fun getStorageInfo(): StorageInfo = withContext(Dispatchers.IO) {
        val (total, free, used) = try {
            val stat = StatFs(Environment.getExternalStorageDirectory().path)
            val t = stat.totalBytes
            val f = stat.availableBytes
            Triple(t, f, (t - f).coerceAtLeast(0L))
        } catch (_: Throwable) {
            Triple(0L, 0L, 0L)
        }

        val imageSize = getSizeByUri(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        val videoSize = getSizeByUri(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        val photoCount = getCountByUri(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        val videoCount = getCountByUri(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        val (screenshotsSize, screenshotCount) = getScreenshotsStats()
        val (downloadSize, downloadCount) = getDownloadsStats()
        val (whatsappSize, whatsappCount) = getWhatsAppStats()

        StorageInfo(
            totalSpace = total,
            usedSpace = used,
            freeSpace = free,
            imageSize = imageSize,
            videoSize = videoSize,
            screenshotsSize = screenshotsSize,
            downloadSize = downloadSize,
            whatsappSize = whatsappSize,
            photoCount = photoCount,
            videoCount = videoCount,
            screenshotCount = screenshotCount,
            downloadCount = downloadCount,
            whatsappCount = whatsappCount
        )
    }

    private fun getSizeByUri(uri: Uri): Long {
        var total = 0L
        val projection = arrayOf(MediaStore.MediaColumns.SIZE)
        try {
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val col = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                if (col >= 0) {
                    while (cursor.moveToNext()) total += cursor.getLong(col)
                }
            }
        } catch (_: Throwable) {
        }
        return total
    }

    private fun getCountByUri(uri: Uri): Int {
        return try {
            contentResolver.query(
                uri, arrayOf(MediaStore.MediaColumns._ID),
                null, null, null
            )?.use { cursor -> cursor.count } ?: 0
        } catch (_: Throwable) {
            0
        }
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

        try {
            contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
                ?.use { cursor ->
                    val idCol = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                    val nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                    val addedCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
                    val modCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                    val mimeCol = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                    val albumCol = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                    val dataCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    val widthCol = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                    val heightCol = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
                    val durationCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)

                    if (idCol < 0 || sizeCol < 0) return files

                    while (cursor.moveToNext()) {
                        val size = cursor.getLong(sizeCol)
                        if (size <= 0) continue
                        val mime = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "" else ""
                        if (mime.isBlank()) continue
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
                                name = if (nameCol >= 0) cursor.getString(nameCol) ?: "" else "",
                                path = path,
                                size = size,
                                dateAdded = if (addedCol >= 0) cursor.getLong(addedCol) * 1000L else 0L,
                                dateModified = if (modCol >= 0) cursor.getLong(modCol) * 1000L else 0L,
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
        } catch (_: Throwable) {
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

    private fun getScreenshotsStats(): Pair<Long, Int> {
        val pathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.MediaColumns.RELATIVE_PATH else MediaStore.MediaColumns.BUCKET_DISPLAY_NAME
        val keywords = listOf("screenshot", "screenshots", "screen record", "screenrecord")
        val selection = keywords.joinToString(" OR ") { "LOWER($pathCol) LIKE ?" }
        val args = keywords.map { "%$it%" }.toTypedArray()
        val projection = arrayOf(MediaStore.MediaColumns.SIZE)
        var total = 0L
        var count = 0
        try {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, selection, args, null
            )?.use { cursor ->
                val col = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                if (col >= 0) {
                    while (cursor.moveToNext()) {
                        total += cursor.getLong(col)
                        count++
                    }
                }
            }
        } catch (_: Throwable) {
        }
        return total to count
    }

    private fun getDownloadsStats(): Pair<Long, Int> {
        val pathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.MediaColumns.RELATIVE_PATH else MediaStore.MediaColumns.DATA
        val projection = arrayOf(MediaStore.MediaColumns.SIZE)
        val selection = "LOWER($pathCol) LIKE ?"
        val args = arrayOf("%download%")
        var total = 0L
        var count = 0
        listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).forEach { uri ->
            try {
                contentResolver.query(uri, projection, selection, args, null)?.use { cursor ->
                    val col = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                    if (col >= 0) {
                        while (cursor.moveToNext()) {
                            total += cursor.getLong(col)
                            count++
                        }
                    }
                }
            } catch (_: Throwable) {
            }
        }
        return total to count
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

    private fun getWhatsAppStats(): Pair<Long, Int> {
        val pathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.MediaColumns.RELATIVE_PATH else MediaStore.MediaColumns.DATA
        val keywords = listOf("whatsapp", "com.whatsapp")
        val selection = keywords.joinToString(" OR ") { "LOWER($pathCol) LIKE ?" }
        val args = keywords.map { "%$it%" }.toTypedArray()
        val projection = arrayOf(MediaStore.MediaColumns.SIZE)
        var total = 0L
        var count = 0
        listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).forEach { uri ->
            try {
                contentResolver.query(uri, projection, selection, args, null)?.use { cursor ->
                    val col = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                    if (col >= 0) {
                        while (cursor.moveToNext()) {
                            total += cursor.getLong(col)
                            count++
                        }
                    }
                }
            } catch (_: Throwable) {
            }
        }
        return total to count
    }

    /**
     * Fast & Scoped-Storage safe duplicate detector:
     * Pre-groups by exact byte size first, then hashes only size-matching candidates via ContentResolver.
     */
    fun findDuplicates(
        files: List<MediaFile>,
        onProgress: ((processed: Int, total: Int) -> Unit)? = null
    ): Flow<List<DuplicateGroup>> = flow {
        val totalFiles = files.size
        onProgress?.invoke(0, totalFiles)

        val sizeGroups = files.groupBy { it.size }
        val sameSizeBuckets = sizeGroups.filterValues { it.size > 1 }
        val hashMap = mutableMapOf<String, MutableList<MediaFile>>()

        // Count unique-size files as immediately checked
        var processed = sizeGroups.filterValues { it.size == 1 }.size
        onProgress?.invoke(processed, totalFiles)

        sameSizeBuckets.values.forEach { bucket ->
            bucket.forEach { file ->
                val hash = computeMd5(file.uri)
                if (hash.isNotEmpty()) {
                    val updated = file.copy(md5Hash = hash)
                    hashMap.getOrPut(hash) { mutableListOf() }.add(updated)
                }
                processed++
                if (processed % 3 == 0 || processed == totalFiles) {
                    onProgress?.invoke(processed, totalFiles)
                }
            }
        }

        val groups = hashMap.filterValues { it.size > 1 }.values
            .mapIndexed { i, list ->
                val sorted = list.sortedBy { it.dateAdded }
                DuplicateGroup(
                    groupId = "dup_$i",
                    files = sorted,
                    keepFileId = sorted.first().id
                )
            }
            .sortedByDescending { it.wastedSize }

        onProgress?.invoke(totalFiles, totalFiles)
        emit(groups)
    }.flowOn(Dispatchers.IO)

    private fun computeMd5(uri: Uri): String = try {
        val digest = MessageDigest.getInstance("MD5")
        val buffer = ByteArray(16384)
        contentResolver.openInputStream(uri)?.use { stream ->
            var read: Int
            while (stream.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        } ?: return ""
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (_: Exception) {
        ""
    }

    /**
     * Real On-Device AI Visual Similarity detection using 64-bit Perceptual dHash + Color Histograms + Best Shot Ranking.
     */
    fun findSimilarPhotos(
        files: List<MediaFile>,
        onProgress: ((processed: Int, total: Int) -> Unit)? = null
    ): Flow<List<SimilarGroup>> = flow {
        val groups = AiPhotoAnalyzer.findSimilarPhotoGroups(context, files, onProgress = onProgress)
        emit(groups)
    }.flowOn(Dispatchers.IO)

    /**
     * Real On-Device AI Blur detection using 3x3 Laplacian variance & edge density analysis.
     */
    suspend fun findBlurryPhotos(
        files: List<MediaFile>,
        onProgress: ((processed: Int, total: Int) -> Unit)? = null
    ): List<MediaFile> =
        withContext(Dispatchers.IO) {
            val candidates = files.filter { it.isImage && !it.isGif }.take(500)
            val total = candidates.size
            val result = mutableListOf<MediaFile>()
            candidates.forEachIndexed { idx, file ->
                val analysis = AiPhotoAnalyzer.analyzePhoto(context, file)
                if (analysis != null && analysis.isBlurry) {
                    result.add(AiPhotoAnalyzer.applyAnalysisToFile(file, analysis))
                }
                if (idx % 4 == 0 || idx == candidates.lastIndex) {
                    onProgress?.invoke(idx + 1, total)
                }
            }
            result.sortedBy { it.blurScore }
        }

    /**
     * On-Device AI Bad Lighting / Dark / Overexposed photo detection.
     */
    suspend fun findDarkOrBadLightingPhotos(
        files: List<MediaFile>,
        onProgress: ((processed: Int, total: Int) -> Unit)? = null
    ): List<MediaFile> =
        withContext(Dispatchers.IO) {
            val candidates = files.filter { it.isImage && !it.isGif }.take(500)
            val total = candidates.size
            val result = mutableListOf<MediaFile>()
            candidates.forEachIndexed { idx, file ->
                val analysis = AiPhotoAnalyzer.analyzePhoto(context, file)
                if (analysis != null && analysis.isBadLighting) {
                    result.add(AiPhotoAnalyzer.applyAnalysisToFile(file, analysis))
                }
                if (idx % 4 == 0 || idx == candidates.lastIndex) {
                    onProgress?.invoke(idx + 1, total)
                }
            }
            result.sortedBy { it.brightness }
        }

    /**
     * On-Device AI Smart Auto-Categorization (Portraits, Documents, Bad Lighting, Nature, Social, Best Shots).
     */
    suspend fun getAiSmartCategories(
        onProgress: ((processed: Int, total: Int) -> Unit)? = null
    ): List<AiCategoryBucket> = withContext(Dispatchers.IO) {
        val images = getAllImages()
        AiPhotoAnalyzer.buildSmartCategories(context, images, onProgress = onProgress)
    }

    /**
     * On-Device AI Smart Clean Report with Gallery Health Score & prioritized one-tap recommendations.
     */
    suspend fun buildAiSmartCleanReport(
        onProgress: ((processed: Int, total: Int) -> Unit)? = null
    ): AiCleanReport = withContext(Dispatchers.IO) {
        val images = getAllImages()
        val screenshots = getScreenshots()
        val qualityCandidates = images.filter { it.isImage && !it.isGif }.take(450)
        val totalSteps = (images.size + qualityCandidates.size).coerceAtLeast(1)
        var completedBase = 0
        onProgress?.invoke(0, totalSteps)

        val duplicateExtras = mutableListOf<MediaFile>()
        val dupIds = HashSet<Long>()
        findDuplicates(images) { p, _ ->
            onProgress?.invoke(p.coerceAtMost(images.size), totalSteps)
        }.collect { groups ->
            groups.forEach { g ->
                g.files.filter { it.id != g.keepFileId }.forEach { f ->
                    duplicateExtras.add(f)
                    dupIds.add(f.id)
                }
            }
        }
        completedBase = images.size

        val blurryList = mutableListOf<MediaFile>()
        val badLightingList = mutableListOf<MediaFile>()
        qualityCandidates.forEachIndexed { idx, img ->
            val analysis = AiPhotoAnalyzer.analyzePhoto(context, img)
            if (analysis != null) {
                val enriched = AiPhotoAnalyzer.applyAnalysisToFile(img, analysis)
                if (analysis.isBlurry && img.id !in dupIds) blurryList.add(enriched)
                if (analysis.isBadLighting && img.id !in dupIds && !analysis.isBlurry) badLightingList.add(enriched)
            }
            if (idx % 4 == 0 || idx == qualityCandidates.lastIndex) {
                onProgress?.invoke((completedBase + idx + 1).coerceAtMost(totalSteps), totalSteps)
            }
        }

        val similarGroups = AiPhotoAnalyzer.findSimilarPhotoGroups(context, images, maxPhotosToAnalyze = 400)
        val similarExtras = similarGroups.flatMap { g ->
            g.files.filter { it.id != g.bestShotId && it.id !in dupIds }
        }

        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 86_400_000L)
        val oldScreenshots = screenshots.filter { it.dateAdded < thirtyDaysAgo && it.id !in dupIds }

        val recommendations = buildList {
            if (duplicateExtras.isNotEmpty()) {
                add(AiCleanRecommendation(AiCleanRecommendationType.DUPLICATES, duplicateExtras))
            }
            if (blurryList.isNotEmpty()) {
                add(AiCleanRecommendation(AiCleanRecommendationType.BLURRY, blurryList))
            }
            if (badLightingList.isNotEmpty()) {
                add(AiCleanRecommendation(AiCleanRecommendationType.BAD_LIGHTING, badLightingList))
            }
            if (similarExtras.isNotEmpty()) {
                add(AiCleanRecommendation(AiCleanRecommendationType.SIMILAR_EXTRAS, similarExtras))
            }
            if (oldScreenshots.isNotEmpty()) {
                add(AiCleanRecommendation(AiCleanRecommendationType.OLD_SCREENSHOTS, oldScreenshots))
            }
        }

        val score = AiPhotoAnalyzer.calculateHealthScore(
            totalPhotos = images.size,
            duplicateExtras = duplicateExtras.size,
            similarExtras = similarExtras.size,
            blurryCount = blurryList.size,
            badLightingCount = badLightingList.size,
            oldScreenshotsCount = oldScreenshots.size
        )

        onProgress?.invoke(totalSteps, totalSteps)
        AiCleanReport(
            healthScore = score,
            totalScannedPhotos = images.size,
            recommendations = recommendations
        )
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
            } catch (_: Exception) {
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

        try {
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
        } catch (_: Throwable) {
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
