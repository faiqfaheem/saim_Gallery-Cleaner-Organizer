package com.smart.gallery.cleaner.organizer.app.fragments

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.smart.gallery.cleaner.organizer.app.R
import com.smart.gallery.cleaner.organizer.app.data.model.MediaFile
import com.smart.gallery.cleaner.organizer.app.data.model.StorageInfo
import com.smart.gallery.cleaner.organizer.app.databinding.FragmentStorageBinding
import com.smart.gallery.cleaner.organizer.app.utils.safeNavigate
import com.smart.gallery.cleaner.organizer.app.utils.visibleIf
import com.smart.gallery.cleaner.organizer.app.viewmodel.StorageViewModel

class StorageFragment : Fragment() {

    private var _b: FragmentStorageBinding? = null
    private val b get() = _b!!
    private val vm: StorageViewModel by viewModels()
    private var defaultCenterText: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, c: ViewGroup?, s: Bundle?
    ): View {
        _b = FragmentStorageBinding.inflate(inflater, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)
        setupIconTints()
        setupChart()
        setupClickListeners()

        vm.loading.observe(viewLifecycleOwner) { loading ->
            val hasData = vm.storageInfo.value != null
            b.layoutLoading.visibleIf(loading && !hasData)
            b.scrollView.visibleIf(!loading || hasData)
        }

        vm.storageInfo.observe(viewLifecycleOwner) { info ->
            renderStorageCard(info)
            renderCategories(info)
            updateSegmentedBar(info)
            updateChart(info)
        }
        vm.error.observe(viewLifecycleOwner) {}
    }

    override fun onResume() {
        super.onResume()
        if (vm.loading.value != true) {
            vm.loadStorageInfo()
        }
    }

    private fun setupIconTints() {
        val ctx = requireContext()
        fun tintBadge(container: View, colorRes: Int) {
            val c = ContextCompat.getColor(ctx, colorRes)
            container.background?.mutate()?.setTint(Color.argb(38, Color.red(c), Color.green(c), Color.blue(c)))
        }
        tintBadge(b.flIconImages, R.color.color_ai_organizer)
        tintBadge(b.flIconVideos, R.color.color_videos)
        tintBadge(b.flIconScreenshots, R.color.color_screenshots)
        tintBadge(b.flIconDownloads, R.color.color_downloads)
        tintBadge(b.flIconWhatsapp, R.color.color_whatsapp)
        tintBadge(b.flIconOther, R.color.chart_other_slice)
    }

    private fun setupClickListeners() {
        b.rowImages.setOnClickListener { safeNavigate(R.id.action_storage_to_aiCategories) }
        b.rowVideos.setOnClickListener { safeNavigate(R.id.action_storage_to_videos) }
        b.rowScreenshots.setOnClickListener { safeNavigate(R.id.action_storage_to_screenshots) }
        b.rowDownloads.setOnClickListener { safeNavigate(R.id.action_storage_to_downloads) }
        b.rowWhatsapp.setOnClickListener { safeNavigate(R.id.action_storage_to_whatsapp) }
        b.cardStorageAiClean.setOnClickListener { safeNavigate(R.id.action_storage_to_aiSmartClean) }
    }

    private fun setupChart() {
        b.pieChart.apply {
            description.isEnabled = false
            isDrawHoleEnabled = true
            holeRadius = 64f
            transparentCircleRadius = 68f
            setTransparentCircleColor(Color.WHITE)
            setTransparentCircleAlpha(18)
            setDrawEntryLabels(false)
            legend.isEnabled = false
            setUsePercentValues(false)
            setHoleColor(Color.TRANSPARENT)
            setTouchEnabled(true)
            isRotationEnabled = false
            isHighlightPerTapEnabled = true

            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    val pe = e as? PieEntry ?: return
                    val sizeText = MediaFile.formatSize(pe.value.toLong())
                    centerText = "${pe.label}\n$sizeText"
                }

                override fun onNothingSelected() {
                    centerText = defaultCenterText
                }
            })
        }
    }

    private fun updateChart(info: StorageInfo) {
        val ctx = requireContext()
        val pureImages = (info.imageSize - info.screenshotsSize).coerceAtLeast(0L)
        val usedMedia = pureImages + info.screenshotsSize + info.videoSize + info.downloadSize + info.whatsappSize
        val other = (info.usedSpace - usedMedia).coerceAtLeast(0L)
        val free = info.freeSpace

        data class Slice(val size: Long, val label: String, val color: Int)

        val slices = listOf(
            Slice(pureImages, getString(R.string.filter_images), ContextCompat.getColor(ctx, R.color.color_ai_organizer)),
            Slice(info.videoSize, getString(R.string.videos), ContextCompat.getColor(ctx, R.color.color_videos)),
            Slice(info.screenshotsSize, getString(R.string.screenshots), ContextCompat.getColor(ctx, R.color.color_screenshots)),
            Slice(info.downloadSize, getString(R.string.downloads), ContextCompat.getColor(ctx, R.color.color_downloads)),
            Slice(info.whatsappSize, getString(R.string.whatsapp_short), ContextCompat.getColor(ctx, R.color.color_whatsapp)),
            Slice(other, getString(R.string.storage_system_other), Color.parseColor("#8C9FB3")),
            Slice(free, getString(R.string.label_free_cap), Color.parseColor("#2AFFFFFF"))
        ).filter { it.size > 0 }

        if (slices.isEmpty()) return

        val entries = slices.map { PieEntry(it.size.toFloat(), it.label) }
        val colors = slices.map { it.color }

        val dataSet = PieDataSet(entries, "").apply {
            this.colors = colors
            sliceSpace = 2.5f
            selectionShift = 5f
            valueTextSize = 0f
        }

        val usedPct = if (info.totalSpace > 0) "${info.usagePercent}%" else "–"
        defaultCenterText = getString(R.string.storage_used_pct_format, usedPct)

        b.pieChart.centerText = defaultCenterText
        b.pieChart.setCenterTextSize(15f)
        b.pieChart.setCenterTextColor(Color.WHITE)

        b.pieChart.data = PieData(dataSet)
        b.pieChart.animateY(550)
        b.pieChart.invalidate()
    }

    private fun updateSegmentedBar(info: StorageInfo) {
        val total = info.totalSpace.toFloat().coerceAtLeast(1f)
        val pureImages = (info.imageSize - info.screenshotsSize).coerceAtLeast(0L)
        val usedMedia = pureImages + info.screenshotsSize + info.videoSize + info.downloadSize + info.whatsappSize
        val other = (info.usedSpace - usedMedia).coerceAtLeast(0L)

        fun setWeight(v: View, bytes: Long) {
            val lp = v.layoutParams as? LinearLayout.LayoutParams ?: return
            val ratio = (bytes / total).coerceAtLeast(0f)
            lp.weight = if (bytes > 0L) ratio.coerceAtLeast(0.02f) else 0f
            v.layoutParams = lp
        }

        setWeight(b.segImages, pureImages)
        setWeight(b.segVideos, info.videoSize)
        setWeight(b.segScreenshots, info.screenshotsSize)
        setWeight(b.segDownloads, info.downloadSize)
        setWeight(b.segWhatsapp, info.whatsappSize)
        setWeight(b.segOther, other)
        setWeight(b.segFree, info.freeSpace)
    }

    private fun renderStorageCard(info: StorageInfo) {
        b.tvStorageSubtitle.text = getString(R.string.storage_internal_total, info.formattedTotal)
        b.tvTotalStorage.text = info.formattedTotal
        b.tvUsedStorage.text = info.formattedUsed
        b.tvFreeStorage.text = info.formattedFree
    }

    private fun renderCategories(info: StorageInfo) {
        val usedMedia = info.imageSize + info.videoSize + info.downloadSize + info.whatsappSize
        val other = (info.usedSpace - usedMedia).coerceAtLeast(0L)
        val denom = info.usedSpace.toFloat().coerceAtLeast(1f)

        fun pct(size: Long): Int {
            if (size <= 0L) return 0
            return ((size.toFloat() / denom) * 100).toInt().coerceIn(1, 100)
        }

        fun metaText(count: Int, size: Long): String {
            val items = resources.getQuantityString(R.plurals.items_count, count, count)
            return "$items · ${pct(size)}%"
        }

        b.tvImages.text = MediaFile.formatSize(info.imageSize)
        b.tvVideos.text = MediaFile.formatSize(info.videoSize)
        b.tvScreenshots.text = MediaFile.formatSize(info.screenshotsSize)
        b.tvDownloads.text = MediaFile.formatSize(info.downloadSize)
        b.tvWhatsapp.text = MediaFile.formatSize(info.whatsappSize)
        b.tvOther.text = MediaFile.formatSize(other)

        b.tvImagesMeta.text = metaText(info.photoCount, info.imageSize)
        b.tvVideosMeta.text = metaText(info.videoCount, info.videoSize)
        b.tvScreenshotsMeta.text = metaText(info.screenshotCount, info.screenshotsSize)
        b.tvDownloadsMeta.text = metaText(info.downloadCount, info.downloadSize)
        b.tvWhatsappMeta.text = metaText(info.whatsappCount, info.whatsappSize)
        b.tvOtherMeta.text = "${pct(other)}%"

        b.progressImages.setProgressCompat(pct(info.imageSize), true)
        b.progressVideos.setProgressCompat(pct(info.videoSize), true)
        b.progressScreenshots.setProgressCompat(pct(info.screenshotsSize), true)
        b.progressDownloads.setProgressCompat(pct(info.downloadSize), true)
        b.progressWhatsapp.setProgressCompat(pct(info.whatsappSize), true)
        b.progressOther.setProgressCompat(pct(other), true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
