package com.godot.devassistant.ui

import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.godot.devassistant.R
import com.godot.devassistant.data.ProjectRepository
import com.godot.devassistant.databinding.ActivityEditorBinding
import com.godot.devassistant.editor.CompletionAdapter
import com.godot.devassistant.editor.EditorKeyboardBar
import com.godot.devassistant.model.CompletionItem
import kotlinx.coroutines.launch

/**
 * 代码编辑器界面
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

    private fun setupEditor() {
        binding.editorView.apply {
            showLineNumbers = true
            autoCompleteEnabled = true

            onCompletionRequest = { items ->
                showCompletions(items)
            }

            onTextChangedListener = { dirty ->
                supportActionBar?.title = if (dirty) "* $projectName" else projectName
            }
        }
    }

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
     * 键盘工具栏事件处理
     */
    private fun setupKeyboardBar() {
        binding.keyboardBar.onKeyPressed = { type, text ->
            when (type) {
                EditorKeyboardBar.KeyType.COMPLETE -> {
                    // 手动触发代码补全
                    binding.editorView.requestCompletionsNow()
                }
                EditorKeyboardBar.KeyType.TAB -> {
                    binding.editorView.insertTextAtCursor("\t")
                }
                EditorKeyboardBar.KeyType.TEXT -> {
                    binding.editorView.insertTextAtCursor(text)
                }
                EditorKeyboardBar.KeyType.NEWLINE -> {
                    binding.editorView.insertTextAtCursor("\n")
                    autoIndent()
                }
                EditorKeyboardBar.KeyType.BACKSPACE -> {
                    binding.editorView.dispatchKeyEvent(
                        KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL)
                    )
                    binding.editorView.dispatchKeyEvent(
                        KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL)
                    )
                }
                EditorKeyboardBar.KeyType.DELETE -> {
                    val start = binding.editorView.selectionStart
                    if (start in 0 until binding.editorView.text.length) {
                        binding.editorView.text.delete(start, start + 1)
                    }
                }
                EditorKeyboardBar.KeyType.ARROW_LEFT -> moveCursor(KeyEvent.KEYCODE_DPAD_LEFT)
                EditorKeyboardBar.KeyType.ARROW_RIGHT -> moveCursor(KeyEvent.KEYCODE_DPAD_RIGHT)
                EditorKeyboardBar.KeyType.ARROW_UP -> moveCursor(KeyEvent.KEYCODE_DPAD_UP)
                EditorKeyboardBar.KeyType.ARROW_DOWN -> moveCursor(KeyEvent.KEYCODE_DPAD_DOWN)

                // 修饰键由键盘栏内部消化，这里仅作兜底
                EditorKeyboardBar.KeyType.CTRL,
                EditorKeyboardBar.KeyType.ALT,
                EditorKeyboardBar.KeyType.SHIFT -> {
                    // 无需处理
                }
            }
        }
    }

    /** 通过派发按键事件移动光标 */
    private fun moveCursor(keyCode: Int) {
        binding.editorView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        binding.editorView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    /**
     * 自动缩进：根据上一行的缩进量决定当前行缩进
     */
    private fun autoIndent() {
        val editor = binding.editorView
        val cursor = editor.selectionStart
        val source = editor.text.toString()
        if (cursor <= 0 || cursor > source.length) return

        val prevLineEnd = source.lastIndexOf('\n', cursor - 1)
        val prevLineStart = if (prevLineEnd <= 0) 0 else source.lastIndexOf('\n', prevLineEnd - 1) + 1
        val end = if (prevLineEnd == -1) cursor else prevLineEnd
        if (prevLineStart >= end) return

        val prevLine = source.substring(prevLineStart, end)

        val indent = StringBuilder()
        for (c in prevLine) {
            if (c == ' ' || c == '\t') indent.append(c) else break
        }

        val trimmed = prevLine.trimEnd()
        if (trimmed.endsWith(":")) {
            indent.append("    ")
        }

        if (indent.isNotEmpty()) {
            editor.insertTextAtCursor(indent.toString())
        }
    }

    private fun loadScriptList() {
        projectUri?.let { uri ->
            lifecycleScope.launch {
                val scripts = repository.loadScriptTree(uri)
                if (scripts.isEmpty()) {
                    Snackbar.make(binding.root, "项目中没有 .gd 脚本文件", Snackbar.LENGTH_LONG).show()
                } else {
                    showScriptPicker(scripts.map { it.name to it.uri })
                }
            }
        }
    }

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

    private fun createNewScript(fileName: String) {
        projectUri?.let { uri ->
            lifecycleScope.launch {
                val fileUri = repository.createScript(uri, fileName)
                if (fileUri != null) {
                    val template = buildScriptTemplate(fileName)
                    repository.saveScript(fileUri, template)
                    openScript(fileUri)
                } else {
                    Snackbar.make(binding.root, "创建文件失败", Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

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

    private fun openScript(uri: Uri) {
        currentFileUri = uri
        lifecycleScope.launch {
            val content = repository.readScript(uri)
            binding.editorView.setFileContent(content)
            supportActionBar?.title = projectName
        }
    }

    private fun showCompletions(items: List<CompletionItem>) {
        if (items.isEmpty()) {
            hideCompletions()
            Snackbar.make(binding.root, "没有可用的补全", Snackbar.LENGTH_SHORT).show()
            return
        }
        completionAdapter.submitList(items)
        binding.completionList.visibility = View.VISIBLE
    }

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

    @Deprecated("Deprecated in Java")
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
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}
