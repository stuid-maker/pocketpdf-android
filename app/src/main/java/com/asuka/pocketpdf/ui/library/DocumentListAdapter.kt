package com.asuka.pocketpdf.ui.library

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.asuka.pocketpdf.R
import com.asuka.pocketpdf.databinding.ItemDocumentBinding
import com.asuka.pocketpdf.domain.model.Document
import com.asuka.pocketpdf.domain.model.IndexStatus

/**
 * 文档库列表的 Adapter。
 *
 * 选 ListAdapter + DiffUtil 而非 RecyclerView.Adapter 的工程论据：
 * - Room Flow 每次 emit 都会推一份新的完整列表（与 RxJava / mutable list 不同，Flow item 是 immutable list）。
 *   不用 DiffUtil 就得 `notifyDataSetChanged()`，列表抖一下；DiffUtil 按 id 比对增量更新动画自然。
 *
 * Item 视图（item_document.xml）：左 PDF icon + 右上 title / 右下 pages·time / 最右索引徽章。
 *
 * 索引徽章 W1 阶段恒为 NOT_INDEXED；W2 切 INDEXING（带进度）/ INDEXED / FAILED。
 */
internal class DocumentListAdapter(
    private val onClick: (Document) -> Unit,
) : ListAdapter<Document, DocumentListAdapter.DocumentViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DocumentViewHolder {
        val binding = ItemDocumentBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return DocumentViewHolder(binding, onClick)
    }

    override fun onBindViewHolder(holder: DocumentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * 取一个位置上的 Document（供 ItemTouchHelper 拿到滑动条目的领域数据）。
     * 仅在 ItemTouchHelper.onSwiped 里被 Activity 调用，故 internal 可见性即可。
     */
    fun documentAt(position: Int): Document? {
        // ItemTouchHelper can report NO_POSITION while RecyclerView is settling animations.
        return if (position in 0 until itemCount) getItem(position) else null
    }

    internal class DocumentViewHolder(
        private val binding: ItemDocumentBinding,
        private val onClick: (Document) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(document: Document) {
            binding.tvDocumentTitle.text = document.title
            binding.tvDocumentMeta.text = binding.root.context.getString(
                R.string.library_item_meta,
                document.pageCount,
                DateUtils.getRelativeTimeSpanString(
                    document.importedAt,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE,
                ),
            )
            binding.tvDocumentIndexBadge.text = renderBadge(document.indexStatus)
            binding.root.setOnClickListener { onClick(document) }
        }

        private fun renderBadge(status: IndexStatus): String {
            val context = binding.root.context
            return when (status) {
                IndexStatus.NOT_INDEXED -> context.getString(R.string.library_badge_not_indexed)
                IndexStatus.INDEXING -> context.getString(R.string.library_badge_indexing)
                IndexStatus.INDEXED -> context.getString(R.string.library_badge_indexed)
                IndexStatus.FAILED -> context.getString(R.string.library_badge_failed)
            }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Document>() {
            override fun areItemsTheSame(oldItem: Document, newItem: Document): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Document, newItem: Document): Boolean =
                oldItem == newItem
        }
    }
}
