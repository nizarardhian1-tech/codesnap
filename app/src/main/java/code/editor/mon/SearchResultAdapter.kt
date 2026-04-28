package code.editor.mon

import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import code.editor.mon.databinding.ItemSearchResultBinding

class SearchResultAdapter(
    private val items: MutableList<SearchResult>,
    private val onClick: (SearchResult) -> Unit,
    private val onLongClick: (SearchResult) -> Unit
) : RecyclerView.Adapter<SearchResultAdapter.VH>() {

    inner class VH(val b: ItemSearchResultBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemSearchResultBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = items[position]
        val b = holder.b
        val ctx = b.root.context

        b.tvFilePath.text = r.relativePath
        b.tvLineNumber.text = "L${r.lineNumber}"

        // Highlight matched portion in code preview
        val preview = r.linePreview.trim()
        val spanned = SpannableString(preview)
        val start = r.matchStartInLine - (r.linePreview.length - r.linePreview.trimStart().length)
        val end = start + (r.matchEndInLine - r.matchStartInLine)
        if (start >= 0 && end <= spanned.length) {
            spanned.setSpan(
                BackgroundColorSpan(ctx.getColor(R.color.match_highlight)),
                start.coerceAtLeast(0),
                end.coerceAtMost(spanned.length),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        b.tvCodePreview.text = spanned

        b.root.setOnClickListener { onClick(r) }
        b.root.setOnLongClickListener { onLongClick(r); true }
    }
}
