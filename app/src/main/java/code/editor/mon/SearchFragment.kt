package code.editor.mon

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import code.editor.mon.databinding.FragmentSearchBinding
import kotlinx.coroutines.*

class SearchFragment : Fragment() {

    private var _b: FragmentSearchBinding? = null
    private val b get() = _b!!
    private lateinit var vm: SharedViewModel
    private val results = mutableListOf<SearchResult>()
    private lateinit var adapter: SearchResultAdapter
    private var searchJob: Job? = null

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentSearchBinding.inflate(i, c, false).also { _b = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vm = ViewModelProvider(requireActivity())[SharedViewModel::class.java]

        adapter = SearchResultAdapter(results,
            onClick = { openInEditor(it) },
            onLongClick = { openInEditor(it) }
        )
        b.rvResults.layoutManager = LinearLayoutManager(requireContext())
        b.rvResults.adapter = adapter

        b.btnBack.setOnClickListener { findNavController().popBackStack() }
        b.btnSearch.setOnClickListener { doSearch() }
    }

    private fun doSearch() {
        val query = b.etSearch.text.toString()
        if (query.isBlank()) { toast("Enter a search query"); return }
        if (!ProjectManager.isProjectOpen()) { toast("Open a project first"); return }

        searchJob?.cancel()
        results.clear(); adapter.notifyDataSetChanged()
        b.searchProgress.visibility = View.VISIBLE
        b.tvPlaceholder.visibility = View.GONE
        b.rvResults.visibility = View.GONE
        b.tvSearchStatus.visibility = View.GONE

        val caseSensitive = b.cbCaseSensitive.isChecked
        val useRegex = b.cbRegex.isChecked
        val wholeWord = b.cbWholeWord.isChecked
        val ctx = requireContext().applicationContext

        searchJob = lifecycleScope.launch(Dispatchers.IO) {
            val found = mutableListOf<SearchResult>()
            for (fileNode in ProjectManager.allFiles) {
                if (!isActive) break
                if (!FileUtils.isTextFile(fileNode.name)) continue
                val content = FileUtils.readFile(fileNode.uri, ProjectManager.isLocalProject, ctx) ?: continue
                found.addAll(searchInContent(content, query, fileNode, caseSensitive, useRegex, wholeWord))
            }
            withContext(Dispatchers.Main) {
                b.searchProgress.visibility = View.GONE
                results.addAll(found)
                adapter.notifyDataSetChanged()
                if (found.isEmpty()) {
                    b.tvPlaceholder.text = "No results found."
                    b.tvPlaceholder.visibility = View.VISIBLE
                } else {
                    val fileCount = found.map { it.relativePath }.distinct().size
                    b.tvSearchStatus.text = "${found.size} result(s) in $fileCount file(s)"
                    b.tvSearchStatus.visibility = View.VISIBLE
                    b.rvResults.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun searchInContent(
        content: String, query: String, node: FileNode,
        caseSensitive: Boolean, useRegex: Boolean, wholeWord: Boolean
    ): List<SearchResult> {
        val out = mutableListOf<SearchResult>()
        val lines = content.lines()
        val pattern = when {
            useRegex -> try {
                val opts = if (caseSensitive) setOf() else setOf(RegexOption.IGNORE_CASE)
                Regex(if (wholeWord) "\\b$query\\b" else query, opts)
            } catch (e: Exception) { return emptyList() }
            wholeWord -> {
                val opts = if (caseSensitive) setOf() else setOf(RegexOption.IGNORE_CASE)
                Regex("\\b${Regex.escape(query)}\\b", opts)
            }
            else -> null
        }

        for ((idx, line) in lines.withIndex()) {
            if (pattern != null) {
                val match = pattern.find(line) ?: continue
                out.add(SearchResult(node.name, node.relativePath, node.uri, idx + 1, line,
                    match.range.first, match.range.last + 1, ProjectManager.isLocalProject))
            } else {
                val searchLine = if (caseSensitive) line else line.lowercase()
                val searchQ = if (caseSensitive) query else query.lowercase()
                var start = 0
                while (true) {
                    val i = searchLine.indexOf(searchQ, start); if (i == -1) break
                    out.add(SearchResult(node.name, node.relativePath, node.uri, idx + 1, line,
                        i, i + query.length, ProjectManager.isLocalProject))
                    start = i + query.length
                }
            }
        }
        return out
    }

    private fun openInEditor(result: SearchResult) {
        vm.openFileRequest.value = FileOpenRequest(
            uri = result.uri, fileName = result.fileName,
            relativePath = result.relativePath,
            lineNumber = result.lineNumber,
            highlightText = b.etSearch.text.toString(),
            isLocal = result.isLocal
        )
        findNavController().popBackStack()
        findNavController().navigate(R.id.toEditor)
    }

    private fun toast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
