package com.smart.gallery.cleaner.organizer.app.adapter

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.videoFrameMillis
import com.smart.gallery.cleaner.organizer.app.R
import com.smart.gallery.cleaner.organizer.app.data.model.MediaFile
import com.smart.gallery.cleaner.organizer.app.data.model.MediaType
import com.smart.gallery.cleaner.organizer.app.databinding.ItemMediaGridBinding
import com.smart.gallery.cleaner.organizer.app.utils.formatDuration
import com.smart.gallery.cleaner.organizer.app.utils.gone
import com.smart.gallery.cleaner.organizer.app.utils.visible

class MediaGridAdapter(
    private val onItemClick: (MediaFile) -> Unit,
    private val onSelectionChanged: (MediaFile) -> Unit,
    private val onLongClick: (MediaFile) -> Unit = {},
    private val keepFileId: Long? = null,
    private val bestShotId: Long? = null,
    private val hideCheckbox: Boolean = false,
    private val fixedItemWidthPx: Int? = null
) : ListAdapter<MediaFile, MediaGridAdapter.MediaViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<MediaFile>() {
            override fun areItemsTheSame(a: MediaFile, b: MediaFile) = a.id == b.id
            override fun areContentsTheSame(a: MediaFile, b: MediaFile) =
                a.isSelected == b.isSelected &&
                        a.isBlurry == b.isBlurry &&
                        a.isBestShot == b.isBestShot &&
                        a.size == b.size &&
                        a.duration == b.duration
        }

        fun openInDefaultViewer(context: Context, file: MediaFile) {
            val mimeType = when {
                file.isVideo -> "video/*"
                file.isGif -> "image/gif"
                else -> "image/*"
            }
            try {
                context.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(file.uri, mimeType)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        },
                        null
                    )
                )
            } catch (_: Exception) {
                try {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, file.uri).apply {
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    )
                } catch (_: Exception) {
                }
            }
        }
    }

    inner class MediaViewHolder(val b: ItemMediaGridBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(file: MediaFile) {
            fixedItemWidthPx?.let { w ->
                val lp = b.root.layoutParams
                if (lp != null && lp.width != w) {
                    lp.width = w
                    b.root.layoutParams = lp
                }
            }

            b.ivThumbnail.load(file.uri) {
                crossfade(150)
                placeholder(R.drawable.ic_image_placeholder)
                error(R.drawable.ic_image_placeholder)
                if (file.isVideo) {
                    videoFrameMillis(1000L)
                    decoderFactory { result, options, _ ->
                        coil.decode.VideoFrameDecoder(result.source, options)
                    }
                }
            }

            b.tvSize.text = file.formattedSize
            if (file.mediaType == MediaType.VIDEO) {
                b.ivPlayIcon.visible()
                if (file.duration > 0) {
                    b.tvDuration.text = file.duration.formatDuration()
                    b.tvDuration.visible()
                } else {
                    b.tvDuration.gone()
                }
            } else {
                b.ivPlayIcon.gone()
                b.tvDuration.gone()
            }

            val isKeeper = keepFileId != null && file.id == keepFileId
            val isBest = (bestShotId != null && file.id == bestShotId) || file.isBestShot

            if (isKeeper || (bestShotId != null && isBest)) {
                b.chipKeep.visible()
                b.chipKeep.text = if (isBest && bestShotId != null) {
                    b.root.context.getString(R.string.badge_best_shot)
                } else {
                    b.root.context.getString(R.string.badge_keep)
                }
                b.chipBlurry.gone()
            } else {
                b.chipKeep.gone()
                if (file.isBlurry) b.chipBlurry.visible() else b.chipBlurry.gone()
            }

            if (hideCheckbox) {
                b.flCheckbox.gone()
            } else {
                b.flCheckbox.visible()
            }

            val highlighted = file.isSelected || isKeeper
            b.cbSelect.isChecked = file.isSelected
            b.selectionOverlay.visibility = if (file.isSelected) View.VISIBLE else View.GONE
            b.viewSelectedRing.visibility = if (highlighted) View.VISIBLE else View.GONE

            b.root.setOnClickListener {
                if (hideCheckbox) {
                    onItemClick(file)
                } else if (file.isSelected || currentList.any { it.isSelected }) {
                    onItemClick(file)
                } else {
                    openInDefaultViewer(b.root.context, file)
                }
            }
            b.root.setOnLongClickListener {
                if (hideCheckbox) {
                    openInDefaultViewer(b.root.context, file)
                } else {
                    onLongClick(file)
                }
                true
            }
            b.cbSelect.setOnClickListener { onSelectionChanged(file) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        MediaViewHolder(
            ItemMediaGridBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) =
        holder.bind(getItem(position))
}
