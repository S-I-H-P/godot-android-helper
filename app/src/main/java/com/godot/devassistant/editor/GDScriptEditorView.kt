package com.godot.devassistant.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.view.ActionMode
import android.view.GestureDetector
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import androidx.core.content.ContextCompat
import com.godot.devassistant.R
import com.godot.devassistant.model.CompletionItem
import com.godot.devassistant.model.HighlightRange
import com.godot.devassistant.native.NativeLib
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlin.math.max
import kotlin.math.min

/**
 * GDScript 代码编辑器视图
 *
 * 功能：
 * - 语法高亮（通过原生库分析）
 * - 代码补全提示
 * - 行号显示
 * - 自动缩进
 * - 配对括号
 */
class GDScriptEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : EditText(context, attrs) {

    companion object {
        private const val TAG = "GDScriptEditor"
        private val gson = Gson()

        // 配色方案（Material 风格深色主题）
        private const val COLOR_BACKGROUND = 0xFF1E1E2E.toInt()      // Catppuccin 背景
        private const val COLOR_TEXT = 0xFFCDD6F4.toInt()            // 主文本
        private const val COLOR_KEYWORD = 0xFFC792EA.toInt()         // 关键字 - 紫色
        private const val COLOR_TYPE = 0xFFFFCB6B.toInt()            // 类型 - 黄色
        private const val COLOR_STRING = 0xFFA5E075.toInt()          // 字符串 - 绿色
        private const val COLOR_COMMENT = 0xFF6272A4.toInt()         // 注释 - 灰色
        private const val COLOR_NUMBER = 0xFFF78C6C.toInt()          // 数字 - 橙色
        private const val COLOR_FUNCTION = 0xFF82AAFF.toInt()        // 函数 - 蓝色
        private const val COLOR_LINE_NUMBER = 0xFF4A4A6A.toInt()     // 行号
        private const val COLOR_CURRENT_LINE = 0xFF2A2A3E.toInt()    // 当前行背景
        private const val COLOR_CURSOR = 0xFF82AAFF.toInt()          // 光标

        // 补全项类型映射
        private const val KIND_KEYWORD = "keyword"
        private const val KIND_TYPE = "type"
        private const val KIND_FUNCTION = "function"
        private const val KIND_METHOD = "method"
        private const val KIND_CLASS = "class"
    }

    // ============ 属性 ============

    /** 是否显示行号 */
    var showLineNumbers: Boolean = true

    /** 是否启用自动补全 */
    var autoCompleteEnabled: Boolean = true

    /** 代码补全回调（由外部 Activity 注册） */
    var onCompletionRequest: (List<CompletionItem>) -> Unit = {}

    /** 代码变化回调（用于保存状态标记） */
    var onTextChangedListener: ((Boolean) -> Unit)? = null

    /** 当前是否已修改 */
    var isDirty: Boolean = false
        private set

    // ============ 内部状态 ============

    private var highlightTask: Runnable? = null
    private var completionsVisible = false
    private var lastTextForCompletion = ""
    private var completionTriggerDelay = 300L

    // 行号绘制
    private val lineNumberPaint = Paint().apply {
        color = COLOR_LINE_NUMBER
        textSize = textSize * 0.9f
        textAlign = Paint.Align.RIGHT
    }

    // 当前行背景绘制
    private val currentLinePaint = Paint().apply {
        color = COLOR_CURRENT_LINE
    }

    // 光标颜色
    private val cursorPaint = Paint().apply {
        color = COLOR_CURSOR
        strokeWidth = 2f
    }

    // ============ 初始化 ============

    init {
        setTextColor(COLOR_TEXT)
        setBackgroundColor(COLOR_BACKGROUND)
        // 等宽字体
        typeface = android.graphics.Typeface.MONOSPACE
        textSize = 14f

        // 输入配置
        inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS

        setHorizontallyScrolling(true)
        isVerticalScrollBarEnabled = true
        isHorizontalScrollBarEnabled = true

        // 文本监听
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                isDirty = true
                onTextChangedListener?.invoke(true)
                scheduleHighlight()
                scheduleCompletion()
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // 光标位置变化监听
            invalidate()
        }

