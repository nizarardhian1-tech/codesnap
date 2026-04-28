package code.editor.mon

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SnippetAdapter(
    private val snippets: List<String>,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<SnippetAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvSnippet: TextView = itemView.findViewById(android.R.id.text1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val snippet = snippets[position]
        // Show first line or truncated version
        val preview = snippet.lines().firstOrNull()?.take(40) ?: "Snippet"
        holder.tvSnippet.text = if (snippet.lines().size > 1) "$preview..." else preview
        holder.tvSnippet.textSize = 14f
        holder.itemView.setOnClickListener { onClick(snippet) }
    }

    override fun getItemCount() = snippets.size
}
