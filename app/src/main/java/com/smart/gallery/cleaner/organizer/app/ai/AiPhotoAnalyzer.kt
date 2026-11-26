package com.smart.gallery.cleaner.organizer.app.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.FaceDetector
import com.smart.gallery.cleaner.organizer.app.data.model.AiCategory
import com.smart.gallery.cleaner.organizer.app.data.model.AiCategoryBucket
import com.smart.gallery.cleaner.organizer.app.data.model.MediaFile
import com.smart.gallery.cleaner.organizer.app.data.model.SimilarGroup
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 100% On-Device AI / Computer Vision Engine for Gallery Cleaner & Organizer.
 * Performs Laplacian variance sharpness analysis, 64-bit perceptual difference hashing (dHash),
 * color histogram matching, exposure/lighting quality analysis, face detection, document/text
 * density detection, nature/scene profiling, and AI Best-Shot ranking.
 */
object AiPhotoAnalyzer {

    data class PhotoAnalysis(
        val fileId: Long,
        val dateModified: Long,
        val laplacianVariance: Float,
        val sharpnessScore: Float,        // 0..100 (higher = sharper)
        val isBlurry: Boolean,
        val meanBrightness: Float,        // 0..255
        val contrastStdDev: Float,
        val isTooDark: Boolean,
        val isOverexposed: Boolean,
        val isBadLighting: Boolean,
        val dHash: Long,                  // 64-bit perceptual hash
        val colorHistogram: FloatArray,   // 12-bin normalized RGB histogram
        val qualityScore: Float,          // 0..100 composite AI photo quality score
        val hasFace: Boolean,
        val isDocumentLike: Boolean,
        val isNatureLike: Boolean,
        val isMemeOrSocial: Boolean,
        val tags: List<String>
    )

    private val cache = ConcurrentHashMap<Long, PhotoAnalysis>()

    fun clearCache() {
        cache.clear()
    }

