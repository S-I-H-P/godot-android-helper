package com.godot.devassistant.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.godot.devassistant.GodotDevApp
import com.godot.devassistant.R
import com.godot.devassistant.adapter.ProjectAdapter
import com.godot.devassistant.databinding.ActivityMainBinding
import com.godot.devassistant.util.SafFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 主界面：项目列表
 */
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

    // 文档导入回调
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

    /**
     * 导入 txt 或 md 文档
     */
    private fun importDocument(uri: Uri) {
        lifecycleScope.launch {
            try {
                // 读取文件内容
                val content = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    } ?: return@withContext null
                }

                if (content == null) {
                    Snackbar.make(binding.root, "无法读取文件", Snackbar.LENGTH_LONG).show()
                    return@launch
                }

                // 获取文件名
                val name = queryName(uri)

                // 显示文档
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
        val isMarkdown = title.endsWith(".md")
        val webContent = if (isMarkdown) {
            // 简单的 Markdown 转 HTML
            """
            <html>
            <head>
            <meta charset="UTF-8">
            <style>
                body { font-family: -apple-system, "Noto Sans SC", sans-serif;
                       background: #1E1E2E; color: #CDD6F4; line-height: 1.8;
                       padding: 16px; font-size: 15px; }
                h1,h2,h3 { color: #89B4FA; }
                h1 { border-bottom: 2px solid #4A90D9; padding-bottom: 8px; }
                code { background: #181825; padding: 2px 6px; border-radius: 4px;
                       font-family: monospace; color: #F5C2E7; }
                pre { background: #181825; padding: 12px; border-radius: 8px;
                      overflow-x: auto; border: 1px solid #313244; }
                pre code { background: none; padding: 0; }
                a { color: #82AAFF; }
                table { border-collapse: collapse; width: 100%; margin: 10px 0; }
                th, td { border: 1px solid #313244; padding: 6px 8px; }
                th { background: #313244; }
            </style>
            </head>
            <body>
            ${renderMarkdown(content)}
            </body>
            </html>
            """
        } else {
            """
            <html>
            <head>
            <meta charset="UTF-8">
            <style>
                body { font-family: -apple-system, sans-serif;
                       background: #1E1E2E; color: #CDD6F4;
                       line-height: 1.6; padding: 16px; font-size: 15px; }
            </style>
            </head>
            <body>
            ${content.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")}
            </body>
            </html>
            """
        }

        // 创建 WebView 显示文档
        val webView = WebView(this)
        webView.setBackgroundColor(0xFF1E1E2E.toInt())
        webView.settings.javaScriptEnabled = false
        webView.loadDataWithBaseURL(null, webContent, "text/html", "UTF-8", null)

        val view = object : android.view.View(this) {
            init { visibility = GONE }
        }

        // 使用全屏 Activity 方式显示文档
        val dialog = android.app.Dialog(this)
        dialog.setContentView(webView)
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        )
        dialog.show()

        // 设置标题
        supportActionBar?.title = title
    }

    private fun renderMarkdown(md: String): String {
        var html = md.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        // 标题
        html = html.replace(Regex("^### (.*)$"), "<h3>\\1</h3>", RegexOption.MULTILINE)
        html = html.replace(Regex("^## (.*)$"), "<h2>\\1</h2>", RegexOption.MULTILINE)
        html = html.replace(Regex("^# (.*)$"), "<h1>\\1</h1>", RegexOption.MULTILINE)
        // 代码块
        html = html.replace(Regex("```(?:[a-z]*)\n([\\s\\S]*?)```"), "<pre><code>\\1</code></pre>")
        // 行内代码
        html = html.replace(Regex("`([^`]+)`"), "<code>\\1</code>")
        // 粗体
        html = html.replace(Regex("\\*\\*([^*]+)\\*\\*"), "<b>\\1</b>")
        // 列表
        html = html.replace(Regex("^(\\s+)*[-*] (.*)$"), "<li>\\2</li>", RegexOption.MULTILINE)
        // 段落
        html = html.replace("\n\n", "</p><p>")
        html = "<p>$html</p>"
        return html
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_import_doc -> {
                // 选择 txt 或 md 文件
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                intent.type = "*/*"
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
