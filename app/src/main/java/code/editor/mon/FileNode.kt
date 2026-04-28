package code.editor.mon

import android.net.Uri

data class FileNode(
    val name: String,
    val uri: Uri,
    val isDirectory: Boolean,
    val depth: Int = 0,
    val relativePath: String = "",
    val children: MutableList<FileNode> = mutableListOf(),
    var isExpanded: Boolean = false
)
