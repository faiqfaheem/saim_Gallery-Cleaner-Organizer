package com.smart.gallery.cleaner.organizer.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.smart.gallery.cleaner.organizer.app.R
import com.smart.gallery.cleaner.organizer.app.data.model.DuplicateGroup
import com.smart.gallery.cleaner.organizer.app.data.model.MediaFile
import com.smart.gallery.cleaner.organizer.app.databinding.ItemDuplicateGroupBinding

class DuplicateGroupAdapter(
    private val onDeleteGroup: (DuplicateGroup) -> Unit,
    private val onKeepFile: (MediaFile, DuplicateGroup) -> Unit
) : ListAdapter<DuplicateGroup, DuplicateGroupAdapter.GroupViewHolder>(DIFF) {

    private val viewPool = RecyclerView.RecycledViewPool()

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<DuplicateGroup>() {
            override fun areItemsTheSame(a: DuplicateGroup, b: DuplicateGroup) = a.groupId == b.groupId
            override fun areContentsTheSame(a: DuplicateGroup, b: DuplicateGroup) = a == b
        }
    }

    inner class GroupViewHolder(private val b: ItemDuplicateGroupBinding) :
        RecyclerView.ViewHolder(b.root) {

        private var scrollListener: RecyclerView.OnScrollListener? = null

        fun bind(group: DuplicateGroup) {
            b.tvDuplicateCount.text =
                "${group.files.size} copies · ${MediaFile.formatSize(group.wastedSize)} wasted"

            val innerAdapter = MediaGridAdapter(
                onItemClick = { file -> onKeepFile(file, group) },
                onSelectionChanged = {}
            )

            val layoutManager = LinearLayoutManager(b.root.context, LinearLayoutManager.HORIZONTAL, false)
            b.rvDuplicates.layoutManager = layoutManager
            b.rvDuplicates.setRecycledViewPool(viewPool)
            b.rvDuplicates.adapter = innerAdapter
            innerAdapter.submitList(group.files)

            setupDots(group.files.size)

            scrollListener?.let { b.rvDuplicates.removeOnScrollListener(it) }
            scrollListener = object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    val firstVisible = layoutManager.findFirstCompletelyVisibleItemPosition()
                        .takeIf { it != RecyclerView.NO_POSITION }
                        ?: layoutManager.findFirstVisibleItemPosition()

                    if (firstVisible != RecyclerView.NO_POSITION) {
                        updateActiveDot(firstVisible)
                    }
                }
            }
            b.rvDuplicates.addOnScrollListener(scrollListener!!)

            b.btnDeleteGroup.setOnClickListener { onDeleteGroup(group) }
        }

        private fun setupDots(count: Int) {
            b.layoutDotsContainer.removeAllViews()

            if (count <= 1) {
                b.layoutDotsContainer.visibility = View.GONE
                return
            }

            b.layoutDotsContainer.visibility = View.VISIBLE

            val context = b.root.context
            val dotSize = context.resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._6sdp)
            val dotMargin = context.resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._3sdp)

            for (i in 0 until count) {
                val dot = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply {
                        setMargins(dotMargin, 0, dotMargin, 0)
                    }
                    setBackgroundResource(
                        if (i == 0) R.drawable.bg_dot_active else R.drawable.bg_dot_inactive
                    )
                }
                b.layoutDotsContainer.addView(dot)
            }
        }

        private fun updateActiveDot(activeIndex: Int) {
            val container = b.layoutDotsContainer
            for (i in 0 until container.childCount) {
                val dot = container.getChildAt(i)
                dot.setBackgroundResource(
                    if (i == activeIndex) R.drawable.bg_dot_active else R.drawable.bg_dot_inactive
                )
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        GroupViewHolder(
            ItemDuplicateGroupBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) =
        holder.bind(getItem(position))
}