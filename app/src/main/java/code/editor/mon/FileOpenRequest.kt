package code.editor.mon

import android.net.Uri

data class FileOpenRequest(
    val uri: Uri,
    val fileName: String,
    val relativePath: String,
    val lineNumber: Int = -1,
    val highlightText: String = "",
    val isLocal: Boolean = false
)