        // 自定义菜单（复制/粘贴等）
        customSelectionActionModeCallback = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                menu.add(0, 1, 0, "复制")
                menu.add(0, 2, 1, "粘贴")
                menu.add(0, 3, 2, "剪切")
                menu.add(0, 4, 3, "全选")
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                return when (item.itemId) {
                    1 -> { // 复制
                        val sel = text?.subSequence(selectionStart, selectionEnd)?.toString() ?: return true
                        (context as? android.content.ClipboardManager)?.setPrimaryClip(
                            android.content.ClipData.newPlainText("text", sel)
                        )
                        mode.finish()
                        true
                    }
                    2 -> { // 粘贴
                        val clip = (context as? android.content.ClipboardManager)
                            ?.primaryClip?.getItemAt(0)?.text?.toString()
                        if (clip != null) {
                            val start = selectionStart
                            val end = selectionEnd
                            text.replace(start, end, clip)
                        }
                        mode.finish()
                        true
                    }
                    3 -> { // 剪切
                        val sel = text?.subSequence(selectionStart, selectionEnd)?.toString() ?: return true
                        (context as? android.content.ClipboardManager)?.setPrimaryClip(
                            android.content.ClipData.newPlainText("text", sel)
                        )
                        val start = selectionStart
                        val end = selectionEnd
                        text.delete(start, end)
                        mode.finish()
                        true
                    }
                    4 -> { // 全选
                        setSelection(0, text.length)
                        mode.finish()
                        true
                    }
                    else -> false
                }
            }

            override fun onDestroyActionMode(mode: ActionMode) {}
        }
    }

    // ============ 绘制 ============

    override fun onDraw(canvas: Canvas) {
        // 绘制当前行背景
        drawCurrentLineHighlight(canvas)
        // 绘制行号
        drawLineNumbers(canvas)
        super.onDraw(canvas)
    }

    /**
     * 绘制当前行高亮
     */
    private fun drawCurrentLineHighlight(canvas: Canvas) {
        val line = layout?.getLineForOffset(selectionStart) ?: return
        val lineTop = layout.getLineTop(line)
        val lineBottom = layout.getLineBottom(line)
        val left = if (showLineNumbers) lineNumberWidth() else 0f
        canvas.drawRect(
            left + scrollX,
            lineTop.toFloat(),
            width.toFloat() + scrollX,
            lineBottom.toFloat(),
            currentLinePaint
        )
    }

    /**
     * 绘制行号
     */
    private fun drawLineNumbers(canvas: Canvas) {
        if (!showLineNumbers || layout == null) return

        val firstLine = layout.getLineForVertical(scrollY)
        val lastLine = layout.getLineForVertical(scrollY + height)
        val lineNumberWidth = lineNumberWidth()

        for (line in firstLine..lastLine) {
            val lineTop = layout.getLineTop(line).toFloat()
            val lineBottom = layout.getLineBottom(line).toFloat()
            val lineNumber = line + 1
            val text = lineNumber.toString()

            // 绘制行号
            val baseline = (lineTop + lineBottom) / 2 - (lineNumberPaint.fontMetrics.descent + lineNumberPaint.fontMetrics.ascent) / 2
            canvas.drawText(
                text,
                lineNumberWidth - 12f + scrollX,
                baseline,
                lineNumberPaint
            )
        }
    }

    /**
     * 行号区域宽度
     */
    private fun lineNumberWidth(): Float {
        if (!showLineNumbers) return 0f
        val lineCount = layout?.lineCount ?: 1
        val digits = max(2, lineCount.toString().length)
        return 40f + digits * 8f
    }

    // ============ 语法高亮 ============

    /**
     * 调度语法高亮任务（防抖）
     */
    private fun scheduleHighlight() {
        highlightTask?.let { removeCallbacks(it) }
        highlightTask = Runnable {
            applyHighlighting()
        }
        postDelayed(highlightTask, 200)
    }

    /**
     * 应用语法高亮
     */
    fun applyHighlighting() {
        val source = text.toString()
        if (source.isEmpty()) return

        try {
            val json = NativeLib.highlightCode(source)
            val ranges: List<HighlightRange> = gson.fromJson(
                json,
                object : TypeToken<List<HighlightRange>>() {}.type
            )

            val spannable = SpannableStringBuilder(source)

            // 先清空原有样式
            for (span in spannable.getSpans(0, spannable.length, ForegroundColorSpan::class.java)) {
                spannable.removeSpan(span)
            }

            // 应用新的高亮
            for (range in ranges) {
                val start = range.start.coerceIn(0, source.length)
                val end = range.end.coerceIn(start, source.length)
                if (start >= end) continue

                val color = when (range.type) {
                    1 -> COLOR_KEYWORD     // KEYWORD
                    2 -> COLOR_TYPE        // TYPE
                    3 -> COLOR_FUNCTION    // BUILTIN_FUNC
                    4 -> COLOR_COMMENT     // COMMENT
                    5 -> COLOR_STRING      // STRING
                    6 -> COLOR_NUMBER      // NUMBER
                    9 -> COLOR_TYPE        // CLASS_DEF
                    10 -> COLOR_NUMBER     // CONSTANT
                    else -> COLOR_TEXT
                }
                spannable.setSpan(
                    ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            setText(spannable, BufferType.SPANNABLE)
        } catch (e: Exception) {
            // 高亮失败时保留原文本
        }
    }

    // ============ 代码补全 ============

    /**
     * 调度补全请求（防抖）
     */
    private fun scheduleCompletion() {
        if (!autoCompleteEnabled) return
        completionRunnable?.let { removeCallbacks(it) }
        completionRunnable = Runnable {
            requestCompletions()
        }
        postDelayed(completionRunnable, completionTriggerDelay)
    }

    private var completionRunnable: Runnable? = null

    /**
     * 请求代码补全
     */
    private fun requestCompletions() {
        // 获取光标前的文本
        val cursor = selectionStart
        val source = text.toString()
        val prefix = source.substring(0, cursor)

        if (prefix.isEmpty() || prefix.endsWith("\n")) return

        // 获取当前词
        val currentWord = extractCurrentWord(source, cursor)
        if (currentWord.length < 1) return

        // 避免重复请求
        if (prefix == lastTextForCompletion) return
        lastTextForCompletion = prefix

        try {
            val json = NativeLib.getCompletions(prefix)
            val items: List<CompletionItem> = gson.fromJson(
                json,
                object : TypeToken<List<CompletionItem>>() {}.type
            )
            if (items.isNotEmpty()) {
                onCompletionRequest(items)
            }
        } catch (e: Exception) {
            // 忽略补全错误
        }
    }

    /**
     * 提取光标前的当前单词
     */
    private fun extractCurrentWord(source: String, cursor: Int): String {
        val sb = StringBuilder()
        var i = cursor - 1
        while (i >= 0) {
            val c = source[i]
            if (c.isLetterOrDigit() || c == '_') {
                sb.insert(0, c)
                i--
            } else {
                break
            }
        }
        return sb.toString()
    }

    /**
     * 插入补全项
     */
    fun insertCompletion(item: CompletionItem) {
        val cursor = selectionStart
        val source = text.toString()

        // 删除当前词
        var wordStart = cursor
        while (wordStart > 0) {
            val c = source[wordStart - 1]
            if (c.isLetterOrDigit() || c == '_') {
                wordStart--
            } else {
                break
            }
        }

        // 插入补全文本
        text.replace(wordStart, cursor, item.text)
        // 光标移到插入文本末尾
        setSelection(wordStart + item.text.length)
    }

    // ============ 文本操作 ============

    /**
     * 插入文本到光标位置
     */
    fun insertTextAtCursor(insertText: String) {
        val start = selectionStart
        val end = selectionEnd
        text.replace(start, end, insertText)
        setSelection(start + insertText.length)
    }

    /**
     * 在每行开头插入文本（多行缩进）
     */
    fun indentSelection() {
        val start = selectionStart
        val end = selectionEnd
        val source = text.toString()

        // 获取选中区域的行范围
        val lineStart = source.lastIndexOf('\n', start - 1) + 1
        val sb = StringBuilder(source)
        var pos = lineStart
        while (pos <= end && pos < sb.length) {
            sb.insert(pos, "    ")
            // 移动到下一行开头
            val nextLine = sb.indexOf('\n', pos + 4)
            if (nextLine == -1) break
            pos = nextLine + 1
        }

        setText(sb)
        setSelection(start + 4, end + 4)
    }

    /**
     * 减少缩进
     */
    fun outdentSelection() {
        val start = selectionStart
        val end = selectionEnd
        val source = text.toString()

        val lineStart = source.lastIndexOf('\n', start - 1) + 1
        val sb = StringBuilder(source)
        var pos = lineStart
        while (pos <= end && pos < sb.length) {
            if (pos + 4 <= sb.length && sb.substring(pos, pos + 4) == "    ") {
                sb.delete(pos, pos + 4)
            }
            val nextLine = sb.indexOf('\n', pos)
            if (nextLine == -1) break
            pos = nextLine + 1
        }

        setText(sb)
        setSelection(max(0, start - 4), max(0, end - 4))
    }

    /**
     * 注释/取消注释当前行
     */
    fun toggleComment() {
        val start = selectionStart
        val end = selectionEnd
        val source = text.toString()

        val lineStart = source.lastIndexOf('\n', start - 1) + 1
        val lineEnd = source.indexOf('\n', start).let { if (it == -1) source.length else it }
        val line = source.substring(lineStart, lineEnd)

        val sb = StringBuilder(source)
        if (line.startsWith("#")) {
            sb.delete(lineStart, lineStart + 1)
        } else {
            sb.insert(lineStart, "#")
        }

        setText(sb)
        setSelection(lineStart + 1, lineEnd + 1)
    }

    /**
     * 跳转到指定行
     */
    fun gotoLine(lineNumber: Int) {
        val source = text.toString()
        var offset = 0
        var line = 1
        while (line < lineNumber && offset < source.length) {
            val idx = source.indexOf('\n', offset)
            if (idx == -1) break
            offset = idx + 1
            line++
        }
        setSelection(offset)
        requestFocus()
    }

    /**
     * 获取当前行号
     */
    fun getCurrentLineNumber(): Int {
        val offset = selectionStart
        val source = text.toString()
        var line = 1
        for (i in 0 until offset.coerceAtMost(source.length)) {
            if (source[i] == '\n') line++
        }
        return line
    }

    /**
     * 重置修改标记
     */
    fun markClean() {
        isDirty = false
        onTextChangedListener?.invoke(false)
    }

    /**
     * 设置文件内容（不触发高亮去抖）
     */
    fun setFileContent(content: String) {
        setText(content)
        isDirty = false
        applyHighlighting()
    }

    /**
     * 获取文件内容
     */
    fun getFileContent(): String = text.toString()
}
