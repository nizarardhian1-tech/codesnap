package code.editor.mon

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.*
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
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
    private var currentExt = ""
    private var isLocal = false
    private var isModified = false
    private var originalContent = ""

    private val undoStack = UndoStack()
    private var isApplyingUndo = false
    private var isApplyingHighlight = false

    // Debounce for undo snapshot + highlight
    private val handler = Handler(Looper.getMainLooper())
    private val snapshotRunnable = Runnable { takeUndoSnapshot() }
    private val highlightRunnable = Runnable { applyHighlight() }

    // Find state
    private var findMatches: List<Int> = emptyList()
    private var findIdx = -1

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentEditorBinding.inflate(i, c, false).also { _b = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vm = ViewModelProvider(requireActivity())[SharedViewModel::class.java]

        vm.openFileRequest.observe(viewLifecycleOwner) { req ->
            req ?: return@observe
            loadFile(req)
            vm.openFileRequest.value = null
        }

        b.editCode.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isApplyingUndo || isApplyingHighlight) return
                val text = s.toString()
                isModified = text != originalContent
                updateModifiedDot()
                updateLineInfo()
                // Debounce: snapshot every 1s of idle, highlight every 300ms
                handler.removeCallbacks(snapshotRunnable)
                handler.removeCallbacks(highlightRunnable)
                handler.postDelayed(snapshotRunnable, 1000)
                handler.postDelayed(highlightRunnable, 400)
            }
        })

        b.btnBack.setOnClickListener { findNavController().popBackStack() }
        b.btnSave.setOnClickListener { saveFile() }
        b.btnUndo.setOnClickListener { doUndo() }
        b.btnFindToggle.setOnClickListener { toggleFindBar() }

        // Find bar
        b.etFind.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) { doFind() }
            override fun afterTextChanged(s: Editable?) {}
        })
        b.btnFindNext.setOnClickListener { navigateFind(+1) }
        b.btnFindPrev.setOnClickListener { navigateFind(-1) }
        b.btnCloseFind.setOnClickListener { b.layoutFindBar.visibility = View.GONE; reHighlight() }

        // Replace
        b.btnReplaceOne.setOnClickListener { doReplaceOne() }
        b.btnReplaceAll.setOnClickListener { doReplaceAll() }
    }

    private fun loadFile(req: FileOpenRequest) {
        currentUri = req.uri
        currentFileName = req.fileName
        currentExt = req.fileName.substringAfterLast('.', "")
        isLocal = req.isLocal

        b.tvFileName.text = req.relativePath
        b.tvFileExt.text = currentExt.uppercase()
        isModified = false
        updateModifiedDot()
        undoStack.clear()
        b.layoutFindBar.visibility = View.GONE

        lifecycleScope.launch {
            val content = withContext(Dispatchers.IO) {
                FileUtils.readFile(req.uri, req.isLocal, requireContext().applicationContext)
            } ?: ""
            originalContent = content
            undoStack.init(content)

            isApplyingHighlight = true
            val highlighted = withContext(Dispatchers.Default) {
                SyntaxHighlighter.highlight(content, currentExt)
            }
            b.editCode.setText(highlighted)
            isApplyingHighlight = false

            updateLineInfo()

            if (req.lineNumber > 0) scrollToLine(req.lineNumber - 1)
            if (req.highlightText.isNotEmpty()) {
                b.etFind.setText(req.highlightText)
                b.layoutFindBar.visibility = View.VISIBLE
                doFind()
            }
        }
    }

    private fun takeUndoSnapshot() {
        if (currentUri == null) return
        undoStack.push(b.editCode.text.toString(), b.editCode.selectionStart)
    }

    private fun applyHighlight() {
        if (currentUri == null || isApplyingHighlight) return
        val cursor = b.editCode.selectionStart
        val text = b.editCode.text.toString()
        lifecycleScope.launch {
            val highlighted = withContext(Dispatchers.Default) {
                SyntaxHighlighter.highlight(text, currentExt)
            }
            isApplyingHighlight = true
            b.editCode.setText(highlighted)
            val safePos = cursor.coerceIn(0, b.editCode.text.length)
            b.editCode.setSelection(safePos)
            isApplyingHighlight = false
        }
    }

    private fun reHighlight() {
        handler.removeCallbacks(highlightRunnable)
        handler.post(highlightRunnable)
    }

    private fun doUndo() {
        val state = undoStack.pop()
        if (state == null) { toast("Nothing to undo"); return }
        isApplyingUndo = true
        lifecycleScope.launch {
            val highlighted = withContext(Dispatchers.Default) {
                SyntaxHighlighter.highlight(state.text, currentExt)
            }
            b.editCode.setText(highlighted)
            val safePos = state.cursorPos.coerceIn(0, b.editCode.text.length)
            b.editCode.setSelection(safePos)
            isModified = state.text != originalContent
            updateModifiedDot()
            isApplyingUndo = false
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
                updateModifiedDot()
                toast("✓ Saved")
            } else toast("Save failed!")
        }
    }

    // ── Find ──────────────────────────────────────────────────────────────────

    private fun toggleFindBar() {
        b.layoutFindBar.visibility =
            if (b.layoutFindBar.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        if (b.layoutFindBar.visibility == View.VISIBLE) b.etFind.requestFocus()
    }

    private fun doFind() {
        val q = b.etFind.text.toString()
        if (q.isEmpty()) { b.tvFindCount.text = ""; reHighlight(); findMatches = emptyList(); return }
        val text = b.editCode.text.toString()
        val matches = mutableListOf<Int>()
        var idx = 0
        while (true) {
            val i = text.indexOf(q, idx, ignoreCase = true); if (i == -1) break
            matches.add(i); idx = i + q.length
        }
        findMatches = matches
        findIdx = if (matches.isNotEmpty()) 0 else -1
        updateFindHighlights(q)
        updateFindCount()
        if (findIdx >= 0) scrollToChar(matches[findIdx])
    }

    private fun navigateFind(dir: Int) {
        if (findMatches.isEmpty()) return
        findIdx = (findIdx + dir + findMatches.size) % findMatches.size
        updateFindCount()
        updateFindHighlights(b.etFind.text.toString())
        scrollToChar(findMatches[findIdx])
    }

    private fun updateFindHighlights(q: String) {
        if (q.isEmpty()) return
        val text = b.editCode.text.toString()
        val highlighted = SyntaxHighlighter.highlight(text, currentExt)
        val matchColor = requireContext().getColor(R.color.match_highlight)
        val currentColor = requireContext().getColor(R.color.accent)
        for ((i, start) in findMatches.withIndex()) {
            val end = (start + q.length).coerceAtMost(highlighted.length)
            val color = if (i == findIdx) currentColor else matchColor
            highlighted.setSpan(BackgroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        isApplyingHighlight = true
        val cursor = b.editCode.selectionStart
        b.editCode.setText(highlighted)
        b.editCode.setSelection(cursor.coerceIn(0, b.editCode.text.length))
        isApplyingHighlight = false
    }

    private fun updateFindCount() {
        b.tvFindCount.text = if (findMatches.isEmpty()) "0" else "${findIdx + 1}/${findMatches.size}"
    }

    // ── Replace ───────────────────────────────────────────────────────────────

    private fun doReplaceOne() {
        val find = b.etFind.text.toString()
        val replace = b.etReplace.text.toString()
        if (find.isEmpty()) return
        val text = b.editCode.text.toString()
        val idx = text.indexOf(find, ignoreCase = true)
        if (idx == -1) { toast("Not found"); return }
        takeUndoSnapshot()
        val newText = text.substring(0, idx) + replace + text.substring(idx + find.length)
        setEditorText(newText)
        toast("✓ Replaced 1 occurrence")
        doFind()
    }

    private fun doReplaceAll() {
        val find = b.etFind.text.toString()
        val replace = b.etReplace.text.toString()
        if (find.isEmpty()) return
        val text = b.editCode.text.toString()
        val count = findMatches.size
        if (count == 0) { toast("Not found"); return }
        takeUndoSnapshot()
        val newText = text.replace(find, replace, ignoreCase = true)
        setEditorText(newText)
        toast("✓ Replaced $count occurrence(s)")
        b.etFind.setText(find)
    }

    private fun setEditorText(text: String) {
        lifecycleScope.launch {
            val highlighted = withContext(Dispatchers.Default) {
                SyntaxHighlighter.highlight(text, currentExt)
            }
            isApplyingHighlight = true
            b.editCode.setText(highlighted)
            isApplyingHighlight = false
            isModified = text != originalContent
            updateModifiedDot()
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun updateModifiedDot() {
        b.tvModified.visibility = if (isModified) View.VISIBLE else View.GONE
    }

    private fun updateLineInfo() {
        val text = b.editCode.text.toString()
        val lines = text.count { it == '\n' } + 1
        val chars = text.length
        b.tvLineInfo.text = "$lines lines  $chars chars"
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
            val safe = charIndex.coerceIn(0, b.editCode.text.length)
            val line = layout.getLineForOffset(safe)
            val top = layout.getLineTop(line)
            b.scrollEditor.scrollTo(0, (top - 150).coerceAtLeast(0))
        }
    }

    private fun toast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

    override fun onDestroyView() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroyView()
        _b = null
    }
}
