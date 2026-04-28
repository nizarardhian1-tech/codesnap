package code.editor.mon

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.text.*
import android.text.style.BackgroundColorSpan
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import code.editor.mon.databinding.DialogReplaceBinding
import code.editor.mon.databinding.FragmentEditorBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditorFragment : Fragment() {

    private var _b: FragmentEditorBinding? = null
    private val b get() = _b!!
    private lateinit var vm: SharedViewModel

    private var currentUri: Uri? = null
    private var currentFileName = ""
    private var isLocal = false
    private var isModified = false
    private var originalContent = ""

    // Simple undo stack (stores states before programmatic replacements)
    private val undoStack = ArrayDeque<String>()
    private var ignoreTextChange = false

    // Find-in-file state
    private var findMatches: List<Int> = emptyList()
    private var findMatchIdx = -1
    private var lastFindQuery = ""

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentEditorBinding.inflate(i, c, false).also { _b = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vm = ViewModelProvider(requireActivity())[SharedViewModel::class.java]

        // Observe file open request from Files or Search tab
        vm.openFileRequest.observe(viewLifecycleOwner) { req ->
            req ?: return@observe
            loadFile(req)
            vm.openFileRequest.value = null
        }

        // Track modifications
        b.editCode.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!ignoreTextChange && currentUri != null) {
                    val modified = s.toString() != originalContent
                    if (modified != isModified) {
                        isModified = modified
                        updateModifiedIndicator()
                    }
                }
            }
        })

        // Action buttons
        b.btnSave.setOnClickListener { saveFile() }
        b.btnUndo.setOnClickListener { doUndo() }
        b.btnFind.setOnClickListener { toggleFindBar() }
        b.btnReplace.setOnClickListener { showReplaceDialog() }

        // Find bar
        b.btnFindNext.setOnClickListener { navigateFind(+1) }
        b.btnFindPrev.setOnClickListener { navigateFind(-1) }
        b.btnCloseFindBar.setOnClickListener { b.layoutFindBar.visibility = View.GONE; clearHighlights() }
        b.etFindInFile.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { doFindInFile(); true } else false
        }
        b.etFindInFile.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, before: Int, count: Int) { doFindInFile() }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun loadFile(req: FileOpenRequest) {
        currentUri = req.uri
        currentFileName = req.fileName
        isLocal = req.isLocal

        b.tvFileName.text = req.relativePath
        b.tvNoFile.visibility = View.GONE
        b.scrollEditor.visibility = View.VISIBLE
        b.layoutActions.visibility = View.VISIBLE
        b.layoutFindBar.visibility = View.GONE
        undoStack.clear()
        isModified = false
        updateModifiedIndicator()

        lifecycleScope.launch {
            val content = withContext(Dispatchers.IO) {
                FileUtils.readFile(req.uri, req.isLocal, requireContext().applicationContext)
            } ?: ""

            originalContent = content
            ignoreTextChange = true
            b.editCode.setText(content)
            ignoreTextChange = false

            // Scroll to line if requested
            if (req.lineNumber > 0) {
                scrollToLine(req.lineNumber - 1)
            }

            // Highlight search text if came from search
            if (req.highlightText.isNotEmpty()) {
                b.etFindInFile.setText(req.highlightText)
                b.layoutFindBar.visibility = View.VISIBLE
                doFindInFile()
                if (req.lineNumber > 0) navigateToMatch(req.lineNumber - 1, req.highlightText)
            }
        }
    }

    private fun saveFile() {
        val uri = currentUri ?: return
        val content = b.editCode.text.toString()
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                FileUtils.writeFile(uri, content, isLocal, requireContext().applicationContext)
            }
            if (ok) {
                originalContent = content
                isModified = false
                updateModifiedIndicator()
                toast("✓ Saved")
            } else {
                toast("Save failed!")
            }
        }
    }

    private fun doUndo() {
        if (undoStack.isNotEmpty()) {
            val prev = undoStack.removeLast()
            ignoreTextChange = true
            b.editCode.setText(prev)
            ignoreTextChange = false
            isModified = prev != originalContent
            updateModifiedIndicator()
            b.btnUndo.isEnabled = undoStack.isNotEmpty()
            toast("Undone")
        } else {
            toast("Nothing to undo")
        }
    }

    private fun pushUndo() {
        val current = b.editCode.text.toString()
        undoStack.addLast(current)
        if (undoStack.size > 30) undoStack.removeFirst()
        b.btnUndo.isEnabled = true
    }

    private fun toggleFindBar() {
        if (b.layoutFindBar.visibility == View.VISIBLE) {
            b.layoutFindBar.visibility = View.GONE
            clearHighlights()
        } else {
            b.layoutFindBar.visibility = View.VISIBLE
            b.etFindInFile.requestFocus()
            if (b.etFindInFile.text.isNotEmpty()) doFindInFile()
        }
    }

    private fun doFindInFile() {
        val query = b.etFindInFile.text.toString()
        if (query.isEmpty()) { clearHighlights(); b.tvFindCount.text = ""; return }
        if (query == lastFindQuery) return
        lastFindQuery = query

        val text = b.editCode.text.toString()
        val indices = mutableListOf<Int>()
        var idx = 0
        while (true) {
            val i = text.indexOf(query, idx, ignoreCase = true)
            if (i == -1) break
            indices.add(i)
            idx = i + query.length
        }
        findMatches = indices
        findMatchIdx = if (indices.isNotEmpty()) 0 else -1

        highlightAllMatches(query)
        b.tvFindCount.text = if (indices.isEmpty()) "0/0" else "${findMatchIdx + 1}/${indices.size}"
        if (findMatchIdx >= 0) scrollToChar(indices[findMatchIdx])
    }

    private fun navigateFind(dir: Int) {
        if (findMatches.isEmpty()) return
        findMatchIdx = (findMatchIdx + dir + findMatches.size) % findMatches.size
        b.tvFindCount.text = "${findMatchIdx + 1}/${findMatches.size}"
        scrollToChar(findMatches[findMatchIdx])
    }

    private fun highlightAllMatches(query: String) {
        val spannable = SpannableString(b.editCode.text.toString())
        val highlightColor = requireContext().getColor(R.color.match_highlight)
        for (start in findMatches) {
            spannable.setSpan(
                BackgroundColorSpan(highlightColor),
                start, (start + query.length).coerceAtMost(spannable.length),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        ignoreTextChange = true
        b.editCode.text = spannable
        ignoreTextChange = false
    }

    private fun clearHighlights() {
        findMatches = emptyList(); findMatchIdx = -1; lastFindQuery = ""
        val text = b.editCode.text.toString()
        ignoreTextChange = true
        b.editCode.setText(text)
        ignoreTextChange = false
    }

    private fun navigateToMatch(lineIndex: Int, query: String) {
        val text = b.editCode.text.toString()
        val lines = text.lines()
        if (lineIndex >= lines.size) return
        var charPos = lines.take(lineIndex).sumOf { it.length + 1 }
        val lineText = lines[lineIndex]
        val matchInLine = lineText.indexOf(query, ignoreCase = true)
        if (matchInLine >= 0) charPos += matchInLine
        val matchIdx = findMatches.indexOf(charPos)
        if (matchIdx >= 0) findMatchIdx = matchIdx
        scrollToChar(charPos)
    }

    private fun scrollToLine(lineIndex: Int) {
        b.editCode.post {
            val layout = b.editCode.layout ?: return@post
            if (lineIndex < layout.lineCount) {
                val top = layout.getLineTop(lineIndex)
                b.scrollEditor.scrollTo(0, (top - 100).coerceAtLeast(0))
            }
        }
    }

    private fun scrollToChar(charIndex: Int) {
        b.editCode.post {
            val layout = b.editCode.layout ?: return@post
            val line = layout.getLineForOffset(charIndex.coerceIn(0, b.editCode.text.length))
            val top = layout.getLineTop(line)
            b.scrollEditor.scrollTo(0, (top - 100).coerceAtLeast(0))
        }
    }

    private fun showReplaceDialog() {
        if (currentUri == null) { toast("Open a file first"); return }
        val dialogBinding = DialogReplaceBinding.inflate(layoutInflater)
        // Pre-fill find text from find bar if open
        if (b.layoutFindBar.visibility == View.VISIBLE) {
            dialogBinding.etFindText.setText(b.etFindInFile.text)
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()
        dialog.window?.setBackgroundDrawableResource(R.color.bg_card)

        fun countMatches(find: String): Int {
            if (find.isEmpty()) return 0
            val text = b.editCode.text.toString()
            var count = 0; var idx = 0
            while (true) {
                val i = text.indexOf(find, idx, ignoreCase = true); if (i == -1) break
                count++; idx = i + find.length
            }
            return count
        }

        dialogBinding.etFindText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val n = countMatches(s.toString())
                dialogBinding.tvReplaceInfo.text = "$n occurrence(s) in this file"
                dialogBinding.tvReplaceInfo.visibility = View.VISIBLE
            }
        })

        dialogBinding.btnReplaceFirst.setOnClickListener {
            val find = dialogBinding.etFindText.text.toString()
            val replace = dialogBinding.etReplaceText.text.toString()
            if (find.isEmpty()) return@setOnClickListener
            val text = b.editCode.text.toString()
            val idx = text.indexOf(find, ignoreCase = true)
            if (idx == -1) { toast("Not found"); return@setOnClickListener }
            pushUndo()
            val newText = text.substring(0, idx) + replace + text.substring(idx + find.length)
            ignoreTextChange = true
            b.editCode.setText(newText)
            ignoreTextChange = false
            isModified = newText != originalContent
            updateModifiedIndicator()
            clearHighlights()
            lastFindQuery = ""
            toast("✓ Replaced 1 occurrence")
            dialog.dismiss()
        }

        dialogBinding.btnReplaceAll.setOnClickListener {
            val find = dialogBinding.etFindText.text.toString()
            val replace = dialogBinding.etReplaceText.text.toString()
            if (find.isEmpty()) return@setOnClickListener
            val text = b.editCode.text.toString()
            val count = countMatches(find)
            if (count == 0) { toast("Not found"); return@setOnClickListener }
            pushUndo()
            val newText = text.replace(find, replace, ignoreCase = true)
            ignoreTextChange = true
            b.editCode.setText(newText)
            ignoreTextChange = false
            isModified = newText != originalContent
            updateModifiedIndicator()
            clearHighlights()
            lastFindQuery = ""
            toast("✓ Replaced $count occurrence(s)")
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun updateModifiedIndicator() {
        b.tvModified.visibility = if (isModified) View.VISIBLE else View.GONE
    }

    private fun toast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
