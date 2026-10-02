package com.godot.devassistant.ui

import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.godot.devassistant.R
import com.godot.devassistant.data.ProjectRepository
import com.godot.devassistant.databinding.ActivityEditorBinding
import com.godot.devassistant.editor.CompletionAdapter
import com.godot.devassistant.editor.EditorKeyboardBar
import com.godot.devassistant.editor.GDScriptEditorView
import com.godot.devassistant.model.CompletionItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 代码编辑器界面
 *
 * 功能：
 * - 编辑 .gd 文件
 * - 语法高亮
 * - 代码补全
 * - 键盘工具栏
 * - 保存/另存为
 */
class EditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditorBinding
    private lateinit var repository: ProjectRepository
    private lateinit var completionAdapter: CompletionAdapter

    private var projectUri: Uri? = null
    private var currentFileUri: Uri? = null
    private var projectName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = ProjectRepository(this)

        // 获取传入的项目信息
        projectName = intent.getStringExtra("project_name") ?: "未命名项目"
        projectUri = intent.getStringExtra("project_uri")?.let { Uri.parse(it) }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = projectName
            setDisplayHomeAsUpEnabled(true)
        }

        setupEditor()
        setupCompletionList()
        setupKeyboardBar()
        loadScriptList()
    }

    /**
     * 设置编辑器
     */
    private fun setupEditor() {
        binding.editorView.apply {
            showLineNumbers = true
            autoCompleteEnabled = true

            // 补全回调
            onCompletionRequest = { items ->
                showCompletions(items)
            }

            // 修改状态回调
            onTextChangedListener = { dirty ->
                supportActionBar?.title = if (dirty) "* $projectName" else projectName
            }
        }
    }

    /**
     * 设置补全列表
     */
    private fun setupCompletionList() {
        completionAdapter = CompletionAdapter { item ->
            binding.editorView.insertCompletion(item)
            hideCompletions()
        }
        binding.completionList.apply {
            layoutManager = LinearLayoutManager(this@EditorActivity)
            adapter = completionAdapter
            visibility = View.GONE
        }
    }

    /**
     * 设置键盘工具栏
     */
    private fun setupKeyboardBar() {
        binding.keyboardBar.onKeyPressed = { type, text ->
            when (type) {
                EditorKeyboardBar.KeyType.TAB -> {
                    binding.editorView.insertTextAtCursor(text)
                }
                EditorKeyboardBar.KeyType.TEXT -> {
                    binding.editorView.insertTextAtCursor(text)
                }
                EditorKeyboardBar.KeyType.NEWLINE -> {
                    binding.editorView.insertTextAtCursor("\n")
                    // 自动缩进
                    autoIndent()
                }
                EditorKeyboardBar.KeyType.BACKSPACE -> {
                    binding.editorView.onKeyDown(
                        KeyEvent.KEYCODE_DEL,
                        KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL)
                    )
                }
                EditorKeyboardBar.KeyType.DELETE -> {
                    // 向前删除
                    val start = binding.editorView.selectionStart
                    if (start < binding.editorView.text.length) {
                        binding.editorView.text.delete(start, start + 1)
                    }
                }
                EditorKeyboardBar.KeyType.ARROW_LEFT -> {
                    binding.editorView.onKeyDown(
                        KeyEvent.KEYCODE_DPAD_LEFT,
                        KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT)
                    )
                }
                EditorKeyboardBar.KeyType.ARROW_RIGHT -> {
                    binding.editorView.onKeyDown(
                        KeyEvent.KEYCODE_DPAD_RIGHT,
                        KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)
                    )
                }
                EditorKeyboardBar.KeyType.ARROW_UP -> {
                    binding.editorView.onKeyDown(
                        KeyEvent.KEYCODE_DPAD_UP,
                        KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP)
                    )
                }
                EditorKeyboardBar.KeyType.ARROW_DOWN -> {
                    binding.editorView.onKeyDown(
                        KeyEvent.KEYCODE_DPAD_DOWN,
                        KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN)
                    )
                }
                EditorKeyboardBar.KeyType.SHIFT -> {
                    // Shift 状态由工具栏内部管理
                }
            }
        }
    }

    /**
     * 自动缩进：根据上一行的缩进量决定当前行缩进
     */
    private fun autoIndent() {
        val editor = binding.editorView
        val cursor = editor.selectionStart
        val source = editor.text.toString()

        // 获取上一行
        val prevLineEnd = source.lastIndexOf('\n', cursor - 1)
        if (prevLineEnd == -1) return
        val prevLineStart = source.lastIndexOf('\n', prevLineEnd - 1) + 1
        val prevLine = source.substring(prevLineStart, prevLineEnd)

        // 计算缩进
        val indent = StringBuilder()
        for (c in prevLine) {
            if (c == ' ' || c == '\t') {
                indent.append(c)
            } else {
                break
            }
        }

        // 如果上一行以冒号结尾，增加一级缩进
        val trimmed = prevLine.trimEnd()
        if (trimmed.endsWith(":")) {
            indent.append("    ")
        }

        if (indent.isNotEmpty()) {
            editor.insertTextAtCursor(indent.toString())
        }
    }

    /**
     * 加载脚本列表
     */
    private fun loadScriptList() {
        projectUri?.let { uri ->
            lifecycleScope.launch {
                val scripts = repository.loadScriptTree(uri)
                // 显示脚本列表（简化版：直接在 Snackbar 提示）
                if (scripts.isEmpty()) {
                    Snackbar.make(binding.root, "项目中没有 .gd 脚本文件", Snackbar.LENGTH_LONG).show()
                } else {
                    // 创建第一个脚本的新文件模板
                    showScriptPicker(scripts.map { it.name to it.uri })
                }
            }
        }
    }

    /**
     * 显示脚本选择器
     */
    private fun showScriptPicker(scripts: List<Pair<String, Uri>>) {
        val names = scripts.map { it.first }.toTypedArray()
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("选择脚本文件")
            .setItems(names) { _, which ->
                val (_, uri) = scripts[which]
                openScript(uri)
            }
            .setNeutralButton("新建") { _, _ ->
                showCreateScriptDialog()
            }
            .show()
    }

    /**
     * 新建脚本
     */
    private fun showCreateScriptDialog() {
        val input = android.widget.EditText(this).apply {
            hint = "脚本名.gd"
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("新建脚本")
            .setView(input)
            .setPositiveButton("创建") { _, _ ->
                val name = input.text.toString()
                if (name.isNotBlank()) {
                    createNewScript(name)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 创建新脚本文件
     */
    private fun createNewScript(fileName: String) {
        projectUri?.let { uri ->
            lifecycleScope.launch {
                val fileUri = repository.createScript(uri, fileName)
                if (fileUri != null) {
                    // 写入模板
                    val template = buildScriptTemplate(fileName)
                    repository.saveScript(fileUri, template)
                    openScript(fileUri)
                } else {
                    Snackbar.make(binding.root, "创建文件失败", Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * 生成脚本模板
     */
    private fun buildScriptTemplate(fileName: String): String {
        val className = fileName.removeSuffix(".gd").replaceFirstChar { it.uppercase() }
        return """extends Node
# $className.gd
# 创建时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINESE).format(java.util.Date())}

# 成员变量
# var speed = 200

# 信号
# signal custom_signal(param)

# 当节点进入场景树时调用
func _ready():
    pass

# 每帧调用
# func _process(delta):
#     pass
"""
    }

    /**
     * 打开脚本文件
     */
    private fun openScript(uri: Uri) {
        currentFileUri = uri
        lifecycleScope.launch {
            val content = repository.readScript(uri)
            binding.editorView.setFileContent(content)
            supportActionBar?.title = projectName
        }
    }

    /**
     * 显示补全列表
     */
    private fun showCompletions(items: List<CompletionItem>) {
        if (items.isEmpty()) {
            hideCompletions()
            return
        }
        completionAdapter.submitList(items)
        binding.completionList.visibility = View.VISIBLE
    }

    /**
     * 隐藏补全列表
     */
    private fun hideCompletions() {
        binding.completionList.visibility = View.GONE
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_editor, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_save -> {
                saveCurrentFile()
                true
            }
            R.id.action_new_script -> {
                showCreateScriptDialog()
                true
            }
            R.id.action_toggle_comment -> {
                binding.editorView.toggleComment()
                true
            }
            R.id.action_indent -> {
                binding.editorView.indentSelection()
                true
            }
            R.id.action_outdent -> {
                binding.editorView.outdentSelection()
                true
            }
            R.id.action_goto_line -> {
                showGotoLineDialog()
                true
            }
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * 保存当前文件
     */
    private fun saveCurrentFile() {
        currentFileUri?.let { uri ->
            lifecycleScope.launch {
                val content = binding.editorView.getFileContent()
                val success = repository.saveScript(uri, content)
                if (success) {
                    binding.editorView.markClean()
                    supportActionBar?.title = projectName
                    Snackbar.make(binding.root, "已保存", Snackbar.LENGTH_SHORT).show()
                } else {
                    Snackbar.make(binding.root, "保存失败", Snackbar.LENGTH_LONG).show()
                }
            }
        } ?: run {
            Snackbar.make(binding.root, "没有打开的文件", Snackbar.LENGTH_SHORT).show()
        }
    }

    /**
     * 跳转行对话框
     */
    private fun showGotoLineDialog() {
        val input = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "行号"
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("跳转到行")
            .setView(input)
            .setPositiveButton("跳转") { _, _ ->
                val line = input.text.toString().toIntOrNull() ?: return@setPositiveButton
                binding.editorView.gotoLine(line)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onBackPressed() {
        if (binding.editorView.isModified) {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("未保存的更改")
                .setMessage("当前文件有未保存的更改，是否保存？")
                .setPositiveButton("保存") { _, _ ->
                    saveCurrentFile()
                    finish()
                }
                .setNegativeButton("不保存") { _, _ -> finish() }
                .setNeutralButton("取消", null)
                .show()
        } else {
            super.onBackPressed()
        }
    }
}
