package code.editor.mon

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object FileUtils {

    private val TEXT_EXTENSIONS = setOf(
        "kt", "java", "xml", "json", "txt", "md", "gradle", "py", "js", "ts",
        "html", "css", "cpp", "c", "h", "hpp", "sh", "yaml", "yml", "toml",
        "properties", "pro", "mk", "cmake", "swift", "go", "rs", "dart", "rb",
        "php", "sql", "kts", "groovy", "bat", "ps1", "ini", "cfg", "conf", "log"
    )

    fun isTextFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in TEXT_EXTENSIONS || !name.contains('.')
    }

    // ── DocumentFile (SAF) operations ──────────────────────────────────────

    fun readDocumentFile(uri: Uri, context: Context): String? = try {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
    } catch (e: Exception) { null }

    fun writeDocumentFile(uri: Uri, content: String, context: Context): Boolean = try {
        context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use {
            it.write(content)
        }
        true
    } catch (e: Exception) { false }

    fun traverseDocumentFile(
        doc: DocumentFile,
        depth: Int = 0,
        relativePath: String = ""
    ): List<FileNode> {
        val nodes = mutableListOf<FileNode>()
        val entries = doc.listFiles()
            .sortedWith(compareBy({ !it.isDirectory }, { it.name?.lowercase() }))
        for (entry in entries) {
            val name = entry.name ?: continue
            if (name.startsWith(".")) continue
            val relPath = if (relativePath.isEmpty()) name else "$relativePath/$name"
            val node = FileNode(
                name = name,
                uri = entry.uri,
                isDirectory = entry.isDirectory,
                depth = depth,
                relativePath = relPath
            )
            if (entry.isDirectory) {
                node.children.addAll(traverseDocumentFile(entry, depth + 1, relPath))
            }
            nodes.add(node)
        }
        return nodes
    }

    // ── Local File operations ───────────────────────────────────────────────

    fun readLocalFile(uri: Uri): String? = try {
        File(uri.path!!).readText()
    } catch (e: Exception) { null }

    fun writeLocalFile(uri: Uri, content: String): Boolean = try {
        File(uri.path!!).writeText(content)
        true
    } catch (e: Exception) { false }

    fun traverseLocalDir(dir: File, depth: Int = 0, relativePath: String = ""): List<FileNode> {
        val nodes = mutableListOf<FileNode>()
        val entries = dir.listFiles()
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?: return nodes
        for (entry in entries) {
            if (entry.name.startsWith(".")) continue
            val relPath = if (relativePath.isEmpty()) entry.name else "$relativePath/${entry.name}"
            val node = FileNode(
                name = entry.name,
                uri = Uri.fromFile(entry),
                isDirectory = entry.isDirectory,
                depth = depth,
                relativePath = relPath
            )
            if (entry.isDirectory) {
                node.children.addAll(traverseLocalDir(entry, depth + 1, relPath))
            }
            nodes.add(node)
        }
        return nodes
    }

    // ── Tree helpers ────────────────────────────────────────────────────────

    fun flattenFileTree(nodes: List<FileNode>): List<FileNode> {
        val result = mutableListOf<FileNode>()
        for (node in nodes) {
            result.add(node)
            if (node.isDirectory && node.isExpanded) {
                result.addAll(flattenFileTree(node.children))
            }
        }
        return result
    }

    fun getAllFiles(nodes: List<FileNode>): List<FileNode> {
        val result = mutableListOf<FileNode>()
        for (node in nodes) {
            if (!node.isDirectory) result.add(node)
            else result.addAll(getAllFiles(node.children))
        }
        return result
    }

    // ── ZIP operations ─────────────────────────────────────────────────────

    fun extractZip(zipUri: Uri, destDir: File, context: Context): Boolean = try {
        context.contentResolver.openInputStream(zipUri)?.use { input ->
            ZipInputStream(input).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val destFile = File(destDir, entry.name)
                    if (entry.isDirectory) {
                        destFile.mkdirs()
                    } else {
                        destFile.parentFile?.mkdirs()
                        FileOutputStream(destFile).use { fos -> zis.copyTo(fos) }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
        true
    } catch (e: Exception) { e.printStackTrace(); false }

    fun zipLocalDirectory(sourceDir: File, destUri: Uri, context: Context): Boolean = try {
        context.contentResolver.openOutputStream(destUri)?.use { out ->
            ZipOutputStream(out).use { zos ->
                addToZip(sourceDir, sourceDir.name, zos)
            }
        }
        true
    } catch (e: Exception) { e.printStackTrace(); false }

    private fun addToZip(file: File, zipPath: String, zos: ZipOutputStream) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { addToZip(it, "$zipPath/${it.name}", zos) }
        } else {
            zos.putNextEntry(ZipEntry(zipPath))
            file.inputStream().use { it.copyTo(zos) }
            zos.closeEntry()
        }
    }

    fun zipDocumentFolder(root: DocumentFile, destUri: Uri, context: Context): Boolean = try {
        context.contentResolver.openOutputStream(destUri)?.use { out ->
            ZipOutputStream(out).use { zos ->
                addDocumentToZip(root, root.name ?: "project", zos, context)
            }
        }
        true
    } catch (e: Exception) { e.printStackTrace(); false }

    private fun addDocumentToZip(doc: DocumentFile, zipPath: String, zos: ZipOutputStream, ctx: Context) {
        if (doc.isDirectory) {
            doc.listFiles().forEach { addDocumentToZip(it, "$zipPath/${it.name}", zos, ctx) }
        } else {
            zos.putNextEntry(ZipEntry(zipPath))
            ctx.contentResolver.openInputStream(doc.uri)?.use { it.copyTo(zos) }
            zos.closeEntry()
        }
    }

    // ── Unified read/write (handles both SAF and local) ────────────────────

    fun readFile(uri: Uri, isLocal: Boolean, context: Context): String? =
        if (isLocal) readLocalFile(uri) else readDocumentFile(uri, context)

    fun writeFile(uri: Uri, content: String, isLocal: Boolean, context: Context): Boolean =
        if (isLocal) writeLocalFile(uri, content) else writeDocumentFile(uri, content, context)
}
