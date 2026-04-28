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

    private lateinit var vm: SharedViewModel
    private var _binding: FragmentEditorBinding? = null
    private val binding get() = _binding!!

    private var currentUri: Uri? = null
    private var currentFileName: String = ""
    private var currentLanguage: Language = Language.PLAIN_TEXT
    private var isLocal: Boolean = false
    private var isModified: Boolean = false
    private var originalContent: String = ""

    private val MAX_FILE_SIZE_BYTES: Int = 10 * 1024 * 1024

    private data class EditState(
        val content: String,
        val selectionStart: Int,
        val selectionEnd: Int,
        val scrollX: Int,
        val scrollY: Int
    )
    private val undoStack: ArrayDeque<EditState> = ArrayDeque()
    private val redoStack: ArrayDeque<EditState> = ArrayDeque()

    private var ignoreTextChange: Boolean = false
    private var syntaxHighlightJob: Job? = null
    private var errorHighlightJob: Job? = null

    private var findMatches: List<Int> = emptyList()
    private var findMatchIdx: Int = -1
    private var lastFindQuery: String = ""

    private val colors: Map<String, Int> = mapOf(
        "keyword" to Color.parseColor("#FF79C6"),
        "string" to Color.parseColor("#F1FA8C"),
        "comment" to Color.parseColor("#6272A4"),
        "number" to Color.parseColor("#BD93F9"),
        "function" to Color.parseColor("#50FA7B"),
        "type" to Color.parseColor("#8BE9FD"),
        "error" to Color.parseColor("#FF5555"),
        "bracket_match" to Color.parseColor("#FFB86C")
    )

    private val bracketPairs: Map<Char, Char> = mapOf(
        '(' to ')',
        '[' to ']',
        '{' to '}',
        '<' to '>',
        '"' to '"',
        '\'' to '\'',
        '`' to '`'
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vm = ViewModelProvider(requireActivity())[SharedViewModel::class.java]

        vm.openFileRequest.observe(viewLifecycleOwner) { req ->
            req ?: return@observe
            loadFile(req)
            vm.openFileRequest.value = null
        }

        setupEditor()
        setupActionButtons()
        setupFindBar()
    }

    private fun setupEditor() {
        val b = binding
        b.editCode.typeface = Typeface.MONOSPACE
        b.editCode.textSize = 14f
        b.editCode.lineSpacingMultiplier = 1.3f
        b.editCode.setPadding(48, 16, 16, 16)

        b.editCode.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_TAB && event.action == KeyEvent.ACTION_DOWN) {
                insertTab()
                return@setOnKeyListener true
            }
            if (event.action == KeyEvent.ACTION_DOWN && event.unicodeChar != 0) {
                val char = event.unicodeChar.toChar()
                if (char in bracketPairs.keys) {
                    handleBracketInsert(char)
                    return@setOnKeyListener true
                }
            }
            false
        }

        b.editCode.addTextChangedListener(object : TextWatcher {
            private var beforeText: String = ""
            private var beforeSelectionStart: Int = 0
            private var beforeSelectionEnd: Int = 0

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (!ignoreTextChange && s != null) {
                    beforeText = s.toString()
                    beforeSelectionStart = b.editCode.selectionStart
                    beforeSelectionEnd = b.editCode.selectionEnd
                }
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (!ignoreTextChange) {
                    pushUndoState(beforeText, beforeSelectionStart, beforeSelectionEnd)
                    isModified = s.toString() != originalContent
                    updateModifiedIndicator()
                    applySyntaxHighlightingDebounced()
                    detectErrorsDebounced()
                }
            }
        })
    }

    private fun handleBracketInsert(char: Char) {
        val b = binding
        val start = b.editCode.selectionStart
        val end = b.editCode.selectionEnd
        val text = b.editCode.text.toString()
        val closingChar = bracketPairs[char] ?: return

        if (start != end && start >= 0 && end >= 0 && start <= text.length && end <= text.length) {
            val selectedText = text.substring(start, end)
            val newText = text.substring(0, start) + char + selectedText + closingChar + text.substring(end)
            b.editCode.setText(newText)
            b.editCode.setSelection(start + 1, end + 1)
        } else if (start >= 0 && start <= text.length) {
            val newText = text.substring(0, start) + char + closingChar + text.substring(start)
            b.editCode.setText(newText)
            b.editCode.setSelection(start + 1)
        }
    }

    private fun insertTab() {
        val b = binding
        val start = b.editCode.selectionStart
        val end = b.editCode.selectionEnd
        val text = b.editCode.text.toString()

        if (start >= 0 && end >= 0 && start <= text.length && end <= text.length) {
            val newText = text.substring(0, start) + "    " + text.substring(end)
            b.editCode.setText(newText)
            b.editCode.setSelection(start + 4)
        }
    }

    private fun pushUndoState(content: String, selectionStart: Int, selectionEnd: Int) {
        val b = binding
        undoStack.addLast(
            EditState(
                content = content,
                selectionStart = selectionStart,
                selectionEnd = selectionEnd,
                scrollX = b.scrollEditor.scrollX,
                scrollY = b.scrollEditor.scrollY
            )
        )

        val maxStack = if (originalContent.length > 10000) 500 else 200
        while (undoStack.size > maxStack) {
            undoStack.removeFirst()
        }

        redoStack.clear()
        updateUndoRedoButtons()
    }

    private fun setupActionButtons() {
        val b = binding
        b.btnSave.setOnClickListener { saveFile() }
        b.btnUndo.setOnClickListener { doUndo() }
        b.btnRedo?.setOnClickListener { doRedo() }
        b.btnFind.setOnClickListener { toggleFindBar() }
        b.btnReplace.setOnClickListener { showReplaceDialog() }
        b.btnSnippets?.setOnClickListener { showSnippetsDialog() }
        b.btnSettings?.setOnClickListener { showSettingsDialog() }
    }

    private fun setupFindBar() {
        val b = binding
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
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { doFindInFile() }
        })
    }

    private fun loadFile(req: FileOpenRequest) {
        val b = binding
        currentUri = req.uri
        currentFileName = req.fileName
        isLocal = req.isLocal
        currentLanguage = detectLanguage(req.fileName)

        b.tvFileName.text = req.relativePath
        b.tvLanguage.text = currentLanguage.displayName
        b.tvNoFile.visibility = View.GONE
        b.scrollEditor.visibility = View.VISIBLE
        b.layoutActions.visibility = View.VISIBLE
        b.layoutFindBar.visibility = View.GONE

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

            if (content.length > MAX_FILE_SIZE_BYTES) {
                toast("File too large (>10MB)")
                return@launch
            }

            originalContent = content
            ignoreTextChange = true
            b.editCode.setText(content)
            b.editCode.setSelection(0)
            ignoreTextChange = false

            applySyntaxHighlighting()
            detectErrors()

            undoStack.addLast(EditState(content, 0, 0, 0, 0))

            if (req.lineNumber > 0) scrollToLine(req.lineNumber - 1)

            if (req.highlightText.isNotEmpty()) {
                b.etFindInFile.setText(req.highlightText)
                b.layoutFindBar.visibility = View.VISIBLE
                doFindInFile()
            }
        }
    }

    private fun detectLanguage(fileName: String): Language {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return Language.values().find { ext in it.extensions } ?: Language.PLAIN_TEXT
    }

    private fun saveFile() {
        val b = binding
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
        val b = binding
        if (undoStack.size <= 1) {
            toast("Nothing to undo")
            return
        }

        val currentContent = b.editCode.text.toString()
        val currentSelection = EditState(
            currentContent,
            b.editCode.selectionStart,
            b.editCode.selectionEnd,
            b.scrollEditor.scrollX,
            b.scrollEditor.scrollY
        )

        undoStack.removeLast()
        if (undoStack.isEmpty()) {
            undoStack.addLast(currentSelection)
            toast("Nothing to undo")
            return
        }

        val prev = undoStack.removeLast()
        redoStack.addLast(currentSelection)

        ignoreTextChange = true
        b.editCode.setText(prev.content)
        b.editCode.setSelection(
            prev.selectionStart.coerceIn(0, prev.content.length),
            prev.selectionEnd.coerceIn(0, prev.content.length)
        )
        b.scrollEditor.post {
            b.scrollEditor.scrollTo(prev.scrollX, prev.scrollY)
        }
        ignoreTextChange = false

        isModified = prev.content != originalContent
        updateModifiedIndicator()
        updateUndoRedoButtons()
        applySyntaxHighlighting()

        toast("Undo")
    }

    private fun doRedo() {
        val b = binding
        if (redoStack.isEmpty()) {
            toast("Nothing to redo")
            return
        }

        val currentContent = b.editCode.text.toString()
        val currentSelection = EditState(
            currentContent,
            b.editCode.selectionStart,
            b.editCode.selectionEnd,
            b.scrollEditor.scrollX,
            b.scrollEditor.scrollY
        )

        val next = redoStack.removeLast()
        undoStack.addLast(currentSelection)

        ignoreTextChange = true
        b.editCode.setText(next.content)
        b.editCode.setSelection(
            next.selectionStart.coerceIn(0, next.content.length),
            next.selectionEnd.coerceIn(0, next.content.length)
        )
        b.scrollEditor.post {
            b.scrollEditor.scrollTo(next.scrollX, next.scrollY)
        }
        ignoreTextChange = false

        isModified = next.content != originalContent
        updateModifiedIndicator()
        updateUndoRedoButtons()
        applySyntaxHighlighting()

        toast("Redo")
    }

    private fun updateUndoRedoButtons() {
        val b = binding
        b.btnUndo.isEnabled = undoStack.size > 1
        b.btnRedo?.isEnabled = redoStack.isNotEmpty()
    }

    private fun applySyntaxHighlightingDebounced() {
        syntaxHighlightJob?.cancel()
        syntaxHighlightJob = lifecycleScope.launch {
            delay(150)
            if (!ignoreTextChange) applySyntaxHighlighting()
        }
    }

    private fun applySyntaxHighlighting() {
        val b = binding
        val text = b.editCode.text.toString()
        if (text.isEmpty()) return

        val cursorStart = b.editCode.selectionStart
        val cursorEnd = b.editCode.selectionEnd
        val scrollX = b.scrollEditor.scrollX
        val scrollY = b.scrollEditor.scrollY

        val spannable = SpannableString(text)

        currentLanguage.keywords.forEach { keyword ->
            var idx = 0
            while (idx < text.length) {
                val start = text.indexOf(keyword, idx)
                if (start == -1) break
                val end = start + keyword.length
                val isWholeWord = (start == 0 || !text[start - 1].isLetterOrDigit()) &&
                        (end >= text.length || !text[end].isLetterOrDigit())
                if (isWholeWord) {
                    spannable.setSpan(
                        ForegroundColorSpan(colors["keyword"] ?: Color.WHITE),
                        start,
                        end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                idx = end
            }
        }

        var inString = false
        var stringStart = -1
        var escaped = false
        for (i in text.indices) {
            if (escaped) { escaped = false; continue }
            if (text[i] == '\\' && inString) { escaped = true; continue }
            if (text[i] == '"') {
                if (!inString) { inString = true; stringStart = i }
                else {
                    spannable.setSpan(
                        ForegroundColorSpan(colors["string"] ?: Color.YELLOW),
                        stringStart, i + 1,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    inString = false
                }
            }
        }

        val lines = text.split("\n")
        var charOffset = 0
        for (line in lines) {
            val commentIdx = line.indexOf("//")
            if (commentIdx != -1) {
                val start = charOffset + commentIdx
                val end = charOffset + line.length
                if (start < spannable.length && end <= spannable.length) {
                    spannable.setSpan(
                        ForegroundColorSpan(colors["comment"] ?: Color.GRAY),
                        start, end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
            charOffset += line.length + 1
        }

        val numberRegex = Regex("\\b\\d+(\\.\\d+)?[fFdD]?\\b")
        var match = numberRegex.find(text)
        while (match != null) {
            spannable.setSpan(
                ForegroundColorSpan(colors["number"] ?: Color.CYAN),
                match.range.first, match.range.last + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            match = numberRegex.find(text, match.range.last + 1)
        }

        ignoreTextChange = true
        b.editCode.setText(spannable)
        b.editCode.setSelection(
            cursorStart.coerceIn(0, text.length),
            cursorEnd.coerceIn(0, text.length)
        )
        b.scrollEditor.scrollTo(scrollX, scrollY)
        ignoreTextChange = false
    }

    private fun detectErrorsDebounced() {
        errorHighlightJob?.cancel()
        errorHighlightJob = lifecycleScope.launch {
            delay(500)
            if (!ignoreTextChange) detectErrors()
        }
    }

    private fun detectErrors() {
        val b = binding
        val text = b.editCode.text.toString()
        val spannable = SpannableString(text)

        val brackets = listOf('(' to ')', '[' to ']', '{' to '}')
        val stack = mutableListOf<Pair<Char, Int>>()

        for ((i, char) in text.withIndex()) {
            brackets.find { it.first == char }?.let { stack.add(it.first to i) }
            brackets.find { it.second == char }?.let {
                if (stack.isEmpty() || stack.last().first != it.first) {
                    spannable.setSpan(UnderlineSpan(), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    spannable.setSpan(BackgroundColorSpan(colors["error"] ?: Color.RED), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                } else {
                    stack.removeLast()
                }
            }
        }

        for ((char, pos) in stack) {
            spannable.setSpan(UnderlineSpan(), pos, pos + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        ignoreTextChange = true
        b.editCode.setText(spannable)
        ignoreTextChange = false
    }

    private fun toggleFindBar() {
        val b = binding
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
        val b = binding
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
        val b = binding
        if (findMatches.isEmpty()) return
        findMatchIdx = (findMatchIdx + dir + findMatches.size) % findMatches.size
        b.tvFindCount.text = "${findMatchIdx + 1}/${findMatches.size}"
        scrollToChar(findMatches[findMatchIdx])
    }

    private fun highlightAllMatches(query: String) {
        val b = binding
        val text = b.editCode.text.toString()
        val spannable = SpannableString(text)
        val highlightColor = requireContext().getColor(R.color.match_highlight)

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

    private fun clearHighlights() {
        findMatches = emptyList()
        findMatchIdx = -1
        lastFindQuery = ""
        applySyntaxHighlighting()
    }

    private fun scrollToLine(lineIndex: Int) {
        val b = binding
        b.editCode.post {
            val layout = b.editCode.layout ?: return@post
            if (lineIndex < layout.lineCount) {
                val top = layout.getLineTop(lineIndex)
                b.scrollEditor.scrollTo(0, (top - 100).coerceAtLeast(0))
            }
        }
    }

    private fun scrollToChar(charIndex: Int) {
        val b = binding
        b.editCode.post {
            val layout = b.editCode.layout ?: return@post
            val line = layout.getLineForOffset(charIndex.coerceIn(0, b.editCode.text.length))
            val top = layout.getLineTop(line)
            b.scrollEditor.scrollTo(0, (top - 100).coerceAtLeast(0))
        }
    }

    private fun showSnippetsDialog() {
        val snippets = currentLanguage.snippets
        if (snippets.isEmpty()) { toast("No snippets for this language"); return }

        val items = snippets.map { s ->
            val firstLine = s.lines().firstOrNull()?.take(30) ?: "Snippet"
            if (s.lines().size > 1) "$firstLine..." else firstLine
        }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle("📋 Code Snippets")
            .setItems(items) { _, which -> insertSnippet(snippets[which]) }
            .show()
    }

    private fun insertSnippet(snippet: String) {
        val b = binding
        val start = b.editCode.selectionStart
        val text = b.editCode.text.toString()
        val newText = text.substring(0, start) + snippet + text.substring(start)

        ignoreTextChange = true
        b.editCode.setText(newText)
        b.editCode.setSelection(start + snippet.length)
        ignoreTextChange = false
        pushUndoState(text, start, start)
    }

    private fun showSettingsDialog() {
        val items = arrayOf("Font Size", "Tab Size", "Theme", "About")
        AlertDialog.Builder(requireContext())
            .setTitle("⚙️ Settings")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showFontSizeDialog()
                    1 -> showTabSizeDialog()
                    2 -> showThemeDialog()
                    3 -> showAboutDialog()
                }
            }
            .show()
    }

    private fun showFontSizeDialog() {
        val b = binding
        val sizes = arrayOf("12sp", "14sp", "16sp", "18sp", "20sp")
        AlertDialog.Builder(requireContext())
            .setTitle("Font Size")
            .setItems(sizes) { _, which ->
                b.editCode.textSize = when (which) {
                    0 -> 12f; 1 -> 14f; 2 -> 16f; 3 -> 18f; else -> 20f
                }
            }
            .show()
    }

    private fun showTabSizeDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Tab Size")
            .setItems(arrayOf("2 spaces", "4 spaces", "8 spaces")) { _, _ ->
                toast("Tab size setting (to be implemented)")
            }
            .show()
    }

    private fun showThemeDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Theme")
            .setItems(arrayOf("Dracula (Dark)", "Light", "Monokai")) { _, _ ->
                toast("Theme switching (to be implemented)")
            }
            .show()
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("About CodeSnap")
            .setMessage("Professional Code Editor for Android\n\nVersion 1.0.0")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showReplaceDialog() {
        val dialogBinding = DialogReplaceBinding.inflate(layoutInflater)
        val b = binding
        if (b.layoutFindBar.visibility == View.VISIBLE) {
            dialogBinding.etFindText.setText(b.etFindInFile.text)
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()
        dialog.window?.setBackgroundDrawableResource(R.color.bg_card)

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
            val count = text.countMatches(find)
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

    private fun String.countMatches(find: String): Int {
        if (find.isEmpty()) return 0
        var count = 0
        var idx = 0
        while (true) {
            val i = indexOf(find, idx, ignoreCase = true)
            if (i == -1) break
            count++
            idx = i + find.length
        }
        return count
    }

    private fun updateModifiedIndicator() {
        val b = binding
        b.tvModified.visibility = if (isModified) View.VISIBLE else View.GONE
    }

    private fun toast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        syntaxHighlightJob?.cancel()
        errorHighlightJob?.cancel()
        super.onDestroyView()
        _binding = null
    }
}
