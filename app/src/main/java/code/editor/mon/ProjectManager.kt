package code.editor.mon

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File

object ProjectManager {
    var projectName: String = ""
    var rootUri: Uri? = null
    var rootDocument: DocumentFile? = null
    var fileNodes: List<FileNode> = emptyList()
    var allFiles: List<FileNode> = emptyList()  // flat list, non-directories only

    var isLocalProject: Boolean = false
    var localProjectDir: File? = null

    fun isProjectOpen(): Boolean = rootUri != null || localProjectDir != null

    fun reset() {
        projectName = ""
        rootUri = null
        rootDocument = null
        fileNodes = emptyList()
        allFiles = emptyList()
        isLocalProject = false
        localProjectDir = null
    }
}
