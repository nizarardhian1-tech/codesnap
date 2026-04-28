package code.editor.mon

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import code.editor.mon.databinding.ItemFileTreeBinding

class FileTreeAdapter(
    private val items: MutableList<FileNode>,
    private val onClick: (FileNode) -> Unit,
    private val onLongClick: (FileNode) -> Unit = {}
) : RecyclerView.Adapter<FileTreeAdapter.VH>() {

    inner class VH(val b: ItemFileTreeBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemFileTreeBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val node = items[position]
        val b = holder.b

        // Indentation: 16dp per depth level
        val dp = b.root.context.resources.displayMetrics.density
        val indentPx = (node.depth * 16 * dp).toInt()
        val params = b.indentSpacer.layoutParams
        params.width = indentPx
        b.indentSpacer.layoutParams = params

        b.tvName.text = node.name

        if (node.isDirectory) {
            b.ivIcon.setImageResource(R.drawable.ic_folder)
            b.ivArrow.visibility = android.view.View.VISIBLE
            b.ivArrow.setImageResource(
                if (node.isExpanded) R.drawable.ic_arrow_down else R.drawable.ic_arrow_right
            )
            b.tvName.setTextColor(b.root.context.getColor(R.color.accent_yellow))
        } else {
            b.ivIcon.setImageResource(R.drawable.ic_file)
            b.ivArrow.visibility = android.view.View.INVISIBLE
            b.tvName.setTextColor(b.root.context.getColor(R.color.text_primary))
        }

        b.root.setOnClickListener { onClick(node) }
        b.root.setOnLongClickListener { onLongClick(node); true }
    }
}
