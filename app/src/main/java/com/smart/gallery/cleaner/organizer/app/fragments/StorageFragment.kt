package com.smart.gallery.cleaner.organizer.app.fragments

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.smart.gallery.cleaner.organizer.app.data.model.MediaFile
import com.smart.gallery.cleaner.organizer.app.data.model.StorageInfo
import com.smart.gallery.cleaner.organizer.app.databinding.FragmentStorageBinding
import com.smart.gallery.cleaner.organizer.app.utils.visibleIf
import com.smart.gallery.cleaner.organizer.app.viewmodel.StorageViewModel

class StorageFragment : Fragment() {

    private var _b: FragmentStorageBinding? = null
    private val b get() = _b!!
    private val vm: StorageViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, c: ViewGroup?, s: Bundle?
    ): View {
        _b = FragmentStorageBinding.inflate(inflater, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)
        setupChart()

        b.btnRefresh.setOnClickListener { vm.loadStorageInfo() }
        vm.loading.observe(viewLifecycleOwner) { loading ->
            b.layoutLoading.visibleIf(loading)
            b.scrollView.visibleIf(!loading)
        }

        vm.storageInfo.observe(viewLifecycleOwner) { info ->
            renderStorageCard(info)
            renderCategories(info)
            updateChart(info)
        }
        vm.error.observe(viewLifecycleOwner) {}
        vm.loadStorageInfo()
    }

    private fun setupChart() {
        b.pieChart.apply {
            description.isEnabled = false
            isDrawHoleEnabled = true
            holeRadius = 58f
            transparentCircleRadius = 62f
            setDrawEntryLabels(false)
            legend.isEnabled = false
            setUsePercentValues(false)
            setHoleColor(Color.TRANSPARENT)
            setTouchEnabled(false)
        }
    }

    private fun updateChart(info: StorageInfo) {
        val usedMedia = info.imageSize + info.videoSize + info.downloadSize + info.whatsappSize
        val other = (info.usedSpace - usedMedia).coerceAtLeast(0L)
        val free = info.freeSpace

        data class Slice(val size: Long, val label: String, val hex: String)

        val slices = listOf(
            Slice(info.imageSize, "Images", "#5B4FCF"),
            Slice(info.videoSize, "Videos", "#EF4444"),
            Slice(info.downloadSize, "Downloads", "#F59E0B"),
            Slice(info.whatsappSize, "WhatsApp", "#22C55E"),
            Slice(other, "Other", "#94A3B8"),
            Slice(free, "Free", "#E2E8F0"),
        ).filter { it.size > 0 }

        if (slices.isEmpty()) return

        val entries = slices.map { PieEntry(it.size.toFloat(), it.label) }
        val colors = slices.map { it.hex.toColorInt() }

        val dataSet = PieDataSet(entries, "").apply {
            this.colors = colors
            sliceSpace = 1.5f
            selectionShift = 0f
            valueTextSize = 0f
        }

        val usedPct = if (info.totalSpace > 0)
            "${((info.usedSpace.toFloat() / info.totalSpace) * 100).toInt()}%"
        else "–"
        b.pieChart.centerText = "$usedPct used"
        b.pieChart.setCenterTextSize(14f)
        b.pieChart.setCenterTextColor("#1E1B4B".toColorInt())

        b.pieChart.data = PieData(dataSet)
        b.pieChart.animateY(600)
        b.pieChart.invalidate()
    }

    private fun renderStorageCard(info: StorageInfo) {
        b.tvStorageSubtitle.text = "Internal · ${info.formattedTotal} total"
        b.tvTotalStorage.text = info.formattedTotal
        b.tvUsedStorage.text = info.formattedUsed
        b.tvFreeStorage.text = info.formattedFree
        b.progressStorage.progress = info.usagePercent
        b.tvProgressLabel.text = "${info.formattedUsed} used"
        b.tvFreeLabel.text = "${info.formattedFree} free"
    }

    private fun renderCategories(info: StorageInfo) {
        val usedMedia = info.imageSize + info.videoSize + info.downloadSize + info.whatsappSize
        val other = (info.usedSpace - usedMedia).coerceAtLeast(0L)
        val denom = info.usedSpace.toFloat().coerceAtLeast(1f)

        fun pct(size: Long): Int {
            if (size <= 0L) return 0
            return ((size.toFloat() / denom) * 100).toInt().coerceIn(1, 100)
        }

        b.tvImages.text = MediaFile.formatSize(info.imageSize)
        b.tvVideos.text = MediaFile.formatSize(info.videoSize)
        b.tvDownloads.text = MediaFile.formatSize(info.downloadSize)
        b.tvWhatsapp.text = MediaFile.formatSize(info.whatsappSize)
        b.tvOther.text = MediaFile.formatSize(other)

        b.progressImages.progress = pct(info.imageSize)
        b.progressVideos.progress = pct(info.videoSize)
        b.progressDownloads.progress = pct(info.downloadSize)
        b.progressWhatsapp.progress = pct(info.whatsappSize)
        b.progressOther.progress = pct(other)
    }

    override fun onDestroyView() {
        super.onDestroyView(); _b = null
    }
}