package code.editor.mon

import android.net.Uri

data class SearchResult(
    val fileName: String,
    val relativePath: String,
    val uri: Uri,
    val lineNumber: Int,
    val linePreview: String,
    val matchStartInLine: Int,
    val matchEndInLine: Int,
    val isLocal: Boolean = false
)
