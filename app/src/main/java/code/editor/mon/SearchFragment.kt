package code.editor.mon

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import code.editor.mon.databinding.DialogReplaceBinding
import code.editor.mon.databinding.FragmentSearchBinding
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class SearchFragment : Fragment() {

    private var _b: FragmentSearchBinding? = null
    private val b get() = _b!!
    private lateinit var vm: SharedViewModel
    private val results = mutableListOf<SearchResult>()
    private lateinit var adapter: SearchResultAdapter
    private var searchJob: Job? = null
    private var filesProcessed = 0
    private var totalFiles = 0

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentSearchBinding.inflate(i, c, false).also { _b = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vm = ViewModelProvider(requireActivity())[SharedViewModel::class.java]

        adapter = SearchResultAdapter(results,
            onClick = { result -> openInEditor(result) },
            onLongClick = { result -> showReplaceDialog(result) }
        )
        b.rvResults.layoutManager = LinearLayoutManager(requireContext())
        b.rvResults.adapter = adapter

        b.btnSearch.setOnClickListener { doSearch() }

        b.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { doSearch(); true } else false
        }
    }

    private fun doSearch() {
        val query = b.etSearch.text.toString()
        if (query.isBlank()) { toast("Enter a search query"); return }
        if (!ProjectManager.isProjectOpen()) { toast("Open a project first"); return }

        searchJob?.cancel()
        results.clear()
        adapter.notifyDataSetChanged()
        b.searchProgress.visibility = View.VISIBLE
        b.tvSearchPlaceholder.visibility = View.GONE
        b.rvResults.visibility = View.GONE
        b.tvSearchStatus.visibility = View.GONE
        filesProcessed = 0

        val caseSensitive = b.cbCaseSensitive.isChecked
        val useRegex = b.cbRegex.isChecked
        val allFiles = ProjectManager.allFiles.filter { FileUtils.isTextFile(it.name) }
        val isLocal = ProjectManager.isLocalProject
        val ctx = requireContext().applicationContext
        totalFiles = allFiles.size

        searchJob = lifecycleScope.launch(Dispatchers.Main) {
            val found = mutableListOf<SearchResult>()
            
            // Parallel processing untuk performa lebih baik
            allFiles.chunked(10).forEach { fileBatch ->
                if (!isActive) return@launch
                
                withContext(Dispatchers.Default) {
                    fileBatch.forEach { fileNode ->
                        if (!isActive) return@forEach
                        try {
                            val content = FileUtils.readFile(fileNode.uri, isLocal, ctx) ?: return@forEach
                            val lineResults = searchInContent(content, query, fileNode, caseSensitive, useRegex)
                            synchronized(found) {
                                found.addAll(lineResults)
                            }
                        } catch (e: Exception) {
                            Log.e("SearchFragment", "Error searching in ${fileNode.name}", e)
                        }
                        filesProcessed++
                    }
                }
                
                // Update progress di main thread
                withContext(Dispatchers.Main) {
                    b.tvSearchStatus.text = "Processing: $filesProcessed/$totalFiles files..."
                    b.tvSearchStatus.visibility = View.VISIBLE
                }
            }

            withContext(Dispatchers.Main) {
                b.searchProgress.visibility = View.GONE
                results.addAll(found)
                adapter.notifyDataSetChanged()

                if (found.isEmpty()) {
                    b.tvSearchPlaceholder.text = "No results found for the given query."
                    b.tvSearchPlaceholder.visibility = View.VISIBLE
                    b.rvResults.visibility = View.GONE
                } else {
                    b.tvSearchStatus.text = "${found.size} result(s) in ${found.map { it.relativePath }.distinct().size} file(s)"
                    b.tvSearchStatus.visibility = View.VISIBLE
                    b.rvResults.visibility = View.VISIBLE
                    b.tvSearchPlaceholder.visibility = View.GONE
                }
            }
        }
    }

    private fun searchInContent(
        content: String, query: String, node: FileNode,
        caseSensitive: Boolean, useRegex: Boolean
    ): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        val lines = content.lines()
        val regex = if (useRegex) {
            try {
                Regex(query, if (caseSensitive) setOf() else setOf(RegexOption.IGNORE_CASE))
            } catch (e: Exception) {
                Log.w("SearchFragment", "Invalid regex: $query", e)
                return emptyList()
            }
        } else null

        for ((index, line) in lines.withIndex()) {
            if (useRegex && regex != null) {
                val match = regex.find(line) ?: continue
                results.add(SearchResult(
                    fileName = node.name,
                    relativePath = node.relativePath,
                    uri = node.uri,
                    lineNumber = index + 1,
                    linePreview = line,
                    matchStartInLine = match.range.first,
                    matchEndInLine = match.range.last + 1,
                    isLocal = ProjectManager.isLocalProject
                ))
            } else {
                val searchLine = if (caseSensitive) line else line.lowercase()
                val searchQ = if (caseSensitive) query else query.lowercase()
                var startIdx = 0
                while (true) {
                    val idx = searchLine.indexOf(searchQ, startIdx)
                    if (idx == -1) break
                    results.add(SearchResult(
                        fileName = node.name,
                        relativePath = node.relativePath,
                        uri = node.uri,
                        lineNumber = index + 1,
                        linePreview = line,
                        matchStartInLine = idx,
                        matchEndInLine = idx + query.length,
                        isLocal = ProjectManager.isLocalProject
                    ))
                    startIdx = idx + query.length
                }
            }
        }
        return results
    }

    private fun openInEditor(result: SearchResult) {
        vm.openFileRequest.value = FileOpenRequest(
            uri = result.uri,
            fileName = result.fileName,
            relativePath = result.relativePath,
            lineNumber = result.lineNumber,
            highlightText = b.etSearch.text.toString(),
            isLocal = result.isLocal
        )
        findNavController().navigate(R.id.editorFragment)
    }

    private fun showReplaceDialog(result: SearchResult) {
        val dialogBinding = DialogReplaceBinding.inflate(layoutInflater)
        dialogBinding.etFindText.setText(b.etSearch.text)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()
        dialog.window?.setBackgroundDrawableResource(R.color.bg_card)

        fun countOccurrences(find: String, content: String, caseSensitive: Boolean): Int {
            if (find.isEmpty()) return 0
            val c = if (caseSensitive) content else content.lowercase()
            val f = if (caseSensitive) find else find.lowercase()
            var count = 0; var idx = 0
            while (true) { val i = c.indexOf(f, idx); if (i == -1) break; count++; idx = i + f.length }
            return count
        }

        dialogBinding.btnReplaceFirst.setOnClickListener {
            val find = dialogBinding.etFindText.text.toString()
            val replace = dialogBinding.etReplaceText.text.toString()
            if (find.isEmpty()) { toast("Enter text to find"); return@setOnClickListener }
            val ctx = requireContext().applicationContext
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val content = FileUtils.readFile(result.uri, result.isLocal, ctx) ?: return@launch
                    val count = countOccurrences(find, content, b.cbCaseSensitive.isChecked)
                    val newContent = if (b.cbCaseSensitive.isChecked)
                        content.replaceFirst(find, replace)
                    else
                        content.replaceFirst(Regex(Regex.escape(find), RegexOption.IGNORE_CASE), replace)
                    val ok = FileUtils.writeFile(result.uri, newContent, result.isLocal, ctx)
                    withContext(Dispatchers.Main) {
                        toast(if (ok) "✓ Replaced 1 occurrence" else "Write failed")
                        dialog.dismiss()
                    }
                } catch (e: Exception) {
                    Log.e("SearchFragment", "Replace failed", e)
                    withContext(Dispatchers.Main) {
                        toast("Replace failed: ${e.message}")
                        dialog.dismiss()
                    }
                }
            }
        }

        dialogBinding.btnReplaceAll.setOnClickListener {
            val find = dialogBinding.etFindText.text.toString()
            val replace = dialogBinding.etReplaceText.text.toString()
            if (find.isEmpty()) { toast("Enter text to find"); return@setOnClickListener }
            val ctx = requireContext().applicationContext
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val content = FileUtils.readFile(result.uri, result.isLocal, ctx) ?: return@launch
                    val count = countOccurrences(find, content, b.cbCaseSensitive.isChecked)
                    val newContent = if (b.cbCaseSensitive.isChecked)
                        content.replace(find, replace)
                    else
                        content.replace(Regex(Regex.escape(find), RegexOption.IGNORE_CASE), replace)
                    val ok = FileUtils.writeFile(result.uri, newContent, result.isLocal, ctx)
                    withContext(Dispatchers.Main) {
                        toast(if (ok) "✓ Replaced $count occurrence(s)" else "Write failed")
                        if (ok) doSearch() // refresh search results
                        dialog.dismiss()
                    }
                } catch (e: Exception) {
                    Log.e("SearchFragment", "Replace all failed", e)
                    withContext(Dispatchers.Main) {
                        toast("Replace failed: ${e.message}")
                        dialog.dismiss()
                    }
                }
            }
        }

        dialog.show()
    }

    private fun toast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

    override fun onDestroyView() {
        searchJob?.cancel()
        super.onDestroyView()
        _b = null
    }
}