    fun analyzePhoto(
        context: Context,
        file: MediaFile,
        runFaceDetection: Boolean = false
    ): PhotoAnalysis? {
        val cached = cache[file.id]
        if (cached != null && cached.dateModified == file.dateModified && (!runFaceDetection || cached.hasFace)) {
            return cached
        }

        val bitmap = decodeThumbnail(context, file, targetSize = 144) ?: return null
        return try {
            val width = bitmap.width
            val height = bitmap.height
            if (width < 16 || height < 16) return null

            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val gray = FloatArray(width * height)
            val hist = FloatArray(12)

            var lumSum = 0.0
            var darkCount = 0
            var brightCount = 0
            var whitePaperCount = 0
            var skinPixelCount = 0
            var greenNatureCount = 0
            var skyBlueCount = 0
            var lowSatCount = 0
            var highSatCount = 0

            val totalPixels = pixels.size.toFloat()

            for (i in pixels.indices) {
                val p = pixels[i]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF

                val lum = 0.299f * r + 0.587f * g + 0.114f * b
                gray[i] = lum
                lumSum += lum

                if (lum < 38f) darkCount++
                if (lum > 235f) brightCount++

                val maxC = max(r, max(g, b))
                val minC = min(r, min(g, b))
                val delta = maxC - minC
                val sat = if (maxC == 0) 0f else delta.toFloat() / maxC.toFloat()

                if (sat < 0.16f) lowSatCount++
                if (sat > 0.38f) highSatCount++
                if (lum > 185f && sat < 0.14f) whitePaperCount++

                // Skin-tone heuristic in RGB/YCbCr range
                if (r > 95 && g > 40 && b > 20 && (maxC - minC) > 15 && abs(r - g) > 15 && r > g && r > b) {
                    skinPixelCount++
                }

                // Foliage green & sky blue heuristics
                if (g > r + 12 && g > b + 8 && g > 55) greenNatureCount++
                if (b > r + 18 && b > g + 4 && b > 95 && i < pixels.size * 0.6f) skyBlueCount++

                // 12-bin RGB histogram (4 bins per channel)
                hist[(r * 4 / 256).coerceIn(0, 3)] += 1f
                hist[4 + (g * 4 / 256).coerceIn(0, 3)] += 1f
                hist[8 + (b * 4 / 256).coerceIn(0, 3)] += 1f
            }

            for (k in hist.indices) hist[k] /= totalPixels

            val meanLum = (lumSum / totalPixels).toFloat()
            var lumVarSum = 0.0
            for (v in gray) {
                val d = v - meanLum
                lumVarSum += d * d
            }
            val contrastStd = sqrt(lumVarSum / totalPixels).toFloat()

            // 3x3 Laplacian variance for true focus/blur measurement
            var lapSum = 0.0
            var lapSqSum = 0.0
            var lapCount = 0
            var strongEdges = 0
            for (y in 1 until height - 1) {
                val row = y * width
                for (x in 1 until width - 1) {
                    val idx = row + x
                    val lap = -4f * gray[idx] +
                            gray[idx - 1] + gray[idx + 1] +
                            gray[idx - width] + gray[idx + width]
                    lapSum += lap
                    lapSqSum += lap * lap
                    if (abs(lap) > 28f) strongEdges++
                    lapCount++
                }
            }
            val lapMean = if (lapCount > 0) lapSum / lapCount else 0.0
            val lapVariance = if (lapCount > 0) {
                ((lapSqSum / lapCount) - (lapMean * lapMean)).toFloat().coerceAtLeast(0f)
            } else 0f

            // Map Laplacian variance to an intuitive 0..100 sharpness score
            val sharpnessScore = (sqrt(lapVariance) / 28f * 100f).coerceIn(0f, 100f)
            val edgeDensity = if (lapCount > 0) strongEdges.toFloat() / lapCount else 0f

            val darkRatio = darkCount / totalPixels
            val brightRatio = brightCount / totalPixels
            val whitePaperRatio = whitePaperCount / totalPixels
            val lowSatRatio = lowSatCount / totalPixels
            val skinRatio = skinPixelCount / totalPixels
            val greenRatio = greenNatureCount / totalPixels
            val skyRatio = skyBlueCount / totalPixels
            val highSatRatio = highSatCount / totalPixels

            val lowerAlbum = file.album.lowercase()
            val lowerPath = file.path.lowercase()
            val lowerName = file.name.lowercase()
            val isScreenshot = lowerAlbum.contains("screenshot") || lowerPath.contains("screenshot") || lowerName.contains("screenshot")
            val isSocialFolder = lowerAlbum.contains("whatsapp") || lowerAlbum.contains("download") ||
                    lowerAlbum.contains("telegram") || lowerAlbum.contains("instagram") ||
                    lowerAlbum.contains("facebook") || lowerPath.contains("whatsapp")

            val isTooDark = meanLum < 46f || (darkRatio > 0.72f && meanLum < 68f)
            val isOverexposed = (meanLum > 226f && brightRatio > 0.62f && contrastStd < 38f)
            val isBadLighting = (isTooDark || isOverexposed) && !isScreenshot

            // Blurry if Laplacian variance is very low (excluding clean vector screenshots or solid dark images)
            val isBlurry = !isScreenshot && lapVariance < 75f && edgeDensity < 0.045f

            // 64-bit Perceptual Difference Hash (9x8 grayscale)
            val dHash = computeDHash(bitmap)

            // Face detection (only when requested or skin pixels suggest a portrait)
            val hasFace = if (runFaceDetection && skinRatio > 0.04f && !isScreenshot && !isTooDark) {
                detectFace(bitmap)
            } else {
                cached?.hasFace ?: false
            }

            // Document / Notes / Receipts heuristic
            val isDocumentLike = !hasFace &&
                    ((whitePaperRatio > 0.42f && edgeDensity > 0.06f && lowSatRatio > 0.62f) ||
                            (lowerName.contains("doc") || lowerName.contains("scan") || lowerName.contains("receipt") || lowerName.contains("note")))

            // Nature / Outdoors heuristic
            val isNatureLike = !isScreenshot && !isDocumentLike &&
                    ((greenRatio > 0.18f && highSatRatio > 0.22f) ||
                            (skyRatio > 0.16f && greenRatio > 0.08f) ||
                            (skyRatio > 0.28f && highSatRatio > 0.25f))

            // Memes / Social media graphics
            val isMemeOrSocial = isSocialFolder && !hasFace

            // Composite AI Quality Score (0..100) for Best Shot ranking
            val exposureBalance = (1f - (abs(meanLum - 126f) / 126f)).coerceIn(0f, 1f) * 100f
            val contrastScore = (contrastStd / 64f * 100f).coerceIn(0f, 100f)
            val megapixels = (file.width.toLong() * file.height.toLong()) / 1_000_000f
            val resScore = (megapixels / 12f * 100f).coerceIn(10f, 100f)
            val faceBonus = if (hasFace) 12f else 0f
            val penalty = (if (isBlurry) 30f else 0f) + (if (isBadLighting) 25f else 0f)

            val qualityScore = (
                    sharpnessScore * 0.46f +
                            exposureBalance * 0.22f +
                            contrastScore * 0.16f +
                            resScore * 0.16f +
                            faceBonus - penalty
                    ).coerceIn(0f, 100f)

            val tags = buildList {
                if (isBlurry) add("blurry")
                if (isTooDark) add("dark")
                if (isOverexposed) add("overexposed")
                if (hasFace) {
                    add("portrait")
                    add("selfie")
                    add("people")
                }
                if (isDocumentLike) {
                    add("document")
                    add("receipt")
                    add("notes")
                }
                if (isNatureLike) {
                    add("nature")
                    add("outdoor")
                    add("scenery")
                }
                if (isScreenshot) add("screenshot")
                if (isMemeOrSocial) add("social")
                if (qualityScore >= 72f && !isScreenshot && !isDocumentLike) add("best")
            }

            val result = PhotoAnalysis(
                fileId = file.id,
                dateModified = file.dateModified,
                laplacianVariance = lapVariance,
                sharpnessScore = sharpnessScore,
                isBlurry = isBlurry,
                meanBrightness = meanLum,
                contrastStdDev = contrastStd,
                isTooDark = isTooDark,
                isOverexposed = isOverexposed,
                isBadLighting = isBadLighting,
                dHash = dHash,
                colorHistogram = hist,
                qualityScore = qualityScore,
                hasFace = hasFace,
                isDocumentLike = isDocumentLike,
                isNatureLike = isNatureLike,
                isMemeOrSocial = isMemeOrSocial,
                tags = tags
            )
            cache[file.id] = result
            result
        } catch (_: Throwable) {
            null
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    fun applyAnalysisToFile(file: MediaFile, analysis: PhotoAnalysis): MediaFile {
        return file.copy(
            isBlurry = analysis.isBlurry,
            blurScore = analysis.sharpnessScore,
            qualityScore = analysis.qualityScore,
            brightness = analysis.meanBrightness,
            isDarkOrOverexposed = analysis.isBadLighting,
            hasFace = analysis.hasFace,
            isDocumentLike = analysis.isDocumentLike,
            aiTags = analysis.tags
        )
    }

    fun findSimilarPhotoGroups(
        context: Context,
        images: List<MediaFile>,
        maxPhotosToAnalyze: Int = 500,
        onProgress: ((processed: Int, total: Int) -> Unit)? = null
    ): List<SimilarGroup> {
        val candidates = images.filter { it.isImage && !it.isGif }.take(maxPhotosToAnalyze)
        val total = candidates.size
        val analyzed = mutableListOf<Pair<MediaFile, PhotoAnalysis>>()

        candidates.forEachIndexed { idx, img ->
            val a = analyzePhoto(context, img, runFaceDetection = false)
            if (a != null) {
                analyzed.add(applyAnalysisToFile(img, a) to a)
            }
            if (idx % 4 == 0 || idx == candidates.lastIndex) {
                onProgress?.invoke(idx + 1, total)
            }
        }

        val used = HashSet<Long>()
        val groups = mutableListOf<SimilarGroup>()
        var groupIdx = 0

        for (i in analyzed.indices) {
            val (fileA, aA) = analyzed[i]
            if (used.contains(fileA.id)) continue

            val cluster = mutableListOf(fileA to aA)

            for (j in i + 1 until analyzed.size) {
                val (fileB, aB) = analyzed[j]
                if (used.contains(fileB.id)) continue

                val hamming = hammingDistance(aA.dHash, aB.dHash)
                if (hamming > 10) continue

                val histSim = histogramSimilarity(aA.colorHistogram, aB.colorHistogram)
                val timeDiffSec = abs(fileA.dateAdded - fileB.dateAdded) / 1000L
                val isBurstTime = timeDiffSec <= 600L // within 10 minutes

                val isMatch = (hamming <= 6 && histSim >= 0.78f) ||
                        (hamming <= 10 && histSim >= 0.85f && isBurstTime)

                if (isMatch) {
                    cluster.add(fileB to aB)
                    used.add(fileB.id)
                }
            }

            if (cluster.size > 1) {
                used.add(fileA.id)
                // Re-score with face detection to pick the true AI Best Shot!
                val ranked = cluster.map { (f, _) ->
                    val detailed = analyzePhoto(context, f, runFaceDetection = true)
                    if (detailed != null) applyAnalysisToFile(f, detailed) else f
                }.sortedByDescending { it.qualityScore }

                val bestId = ranked.first().id
                val marked = ranked.mapIndexed { idx, f ->
                    f.copy(isBestShot = idx == 0, similarGroupId = "sim_$groupIdx")
                }
                groups.add(
                    SimilarGroup(
                        groupId = "sim_$groupIdx",
                        files = marked,
                        bestShotId = bestId,
                        keepFileId = bestId
                    )
                )
                groupIdx++
            }
        }

        return groups.sortedByDescending { it.wastedSize }
    }

    fun buildSmartCategories(
        context: Context,
        images: List<MediaFile>,
        maxPhotosToAnalyze: Int = 280,
        onProgress: ((processed: Int, total: Int) -> Unit)? = null
    ): List<AiCategoryBucket> {
        val candidates = images.filter { it.isImage && !it.isGif }.take(maxPhotosToAnalyze)
        val portraits = mutableListOf<MediaFile>()
        val documents = mutableListOf<MediaFile>()
        val badLighting = mutableListOf<MediaFile>()
        val nature = mutableListOf<MediaFile>()
        val memesSocial = mutableListOf<MediaFile>()
        val bestShots = mutableListOf<MediaFile>()

        val total = candidates.size
        candidates.forEachIndexed { idx, img ->
            val analysis = analyzePhoto(context, img, runFaceDetection = true)
            if (analysis != null) {
                val enriched = applyAnalysisToFile(img, analysis)
                if (analysis.hasFace) portraits.add(enriched)
                if (analysis.isDocumentLike) documents.add(enriched)
                if (analysis.isBadLighting) badLighting.add(enriched)
                if (analysis.isNatureLike) nature.add(enriched)
                if (analysis.isMemeOrSocial) memesSocial.add(enriched)
                if (analysis.qualityScore >= 65f && !analysis.isBlurry && !analysis.isBadLighting && !analysis.isDocumentLike) {
                    bestShots.add(enriched.copy(isBestShot = true))
                }
            }
            if (idx % 4 == 0 || idx == candidates.lastIndex) {
                onProgress?.invoke(idx + 1, total)
            }
        }

        return listOf(
            AiCategoryBucket(AiCategory.PORTRAITS, portraits.sortedByDescending { it.qualityScore }),
            AiCategoryBucket(AiCategory.DOCUMENTS, documents.sortedByDescending { it.dateAdded }),
            AiCategoryBucket(AiCategory.BAD_LIGHTING, badLighting.sortedBy { it.brightness }),
            AiCategoryBucket(AiCategory.NATURE, nature.sortedByDescending { it.qualityScore }),
            AiCategoryBucket(AiCategory.MEMES_SOCIAL, memesSocial.sortedByDescending { it.dateAdded }),
            AiCategoryBucket(AiCategory.BEST_SHOTS, bestShots.sortedByDescending { it.qualityScore })
        ).sortedByDescending { it.files.size }
    }

    fun calculateHealthScore(
        totalPhotos: Int,
        duplicateExtras: Int,
        similarExtras: Int,
        blurryCount: Int,
        badLightingCount: Int,
        oldScreenshotsCount: Int
    ): Int {
        if (totalPhotos <= 0) return 100
        val junkWeighted = (duplicateExtras * 2.2f) +
                (blurryCount * 1.8f) +
                (badLightingCount * 1.4f) +
                (similarExtras * 1.2f) +
                (oldScreenshotsCount * 0.8f)
        val ratio = junkWeighted / totalPhotos.toFloat()
        val penalty = (ratio * 75f).toInt().coerceIn(0, 72)
        return (100 - penalty).coerceIn(25, 100)
    }

    private fun computeDHash(source: Bitmap): Long {
        val scaled = Bitmap.createScaledBitmap(source, 9, 8, true)
        return try {
            var hash = 0L
            var bit = 0
            for (y in 0 until 8) {
                for (x in 0 until 8) {
                    val left = luminance(scaled.getPixel(x, y))
                    val right = luminance(scaled.getPixel(x + 1, y))
                    if (left > right) {
                        hash = hash or (1L shl bit)
                    }
                    bit++
                }
            }
            hash
        } finally {
            if (scaled !== source && !scaled.isRecycled) scaled.recycle()
        }
    }

    private fun detectFace(source: Bitmap): Boolean {
        // FaceDetector requires even width and RGB_565 config
        val evenW = (source.width / 2) * 2
        val h = source.height
        if (evenW < 24 || h < 24) return false

        val rgb565 = Bitmap.createBitmap(evenW, h, Bitmap.Config.RGB_565)
        return try {
            val canvas = android.graphics.Canvas(rgb565)
            canvas.drawBitmap(source, 0f, 0f, null)
            val faces = arrayOfNulls<FaceDetector.Face>(2)
            val detector = FaceDetector(evenW, h, 2)
            val found = detector.findFaces(rgb565, faces)
            found > 0 && (faces[0]?.confidence() ?: 0f) >= 0.35f
        } catch (_: Exception) {
            false
        } finally {
            if (!rgb565.isRecycled) rgb565.recycle()
        }
    }

    private fun decodeThumbnail(context: Context, file: MediaFile, targetSize: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(file.uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            val origW = bounds.outWidth
            val origH = bounds.outHeight
            if (origW <= 0 || origH <= 0) return null

            var sample = 1
            while ((origW / (sample * 2)) >= targetSize && (origH / (sample * 2)) >= targetSize) {
                sample *= 2
            }

            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(file.uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun luminance(color: Int): Float {
        return 0.299f * Color.red(color) + 0.587f * Color.green(color) + 0.114f * Color.blue(color)
    }

    fun hammingDistance(a: Long, b: Long): Int {
        return java.lang.Long.bitCount(a xor b)
    }

    private fun histogramSimilarity(h1: FloatArray, h2: FloatArray): Float {
        var diff = 0f
        for (i in h1.indices) {
            diff += abs(h1[i] - h2[i])
        }
        // Maximum possible L1 diff across 3 normalized channels is 6.0
        return (1f - (diff / 6f)).coerceIn(0f, 1f)
    }
}
