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

    private var _binding: FragmentEditorBinding? = null
    private val binding get() = _binding!!
    private lateinit var vm: SharedViewModel

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

    private val colors = mapOf(
        "keyword" to Color.parseColor("#FF79C6"),
        "string" to Color.parseColor("#F1FA8C"),
        "comment" to Color.parseColor("#6272A4"),
        "number" to Color.parseColor("#BD93F9"),
        "function" to Color.parseColor("#50FA7B"),
        "type" to Color.parseColor("#8BE9FD"),
        "error" to Color.parseColor("#FF5555"),
        "bracket_match" to Color.parseColor("#FFB86C")
    )

    private val bracketPairs = mapOf(
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
        binding.editCode.typeface = Typeface.MONOSPACE
        binding.editCode.textSize = 14f
        binding.editCode.lineSpacingMultiplier = 1.3f
        binding.editCode.setPadding(48, 16, 16, 16)

        binding.editCode.setOnKeyListener { _, keyCode, event ->
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

        binding.editCode.addTextChangedListener(object : TextWatcher {
            private var beforeText: String = ""
            private var beforeSelectionStart: Int = 0
            private var beforeSelectionEnd: Int = 0

            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {
                if (!ignoreTextChange && s != null) {
                    beforeText = s.toString()
                    beforeSelectionStart = binding.editCode.selectionStart
                    beforeSelectionEnd = binding.editCode.selectionEnd
                }
            }

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {}

            override fun afterTextChanged(s: Editable?) {
                if (!ignoreTextChange) {
                    pushUndoState(beforeText, beforeSelectionStart, beforeSelectionEnd)
                    isModified = s.toString() != originalContent
                    updateModifiedIndicator()
                    applySyntaxHighlightingDebounced()
                    detectErrorsDebounced()
                    highlightMatchingBrackets()
                }
            }
        })
    }

    private fun handleBracketInsert(char: Char) {
        val start = binding.editCode.selectionStart
        val end = binding.editCode.selectionEnd
        val text = binding.editCode.text.toString()
        val closingChar = bracketPairs[char] ?: return

        if (start != end && start >= 0 && end >= 0 && start <= text.length && end <= text.length) {
            val selectedText = text.substring(start, end)
            val newText = text.substring(0, start) + char + selectedText + closingChar + text.substring(end)
            binding.editCode.setText(newText)
            binding.editCode.setSelection(start + 1, end + 1)
        } else if (start >= 0 && start <= text.length) {
            val newText = text.substring(0, start) + char + closingChar + text.substring(start)
            binding.editCode.setText(newText)
            binding.editCode.setSelection(start + 1)
        }
    }

    private fun insertTab() {
        val start = binding.editCode.selectionStart
        val end = binding.editCode.selectionEnd
        val text = binding.editCode.text.toString()

        if (start >= 0 && end >= 0 && start <= text.length && end <= text.length) {
            val newText = text.substring(0, start) + "    " + text.substring(end)
            binding.editCode.setText(newText)
            binding.editCode.setSelection(start + 4)
        }
    }

    private fun pushUndoState(content: String, selectionStart: Int, selectionEnd: Int) {
        undoStack.addLast(
            EditState(
                content = content,
                selectionStart = selectionStart,
                selectionEnd = selectionEnd,
                scrollX = binding.scrollEditor.scrollX,
                scrollY = binding.scrollEditor.scrollY
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
        binding.btnSave.setOnClickListener { saveFile() }
        binding.btnUndo.setOnClickListener { doUndo() }
        binding.btnRedo?.setOnClickListener { doRedo() }
        binding.btnFind.setOnClickListener { toggleFindBar() }
        binding.btnReplace.setOnClickListener { showReplaceDialog() }
        binding.btnSnippets?.setOnClickListener { showSnippetsDialog() }
        binding.btnSettings?.setOnClickListener { showSettingsDialog() }
    }

    private fun setupFindBar() {
        binding.btnFindNext.setOnClickListener { navigateFind(+1) }
        binding.btnFindPrev.setOnClickListener { navigateFind(-1) }
        binding.btnCloseFindBar.setOnClickListener {
            binding.layoutFindBar.visibility = View.GONE
            clearHighlights()
        }
        binding.etFindInFile.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                doFindInFile()
                true
            } else false
        }
        binding.etFindInFile.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                doFindInFile()
            }
        })
    }

    private fun loadFile(req: FileOpenRequest) {
        currentUri = req.uri
        currentFileName = req.fileName
        isLocal = req.isLocal
        currentLanguage = detectLanguage(req.fileName)

        binding.tvFileName.text = req.relativePath
        binding.tvLanguage.text = currentLanguage.displayName
        binding.tvNoFile.visibility = View.GONE
        binding.scrollEditor.visibility = View.VISIBLE
        binding.layoutActions.visibility = View.VISIBLE
        binding.layoutFindBar.visibility = View.GONE

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
            binding.editCode.setText(content)
            binding.editCode.setSelection(0)
            ignoreTextChange = false

            applySyntaxHighlighting()
            detectErrors()

            undoStack.addLast(EditState(content, 0, 0, 0, 0))

            if (req.lineNumber > 0) scrollToLine(req.lineNumber - 1)

            if (req.highlightText.isNotEmpty()) {
                binding.etFindInFile.setText(req.highlightText)
                binding.layoutFindBar.visibility = View.VISIBLE
                doFindInFile()
            }
        }
    }

    private fun detectLanguage(fileName: String): Language {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return Language.values().find { ext in it.extensions } ?: Language.PLAIN_TEXT
    }

    private fun saveFile() {
        val uri = currentUri ?: return
        val content = binding.editCode.text.toString()

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
        if (undoStack.size <= 1) {
            toast("Nothing to undo")
            return
        }

        val currentContent = binding.editCode.text.toString()
        val currentSelection = EditState(
            currentContent,
            binding.editCode.selectionStart,
            binding.editCode.selectionEnd,
            binding.scrollEditor.scrollX,
            binding.scrollEditor.scrollY
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
        binding.editCode.setText(prev.content)
        binding.editCode.setSelection(
            prev.selectionStart.coerceIn(0, prev.content.length),
            prev.selectionEnd.coerceIn(0, prev.content.length)
        )
        binding.scrollEditor.post {
            binding.scrollEditor.scrollTo(prev.scrollX, prev.scrollY)
        }
        ignoreTextChange = false

        isModified = prev.content != originalContent
        updateModifiedIndicator()
        updateUndoRedoButtons()
        applySyntaxHighlighting()

        toast("Undo")
    }

    private fun doRedo() {
        if (redoStack.isEmpty()) {
            toast("Nothing to redo")
            return
        }

        val currentContent = binding.editCode.text.toString()
        val currentSelection = EditState(
            currentContent,
            binding.editCode.selectionStart,
            binding.editCode.selectionEnd,
            binding.scrollEditor.scrollX,
            binding.scrollEditor.scrollY
        )

        val next = redoStack.removeLast()
        undoStack.addLast(currentSelection)

        ignoreTextChange = true
        binding.editCode.setText(next.content)
        binding.editCode.setSelection(
            next.selectionStart.coerceIn(0, next.content.length),
            next.selectionEnd.coerceIn(0, next.content.length)
        )
        binding.scrollEditor.post {
            binding.scrollEditor.scrollTo(next.scrollX, next.scrollY)
        }
        ignoreTextChange = false

        isModified = next.content != originalContent
        updateModifiedIndicator()
        updateUndoRedoButtons()
        applySyntaxHighlighting()

        toast("Redo")
    }

    private fun updateUndoRedoButtons() {
        binding.btnUndo.isEnabled = undoStack.size > 1
        binding.btnRedo?.isEnabled = redoStack.isNotEmpty()
    }

    private fun applySyntaxHighlightingDebounced() {
        syntaxHighlightJob?.cancel()
        syntaxHighlightJob = lifecycleScope.launch {
            delay(150)
            if (!ignoreTextChange) applySyntaxHighlighting()
        }
    }

    private fun applySyntaxHighlighting() {
        val text = binding.editCode.text.toString()
        if (text.isEmpty()) return

        val cursorStart = binding.editCode.selectionStart
        val cursorEnd = binding.editCode.selectionEnd
        val scrollX = binding.scrollEditor.scrollX
        val scrollY = binding.scrollEditor.scrollY

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
                        ForegroundColorSpan(colors["string"] ?: Color.YELLOW),
                        stringStart,
                        i + 1,
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
                        start,
                        end,
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
                match.range.first,
                match.range.last + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            match = numberRegex.find(text, match.range.last + 1)
        }

        ignoreTextChange = true
        binding.editCode.setText(spannable)
        binding.editCode.setSelection(
            cursorStart.coerceIn(0, text.length),
            cursorEnd.coerceIn(0, text.length)
        )
        binding.scrollEditor.scrollTo(scrollX, scrollY)
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
        val text = binding.editCode.text.toString()
        val spannable = SpannableString(text)

        val brackets = listOf('(' to ')', '[' to ']', '{' to '}')
        val stack = mutableListOf<Pair<Char, Int>>()

        for ((i, char) in text.withIndex()) {
            brackets.find { it.first == char }?.let {
                stack.add(it.first to i)
            }
            brackets.find { it.second == char }?.let {
                if (stack.isEmpty() || stack.last().first != it.first) {
                    spannable.setSpan(
                        UnderlineSpan(),
                        i,
                        i + 1,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    spannable.setSpan(
                        BackgroundColorSpan(colors["error"] ?: Color.RED),
                        i,
                        i + 1,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                } else {
                    stack.removeLast()
                }
            }
        }

        for ((char, pos) in stack) {
            spannable.setSpan(
                UnderlineSpan(),
                pos,
                pos + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        ignoreTextChange = true
        binding.editCode.setText(spannable)
        ignoreTextChange = false
    }

    private fun highlightMatchingBrackets() {
        val text = binding.editCode.text.toString()
        val cursorPos = binding.editCode.selectionStart

        val brackets = "[]{}()"
        if (cursorPos <= 0 || cursorPos >= text.length) return
        if (text[cursorPos - 1] !in brackets) return

        val bracket = text[cursorPos - 1]
        val matchingBracket = when (bracket) {
            '(' -> ')' to 1
            ')' -> '(' to -1
            '[' -> ']' to 1
            ']' -> '[' to -1
            '{' -> '}' to 1
            '}' -> '{' to -1
            else -> null
        } ?: return

        var depth = 0
        var pos = cursorPos
        val direction = matchingBracket.second
        val target = matchingBracket.first

        while (pos >= 0 && pos < text.length) {
            if (text[pos] == bracket) depth++
            else if (text[pos] == target) {
                depth--
                if (depth == 0) {
                    val spannable = binding.editCode.text as? Spannable ?: return
                    spannable.setSpan(
                        BackgroundColorSpan(colors["bracket_match"] ?: Color.YELLOW),
                        if (direction > 0) cursorPos - 1 else pos,
                        (if (direction > 0) cursorPos else pos + 1).coerceAtMost(text.length),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    break
                }
            }
            pos += direction
        }
    }

    private fun toggleFindBar() {
        if (binding.layoutFindBar.visibility == View.VISIBLE) {
            binding.layoutFindBar.visibility = View.GONE
            clearHighlights()
        } else {
            binding.layoutFindBar.visibility = View.VISIBLE
            binding.etFindInFile.requestFocus()
            if (binding.etFindInFile.text.isNotEmpty()) doFindInFile()
        }
    }

    private fun doFindInFile() {
        val query = binding.etFindInFile.text.toString()
        if (query.isEmpty()) {
            clearHighlights()
            binding.tvFindCount.text = ""
            return
        }
        if (query == lastFindQuery) return
        lastFindQuery = query

        val text = binding.editCode.text.toString()
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
        binding.tvFindCount.text = if (indices.isEmpty()) "0/0" else "${findMatchIdx + 1}/${indices.size}"
        if (findMatchIdx >= 0) scrollToChar(indices[findMatchIdx])
    }

    private fun navigateFind(dir: Int) {
        if (findMatches.isEmpty()) return
        findMatchIdx = (findMatchIdx + dir + findMatches.size) % findMatches.size
        binding.tvFindCount.text = "${findMatchIdx + 1}/${findMatches.size}"
        scrollToChar(findMatches[findMatchIdx])
    }

    private fun highlightAllMatches(query: String) {
        val text = binding.editCode.text.toString()
        val spannable = SpannableString(text)
        val highlightColor = requireContext().getColor(R.color.match_highlight)

        for (start in findMatches) {
            spannable.setSpan(
                BackgroundColorSpan(highlightColor),
                start,
                (start + query.length).coerceAtMost(spannable.length),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        ignoreTextChange = true
        binding.editCode.setText(spannable)
        ignoreTextChange = false
    }

    private fun clearHighlights() {
        findMatches = emptyList()
        findMatchIdx = -1
        lastFindQuery = ""
        applySyntaxHighlighting()
    }

    private fun scrollToLine(lineIndex: Int) {
        binding.editCode.post {
            val layout = binding.editCode.layout ?: return@post
            if (lineIndex < layout.lineCount) {
                val top = layout.getLineTop(lineIndex)
                binding.scrollEditor.scrollTo(0, (top - 100).coerceAtLeast(0))
            }
        }
    }

    private fun scrollToChar(charIndex: Int) {
        binding.editCode.post {
            val layout = binding.editCode.layout ?: return@post
            val line = layout.getLineForOffset(charIndex.coerceIn(0, binding.editCode.text.length))
            val top = layout.getLineTop(line)
            binding.scrollEditor.scrollTo(0, (top - 100).coerceAtLeast(0))
        }
    }

    private fun showSnippetsDialog() {
        val snippets = currentLanguage.snippets
        if (snippets.isEmpty()) {
            toast("No snippets for this language")
            return
        }

        val items = snippets.map { s ->
            val firstLine = s.lines().firstOrNull()?.take(30) ?: "Snippet"
            if (s.lines().size > 1) "$firstLine..." else firstLine
        }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle("📋 Code Snippets")
            .setItems(items) { _, which ->
                insertSnippet(snippets[which])
            }
            .show()
    }

    private fun insertSnippet(snippet: String) {
        val start = binding.editCode.selectionStart
        val text = binding.editCode.text.toString()
        val newText = text.substring(0, start) + snippet + text.substring(start)

        ignoreTextChange = true
        binding.editCode.setText(newText)
        binding.editCode.setSelection(start + snippet.length)
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
        val sizes = arrayOf("12sp", "14sp", "16sp", "18sp", "20sp")
        AlertDialog.Builder(requireContext())
            .setTitle("Font Size")
            .setItems(sizes) { _, which ->
                binding.editCode.textSize = when (which) {
                    0 -> 12f
                    1 -> 14f
                    2 -> 16f
                    3 -> 18f
                    else -> 20f
                }
            }
            .show()
    }

    private fun showTabSizeDialog() {
        val sizes = arrayOf("2 spaces", "4 spaces", "8 spaces")
        AlertDialog.Builder(requireContext())
            .setTitle("Tab Size")
            .setItems(sizes) { _, _ ->
                toast("Tab size setting (to be implemented)")
            }
            .show()
    }

    private fun showThemeDialog() {
        val themes = arrayOf("Dracula (Dark)", "Light", "Monokai")
        AlertDialog.Builder(requireContext())
            .setTitle("Theme")
            .setItems(themes) { _, _ ->
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
        if (binding.layoutFindBar.visibility == View.VISIBLE) {
            dialogBinding.etFindText.setText(binding.etFindInFile.text)
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()
        dialog.window?.setBackgroundDrawableResource(R.color.bg_card)

        dialogBinding.btnReplaceFirst.setOnClickListener {
            val find = dialogBinding.etFindText.text.toString()
            val replace = dialogBinding.etReplaceText.text.toString()
            if (find.isEmpty()) return@setOnClickListener

            val text = binding.editCode.text.toString()
            val idx = text.indexOf(find, ignoreCase = true)
            if (idx == -1) {
                toast("Not found")
                return@setOnClickListener
            }

            val newText = text.substring(0, idx) + replace + text.substring(idx + find.length)
            ignoreTextChange = true
            binding.editCode.setText(newText)
            binding.editCode.setSelection(idx + replace.length)
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

            val text = binding.editCode.text.toString()
            val count = text.countMatches(find)
            if (count == 0) {
                toast("Not found")
                return@setOnClickListener
            }

            val newText = text.replace(find, replace, ignoreCase = true)
            ignoreTextChange = true
            binding.editCode.setText(newText)
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
        binding.tvModified.visibility = if (isModified) View.VISIBLE else View.GONE
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
