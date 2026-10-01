package com.godot.devassistant.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.godot.devassistant.adapter.DocAdapter
import com.godot.devassistant.databinding.ActivityDocBrowserBinding
import com.godot.devassistant.model.DocEntry

/**
 * 离线文档浏览界面
 *
 * 内置单份文档：GDScript 语法完整参考
 * 首次进入直接展示文档内容，列表与搜索辅助定位。
 */
class DocBrowserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDocBrowserBinding
    private lateinit var docAdapter: DocAdapter

    /** 唯一内置文档 */
    private val singleDoc = DocEntry(
        id = "gdscript_reference",
        title = "GDScript 语法完整参考",
        path = "docs/gdscript_reference.html"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDocBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "GDScript 语法参考"
            setDisplayHomeAsUpEnabled(true)
        }

        setupDocList()
        setupWebView()
        setupSearch()
        loadDocList()
    }

    /**
     * 设置文档列表
     */
    private fun setupDocList() {
        docAdapter = DocAdapter { doc ->
            showDocument(doc)
        }
        binding.recyclerDocs.apply {
            layoutManager = LinearLayoutManager(this@DocBrowserActivity)
            adapter = docAdapter
        }
    }

    /**
     * 设置 WebView
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.docWebView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                    view.loadUrl(url)
                    return true
                }
            }
        }
    }

    /**
     * 设置搜索框：过滤文档标题
     */
    private fun setupSearch() {
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val keyword = s?.toString()?.trim() ?: ""
                // 标题不匹配则列表为空；匹配则保留
                val matches = keyword.isEmpty() || singleDoc.title.contains(keyword, true)
                if (matches) {
                    docAdapter.submitList(listOf(singleDoc))
                } else {
                    docAdapter.submitList(emptyList())
                }
            }
        })
    }

    /**
     * 加载文档列表
     */
    private fun loadDocList() {
        docAdapter.submitList(listOf(singleDoc))
    }

    /**
     * 显示文档内容（从 assets 加载）
     */
    private fun showDocument(doc: DocEntry) {
        binding.docWebView.loadUrl("file:///android_asset/docs/${doc.id}.html")
        binding.docWebView.visibility = View.VISIBLE
        binding.recyclerDocs.visibility = View.GONE
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}