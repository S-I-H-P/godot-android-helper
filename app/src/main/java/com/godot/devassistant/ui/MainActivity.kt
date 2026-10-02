package com.godot.devassistant.ui

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.godot.devassistant.R
import com.godot.devassistant.adapter.ProjectAdapter
import com.godot.devassistant.databinding.ActivityMainBinding
import com.godot.devassistant.util.SafFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var projectAdapter: ProjectAdapter

    private val openTreeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                SafFileManager.persistTreePermission(this, uri)
                viewModel.setRootDirectory(uri)
            }
        }
    }

    private val importDocLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            importDocument(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        setupRecyclerView()
        observeViewModel()
        checkSavedRootUri()
    }

    private fun setupRecyclerView() {
        projectAdapter = ProjectAdapter { project ->
            val intent = Intent(this, EditorActivity::class.java).apply {
                putExtra("project_name", project.name)
                putExtra("project_uri", project.uri.toString())
            }
            startActivity(intent)
        }
        binding.recyclerProjects.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = projectAdapter
        }
    }

    private fun observeViewModel() {
        viewModel.projects.observe(this) { projects ->
            projectAdapter.submitList(projects)
            binding.emptyView.visibility = if (projects.isEmpty()) View.VISIBLE else View.GONE
            if (projects.isEmpty()) {
                binding.emptyView.setOnClickListener {
                    openTreeLauncher.launch(SafFileManager.createOpenTreeIntent())
                }
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(this) { error ->
            error?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun checkSavedRootUri() {
        SafFileManager.getSavedRootUri(this)?.let { uri ->
            viewModel.setRootDirectory(uri)
        }
    }

    private fun importDocument(uri: Uri) {
        lifecycleScope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    }
                }

                if (content == null) {
                    Snackbar.make(binding.root, "无法读取文件", Snackbar.LENGTH_LONG).show()
                    return@launch
                }

                val name = queryName(uri)
                showDocument(name, content)

            } catch (e: Exception) {
                Snackbar.make(binding.root, "导入失败: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun queryName(uri: Uri): String {
        var name = "未命名文档"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }

    private fun showDocument(title: String, content: String) {
        val isMarkdown = title.endsWith(".md", true)
        val escaped = content.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val bodyHtml = if (isMarkdown) {
            renderMarkdown(escaped)
        } else {
            escaped
        }

        val webContent = buildString {
            append("<html><head><meta charset=\"UTF-8\"><style>")
            append("body{font-family:-apple-system,'Noto Sans SC',sans-serif;")
            append("background:#1E1E2E;color:#CDD6F4;line-height:1.8;padding:16px;font-size:15px;}")
            append("h1,h2,h3{color:#89B4FA;}")
            append("h1{border-bottom:2px solid #4A90D9;padding-bottom:8px;}")
            append("code{background:#181825;padding:2px 6px;border-radius:4px;")
            append("font-family:monospace;color:#F5C2E7;}")
            append("pre{background:#181825;padding:12px;border-radius:8px;")
            append("overflow-x:auto;border:1px solid #313244;}")
            append("pre code{background:none;padding:0;}")
            append("a{color:#82AAFF;}")
            append("table{border-collapse:collapse;width:100%;margin:10px 0;}")
            append("th,td{border:1px solid #313244;padding:6px 8px;}")
            append("th{background:#313244;}")
            append("</style></head><body>")
            append(bodyHtml)
            append("</body></html>")
        }

        val webView = WebView(this)
        webView.setBackgroundColor(0xFF1E1E2E.toInt())
        webView.settings.javaScriptEnabled = false
        webView.loadDataWithBaseURL(null, webContent, "text/html", "UTF-8", null)

        val dialog = Dialog(this)
        dialog.setContentView(webView,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT))
        dialog.show()
    }

    private fun renderMarkdown(md: String): String {
        var html = md
        // 代码块 (在转义后的基础上，把 <pre><code> 还原)
        html = html.replace(Regex("(?s)`{3}(\\w+)?\\n(.*?)`{3}"), "<pre><code>\\2</code></pre>")
        // 行内代码
        html = html.replace(Regex("`([^`]+)`"), "<code>\\1</code>")
        // 标题
        html = html.replace(Regex("(?m)^### (.*)$"), "<h3>\\1</h3>")
        html = html.replace(Regex("(?m)^## (.*)$"), "<h2>\\1</h2>")
        html = html.replace(Regex("(?m)^# (.*)$"), "<h1>\\1</h1>")
        // 粗体
        html = html.replace(Regex("\\*\\*([^*]+)\\*\\*"), "<b>\\1</b>")
        // 列表
        html = html.replace(Regex("(?m)^\\s*[-*] (.*)$"), "<li>\\1</li>")
        // 段落分隔
        html = html.replace("\n\n", "</p><p>")
        return "<p>" + html + "</p>"
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_import_doc -> {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                importDocLauncher.launch(intent)
                true
            }
            R.id.action_select_dir -> {
                openTreeLauncher.launch(SafFileManager.createOpenTreeIntent())
                true
            }
            R.id.action_refresh -> {
                viewModel.refresh()
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_docs -> {
                startActivity(Intent(this, DocBrowserActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
