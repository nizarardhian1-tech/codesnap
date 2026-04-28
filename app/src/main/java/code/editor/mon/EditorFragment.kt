package code.editor.mon

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.*
import android.text.style.*
import android.util.Log
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import code.editor.mon.databinding.DialogReplaceBinding
import code.editor.mon.databinding.FragmentEditorBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    // MAX FILE SIZE: 5MB untuk mencegah OOM
    private val MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024

    // Undo/Redo stack - SIMPAN STATE SETIAP PERUBAHAN KECIL
    private data class EditState(
        val content: String, 
        val selectionStart: Int, 
        val selectionEnd: Int,
        val scrollX: Int = 0,
        val scrollY: Int = 0
    )
    private val undoStack = ArrayDeque<EditState>()
    private val redoStack = ArrayDeque<EditState>()
    
    // Flag untuk ignore text change saat undo/redo
    private var ignoreTextChange = false
    
    // Debounce untuk syntax highlighting (agar tidak lag saat mengetik)
    private var syntaxHighlightJob: Job? = null

    // Find-in-file state
    private var findMatches: List<Int> = emptyList()
    private var findMatchIdx = -1
    private var lastFindQuery = ""

    // Syntax highlighting keywords (Kotlin/Java basic)
    private val keywords = setOf(
        "fun", "val", "var", "class", "object", "interface", "enum", "data",
        "if", "else", "when", "for", "while", "do", "try", "catch", "finally",
        "return", "break", "continue", "throw", "in", "is", "as", "null",
        "true", "false", "override", "public", "private", "protected", "internal",
        "import", "package", "companion", "sealed", "abstract", "final", "open",
        "const", "lateinit", "suspend", "coroutine", "async", "await", "let",
        "run", "apply", "also", "takeIf", "takeUnless", "repeat", "unit", "int",
        "string", "boolean", "long", "double", "float", "char", "byte", "short",
        "array", "list", "map", "set", "collection", "sequence", "flow"
    )

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

        // Setup EditText dengan line numbers dan syntax highlighting
        setupEditor()

        // Action buttons
        b.btnSave.setOnClickListener { saveFile() }
        b.btnUndo.setOnClickListener { doUndo() }
        b.btnRedo?.setOnClickListener { doRedo() }
        b.btnFind.setOnClickListener { toggleFindBar() }
        b.btnReplace.setOnClickListener { showReplaceDialog() }

        // Find bar
        b.btnFindNext.setOnClickListener { navigateFind(+1) }
        b.btnFindPrev.setOnClickListener { navigateFind(-1) }
        b.btnCloseFindBar.setOnClickListener { 
            b.layoutFindBar.visibility = View.GONE
            clearHighlights() 
        }
        b.etFindInFile.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { 
                doFindInFile()
                true 
            } else false
        }
        b.etFindInFile.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { 
                doFindInFile() 
            }
        })
    }

    private fun setupEditor() {
        // Set monospace font
        b.editCode.typeface = Typeface.MONOSPACE
        
        // Set tab size (4 spaces)
        b.editCode.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_TAB && event.action == KeyEvent.ACTION_DOWN) {
                val start = b.editCode.selectionStart
                val end = b.editCode.selectionEnd
                if (start >= 0 && end >= 0 && start <= b.editCode.text?.length ?: 0) {
                    b.editCode.text?.replace(
                        start.coerceIn(0, b.editCode.text?.length ?: 0), 
                        end.coerceIn(0, b.editCode.text?.length ?: 0), 
                        "    "
                    )
                    b.editCode.setSelection((start + 4).coerceAtMost(b.editCode.text?.length ?: 0))
                }
                return@setOnKeyListener true
            }
            false
        }

        // Text change listener untuk undo tracking DAN syntax highlighting
        b.editCode.addTextChangedListener(object : TextWatcher {
            private var beforeText: CharSequence = ""
            private var beforeSelectionStart = 0
            private var beforeSelectionEnd = 0
            
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (!ignoreTextChange && s != null) {
                    // Capture state BEFORE change untuk undo
                    beforeText = s.toString()
                    beforeSelectionStart = b.editCode.selectionStart
                    beforeSelectionEnd = b.editCode.selectionEnd
                }
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Update line numbers jika ada
                updateLineNumbers()
            }

            override fun afterTextChanged(s: Editable?) {
                if (!ignoreTextChange) {
                    // PUSK UNDO STATE - setiap perubahan kecil (per karakter)
                    // Ini yang bikin undo bekerja seperti VS Code
                    if (beforeText.isNotEmpty() || s?.isNotEmpty() == true) {
                        undoStack.addLast(EditState(
                            content = beforeText.toString(),
                            selectionStart = beforeSelectionStart,
                            selectionEnd = beforeSelectionEnd,
                            scrollX = b.scrollEditor.scrollX,
                            scrollY = b.scrollEditor.scrollY
                        ))
                        
                        // Limit stack size (500 state untuk file besar)
                        val maxStack = if (originalContent.length > 10000) 500 else 200
                        if (undoStack.size > maxStack) {
                            // Remove oldest states
                            val toRemove = undoStack.size - maxStack
                            repeat(toRemove) { if (undoStack.isNotEmpty()) undoStack.removeFirst() }
                        }
                        
                        redoStack.clear() // Clear redo stack on new edit
                        updateUndoRedoButtons()
                    }
                    
                    // Check modification
                    val modified = s.toString() != originalContent
                    if (modified != isModified) {
                        isModified = modified
                        updateModifiedIndicator()
                    }
                    
                    // Apply syntax highlighting dengan debounce (agar tidak lag)
                    applySyntaxHighlightingDebounced()
                }
            }
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
        
        // Clear stacks
        undoStack.clear()
        redoStack.clear()
        isModified = false
        updateModifiedIndicator()
        updateUndoRedoButtons()

        lifecycleScope.launch {
            val content = withContext(Dispatchers.IO) {
                FileUtils.readFile(req.uri, req.isLocal, requireContext().applicationContext)
            }

            if (content == null) {
                toast("Failed to load file")
                Log.e("EditorFragment", "Failed to read file: ${req.uri}")
                return@launch
            }

            // Check file size limit
            if (content.length > MAX_FILE_SIZE_BYTES) {
                toast("File too large (>5MB)")
                Log.w("EditorFragment", "File exceeds size limit: ${content.length} bytes")
                return@launch
            }

            originalContent = content
            ignoreTextChange = true
            b.editCode.setText(content)
            b.editCode.setSelection(0)
            ignoreTextChange = false
            
            // Apply initial syntax highlighting
            applySyntaxHighlighting()
            updateLineNumbers()

            // Save initial state untuk undo (jadi bisa undo ke state awal file)
            undoStack.addLast(EditState(
                content = content,
                selectionStart = 0,
                selectionEnd = 0,
                scrollX = b.scrollEditor.scrollX,
                scrollY = b.scrollEditor.scrollY
            ))

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
                // Clear undo stack setelah save (opsional - bisa juga dipertahankan)
                // undoStack.clear()
                // redoStack.clear()
                updateUndoRedoButtons()
                toast("✓ Saved")
            } else {
                toast("Save failed!")
                Log.e("EditorFragment", "Failed to write file: $uri")
            }
        }
    }

    private fun doUndo() {
        if (undoStack.isEmpty() || undoStack.size <= 1) {
            toast("Nothing to undo")
            return
        }
        
        // Get current state untuk redo
        val currentContent = b.editCode.text.toString()
        val currentSelection = EditState(
            content = currentContent,
            selectionStart = b.editCode.selectionStart,
            selectionEnd = b.editCode.selectionEnd,
            scrollX = b.scrollEditor.scrollX,
            scrollY = b.scrollEditor.scrollY
        )
        
        // Pop last state (current), then pop previous state (to undo to)
        undoStack.removeLast() // Remove current
        
        if (undoStack.isEmpty()) {
            toast("Nothing to undo")
            // Push current back
            undoStack.addLast(currentSelection)
            return
        }
        
        val prev = undoStack.removeLast() // Get state to undo to
        
        // Push current state to redo stack
        redoStack.addLast(currentSelection)
        
        // Apply previous state
        ignoreTextChange = true
        b.editCode.setText(prev.content)
        
        // Restore cursor position
        val cursorPos = prev.selectionStart.coerceIn(0, prev.content.length)
        b.editCode.setSelection(cursorPos, prev.selectionEnd.coerceIn(0, prev.content.length))
        
        // Restore scroll position - INI PENTING! Tidak jump ke atas
        b.scrollEditor.post {
            b.scrollEditor.scrollTo(prev.scrollX, prev.scrollY)
        }
        
        ignoreTextChange = false
        
        isModified = prev.content != originalContent
        updateModifiedIndicator()
        updateUndoRedoButtons()
        
        // Apply syntax highlighting setelah undo
        applySyntaxHighlighting()
        
        toast("Undo")
    }

    private fun doRedo() {
        if (redoStack.isEmpty()) {
            toast("Nothing to redo")
            return
        }
        
        // Get current state untuk undo
        val currentContent = b.editCode.text.toString()
        val currentSelection = EditState(
            content = currentContent,
            selectionStart = b.editCode.selectionStart,
            selectionEnd = b.editCode.selectionEnd,
            scrollX = b.scrollEditor.scrollX,
            scrollY = b.scrollEditor.scrollY
        )
        
        val next = redoStack.removeLast()
        
        // Push current state to undo stack
        undoStack.addLast(currentSelection)
        
        // Apply next state
        ignoreTextChange = true
        b.editCode.setText(next.content)
        
        // Restore cursor position
        val cursorPos = next.selectionStart.coerceIn(0, next.content.length)
        b.editCode.setSelection(cursorPos, next.selectionEnd.coerceIn(0, next.content.length))
        
        // Restore scroll position
        b.scrollEditor.post {
            b.scrollEditor.scrollTo(next.scrollX, next.scrollY)
        }
        
        ignoreTextChange = false
        
        isModified = next.content != originalContent
        updateModifiedIndicator()
        updateUndoRedoButtons()
        
        // Apply syntax highlighting setelah redo
        applySyntaxHighlighting()
        
        toast("Redo")
    }

    private fun updateUndoRedoButtons() {
        b.btnUndo.isEnabled = undoStack.size > 1 // Need at least 2 states to undo
        b.btnRedo?.isEnabled = redoStack.isNotEmpty()
    }

    private fun updateLineNumbers() {
        // Jika ada line number view di layout, update di sini
        // Contoh: b.lineNumbers.text = buildLineNumbers()
        val lineCount = b.editCode.text?.lines()?.size ?: 1
        // Implementasi line numbers tergantung layout Anda
    }

    private fun applySyntaxHighlightingDebounced() {
        syntaxHighlightJob?.cancel()
        syntaxHighlightJob = lifecycleScope.launch {
            delay(500) // Wait 500ms after typing stops
            if (!ignoreTextChange) {
                applySyntaxHighlighting()
            }
        }
    }

    private fun applySyntaxHighlighting() {
        val text = b.editCode.text.toString()
        if (text.isEmpty()) return
        
        // Simpan cursor position sebelum apply highlighting
        val cursorStart = b.editCode.selectionStart
        val cursorEnd = b.editCode.selectionEnd
        val scrollX = b.scrollEditor.scrollX
        val scrollY = b.scrollEditor.scrollY
        
        val spannable = SpannableString(text)
        val keywordColor = Color.parseColor("#FF79C6") // Pink untuk keywords
        val stringColor = Color.parseColor("#F1FA8C") // Yellow untuk strings
        val commentColor = Color.parseColor("#6272A4") // Gray untuk comments
        val numberColor = Color.parseColor("#BD93F9") // Purple untuk numbers
        
        // Highlight keywords
        for (keyword in keywords) {
            var idx = 0
            while (idx < text.length) {
                val start = text.indexOf(keyword, idx)
                if (start == -1) break
                
                val end = start + keyword.length
                
                // Check if it's a whole word (not part of identifier)
                val isWholeWord = (start == 0 || !text[start - 1].isLetterOrDigit()) &&
                                  (end >= text.length || !text[end].isLetterOrDigit())
                
                if (isWholeWord) {
                    spannable.setSpan(
                        ForegroundColorSpan(keywordColor),
                        start, end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                idx = end
            }
        }
        
        // Highlight strings (between quotes)
        var inString = false
        var stringStart = -1
        var escaped = false
        for (i in text.indices) {
            if (escaped) {
                escaped = false
                continue
            }
            if (text[i] == '\\' && inString) {
                escaped = true
                continue
            }
            if (text[i] == '"') {
                if (!inString) {
                    inString = true
                    stringStart = i
                } else {
                    spannable.setSpan(
                        ForegroundColorSpan(stringColor),
                        stringStart, i + 1,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    inString = false
                }
            }
        }
        
        // Highlight comments (// style)
        val lines = text.split("\n")
        var charOffset = 0
        for (line in lines) {
            val commentIdx = line.indexOf("//")
            if (commentIdx != -1) {
                val start = charOffset + commentIdx
                val end = charOffset + line.length
                if (start < spannable.length && end <= spannable.length) {
                    spannable.setSpan(
                        ForegroundColorSpan(commentColor),
                        start, end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
            charOffset += line.length + 1
        }
        
        // Highlight numbers
        val numberRegex = Regex("\\b\\d+(\\.\\d+)?[fFdD]?\\b")
        var match = numberRegex.find(text)
        while (match != null) {
            spannable.setSpan(
                ForegroundColorSpan(numberColor),
                match.range.first, match.range.last + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            match = numberRegex.find(text, match.range.last + 1)
        }
        
        // Apply spannable WITHOUT losing cursor position
        ignoreTextChange = true
        b.editCode.setText(spannable)
        
        // Restore cursor position
        b.editCode.setSelection(
            cursorStart.coerceIn(0, text.length),
            cursorEnd.coerceIn(0, text.length)
        )
        
        // Restore scroll position
        b.scrollEditor.scrollTo(scrollX, scrollY)
        
        ignoreTextChange = false
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
        if (query.isEmpty()) { 
            clearHighlights()
            b.tvFindCount.text = ""
            return 
        }
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
        val text = b.editCode.text.toString()
        val spannable = SpannableString(text)
        val highlightColor = requireContext().getColor(R.color.match_highlight)
        
        // First apply syntax highlighting
        applySyntaxHighlightingToSpannable(spannable)
        
        // Then apply find highlights on top
        for (start in findMatches) {
            spannable.setSpan(
                BackgroundColorSpan(highlightColor),
                start, (start + query.length).coerceAtMost(spannable.length),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        
        ignoreTextChange = true
        b.editCode.setText(spannable)
        ignoreTextChange = false
    }

    private fun applySyntaxHighlightingToSpannable(spannable: SpannableString) {
        val text = spannable.toString()
        val keywordColor = Color.parseColor("#FF79C6")
        
        for (keyword in keywords) {
            var idx = 0
            while (idx < text.length) {
                val start = text.indexOf(keyword, idx)
                if (start == -1) break
                val end = start + keyword.length
                val isWholeWord = (start == 0 || !text[start - 1].isLetterOrDigit()) &&
                                  (end >= text.length || !text[end].isLetterOrDigit())
                if (isWholeWord) {
                    spannable.setSpan(
                        ForegroundColorSpan(keywordColor),
                        start, end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                idx = end
            }
        }
    }

    private fun clearHighlights() {
        findMatches = emptyList()
        findMatchIdx = -1
        lastFindQuery = ""
        val text = b.editCode.text.toString()
        ignoreTextChange = true
        b.editCode.setText(text)
        ignoreTextChange = false
        applySyntaxHighlighting()
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
            
            val newText = text.substring(0, idx) + replace + text.substring(idx + find.length)
            ignoreTextChange = true
            b.editCode.setText(newText)
            b.editCode.setSelection(idx + replace.length)
            ignoreTextChange = false
            isModified = newText != originalContent
            updateModifiedIndicator()
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
            
            val newText = text.replace(find, replace, ignoreCase = true)
            ignoreTextChange = true
            b.editCode.setText(newText)
            ignoreTextChange = false
            isModified = newText != originalContent
            updateModifiedIndicator()
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

    override fun onDestroyView() { 
        syntaxHighlightJob?.cancel()
        super.onDestroyView()
        _b = null 
    }
}
