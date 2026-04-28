package code.editor.mon

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import code.editor.mon.databinding.FragmentFilesBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class FilesFragment : Fragment() {

    private var _b: FragmentFilesBinding? = null
    private val b get() = _b!!
    private lateinit var vm: SharedViewModel
    private val flatList = mutableListOf<FileNode>()
    private lateinit var adapter: FileTreeAdapter

    private val folderLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            loadSafProject(uri)
        }
    }

    private val zipPickLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { loadZip(it) }
        }
    }

    private val exportZipLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { doExportZip(it) }
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentFilesBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vm = ViewModelProvider(requireActivity())[SharedViewModel::class.java]

        adapter = FileTreeAdapter(flatList,
            onClick = { node ->
                if (node.isDirectory) {
                    node.isExpanded = !node.isExpanded
                    refreshFlatList()
                } else {
                    openFile(node)
                }
            },
            onLongClick = { node ->
                if (!node.isDirectory) openFile(node)
            }
        )
        b.rvFileTree.layoutManager = LinearLayoutManager(requireContext())
        b.rvFileTree.adapter = adapter

        b.btnOpenFolder.setOnClickListener {
            folderLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
        }

        b.btnOpenZip.setOnClickListener {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/zip"
            }
            zipPickLauncher.launch(i)
        }

        b.btnExportZip.setOnClickListener {
            if (!ProjectManager.isProjectOpen()) {
                Toast.makeText(requireContext(), "No project open", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/zip"
                putExtra(Intent.EXTRA_TITLE, "${ProjectManager.projectName}.zip")
            }
            exportZipLauncher.launch(i)
        }

        if (ProjectManager.isProjectOpen()) showProject() else showEmpty()
    }

    private fun loadSafProject(uri: Uri) {
        setLoading(true, "Loading...")
        lifecycleScope.launch {
            val root = withContext(Dispatchers.IO) {
                DocumentFile.fromTreeUri(requireContext(), uri)
            }
            if (root == null || !root.isDirectory) {
                setLoading(false)
                toast("Cannot open folder")
                return@launch
            }
            val nodes = withContext(Dispatchers.IO) {
                FileUtils.traverseDocumentFile(root)
            }
            ProjectManager.reset()
            ProjectManager.projectName = root.name ?: "Project"
            ProjectManager.rootUri = uri
            ProjectManager.rootDocument = root
            ProjectManager.fileNodes = nodes
            ProjectManager.allFiles = FileUtils.getAllFiles(nodes)
            ProjectManager.isLocalProject = false
            setLoading(false)
            showProject()
            vm.projectLoaded.value = true
        }
    }

    private fun loadZip(uri: Uri) {
        setLoading(true, "Extracting ZIP...")
        lifecycleScope.launch {
            val dir = withContext(Dispatchers.IO) {
                val dest = File(
                    requireContext().getExternalFilesDir("projects"),
                    "proj_${System.currentTimeMillis()}"
                ).also { it.mkdirs() }
                if (FileUtils.extractZip(uri, dest, requireContext())) dest else null
            }
            if (dir == null) {
                setLoading(false); toast("Failed to extract ZIP"); return@launch
            }
            val root = dir.listFiles()?.takeIf { it.size == 1 && it[0].isDirectory }
                ?.get(0) ?: dir
            val nodes = withContext(Dispatchers.IO) { FileUtils.traverseLocalDir(root) }
            ProjectManager.reset()
            ProjectManager.projectName = root.name
            ProjectManager.localProjectDir = root
            ProjectManager.isLocalProject = true
            ProjectManager.fileNodes = nodes
            ProjectManager.allFiles = FileUtils.getAllFiles(nodes)
            setLoading(false)
            showProject()
            vm.projectLoaded.value = true
        }
    }

    private fun doExportZip(destUri: Uri) {
        lifecycleScope.launch {
            setLoading(true, "Zipping...")
            val ok = withContext(Dispatchers.IO) {
                if (ProjectManager.isLocalProject)
                    ProjectManager.localProjectDir?.let {
                        FileUtils.zipLocalDirectory(it, destUri, requireContext())
                    } ?: false
                else
                    ProjectManager.rootDocument?.let {
                        FileUtils.zipDocumentFolder(it, destUri, requireContext())
                    } ?: false
            }
            setLoading(false)
            toast(if (ok) "✓ Exported successfully!" else "Export failed")
        }
    }

    private fun openFile(node: FileNode) {
        if (!FileUtils.isTextFile(node.name)) {
            toast("Binary file — cannot edit"); return
        }
        vm.openFileRequest.value = FileOpenRequest(
            uri = node.uri,
            fileName = node.name,
            relativePath = node.relativePath,
            isLocal = ProjectManager.isLocalProject
        )
        findNavController().navigate(R.id.editorFragment)
    }

    private fun showProject() {
        refreshFlatList()
        b.tvEmptyState.visibility = View.GONE
        b.rvFileTree.visibility = View.VISIBLE
        b.layoutProjectBar.visibility = View.VISIBLE
        b.tvProjectName.text = "📂 ${ProjectManager.projectName}"
    }

    private fun showEmpty() {
        b.tvEmptyState.visibility = View.VISIBLE
        b.rvFileTree.visibility = View.GONE
        b.layoutProjectBar.visibility = View.GONE
    }

    private fun refreshFlatList() {
        flatList.clear()
        flatList.addAll(FileUtils.flattenFileTree(ProjectManager.fileNodes))
        adapter.notifyDataSetChanged()
    }

    private fun setLoading(on: Boolean, msg: String = "") {
        b.progressBar.visibility = if (on) View.VISIBLE else View.GONE
        if (msg.isNotEmpty()) {
            b.tvProjectName.text = msg
            b.layoutProjectBar.visibility = View.VISIBLE
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
