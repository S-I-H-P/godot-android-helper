package com.godot.devassistant.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.Editable
import android.text.InputType
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.util.Log
import android.widget.EditText
import com.godot.devassistant.model.CompletionItem
import com.godot.devassistant.model.HighlightRange
import com.godot.devassistant.native.NativeLib
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * GDScript 代码编辑器视图
 *
 * 关键点：
 * 1. 语法高亮直接修改 Editable 的 span，不调用 setText（否则光标被重置）
 * 2. 所有原生库调用都放到 IO 线程，绝不阻塞主线程（否则 ANR）
 * 3. 代码补全为【手动触发】——由键盘栏的「补全」按键调用 requestCompletionsNow()
 * 4. 传给原生的文本做长度截断，避免大文件 tokenize 卡死
 */
class GDScriptEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : EditText(context, attrs) {

    companion object {
        private const val TAG = "GDScriptEditor"
        private val gson = Gson()

        /** 传给原生的最大字符数（只取光标前的一段，够词法分析用） */
        private const val MAX_NATIVE_CHARS = 4000

        private const val COLOR_BACKGROUND = 0xFF1E1E2E.toInt()
        private const val COLOR_TEXT = 0xFFCDD6F4.toInt()
        private const val COLOR_KEYWORD = 0xFFC792EA.toInt()
        private const val COLOR_TYPE = 0xFFFFCB6B.toInt()
        private const val COLOR_STRING = 0xFFA5E075.toInt()
        private const val COLOR_COMMENT = 0xFF6272A4.toInt()
        private const val COLOR_NUMBER = 0xFFF78C6C.toInt()
        private const val COLOR_FUNCTION = 0xFF82AAFF.toInt()
        private const val COLOR_LINE_NUMBER = 0xFF4A4A6A.toInt()
        private const val COLOR_CURRENT_LINE = 0xFF2A2A3E.toInt()
    }

    var showLineNumbers: Boolean = true
    var autoCompleteEnabled: Boolean = true
    var onCompletionRequest: (List<CompletionItem>) -> Unit = {}
    var onTextChangedListener: ((Boolean) -> Unit)? = null
    var isModified: Boolean = false
        private set

    /** 后台任务作用域（主线程调度，计算切到 IO） */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var highlightTask: Runnable? = null

    /** 高亮请求序号：只有最新一次的结果才会被应用，避免旧结果覆盖新结果 */
    private var highlightVersion = 0

    /** 补全请求序号 */
    private var completionVersion = 0

    private val lineNumberPaint = Paint().apply {
        color = COLOR_LINE_NUMBER
        textSize = 13f
        textAlign = Paint.Align.RIGHT
    }

    private val currentLinePaint = Paint().apply {
        color = COLOR_CURRENT_LINE
    }

    init {
        setTextColor(COLOR_TEXT)
        setBackgroundColor(COLOR_BACKGROUND)
        typeface = android.graphics.Typeface.MONOSPACE
        textSize = 14f

        inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS

        setHorizontallyScrolling(true)
        isVerticalScrollBarEnabled = true
        isHorizontalScrollBarEnabled = true

        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                isModified = true
                onTextChangedListener?.invoke(true)
                // 只保留高亮防抖；补全已改为手动触发
                scheduleHighlight()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    override fun onDraw(canvas: Canvas) {
        if (layout != null) {
            try {
                drawCurrentLineHighlight(canvas)
                drawLineNumbers(canvas)
            } catch (e: Throwable) {
                // 忽略绘制异常
            }
        }
        super.onDraw(canvas)
    }

    private fun drawCurrentLineHighlight(canvas: Canvas) {
        val len = text?.length ?: 0
        val pos = selectionStart
        if (pos < 0 || pos > len) return
        val line = layout?.getLineForOffset(pos) ?: return
        val lineTop = layout.getLineTop(line).toFloat()
        val lineBottom = layout.getLineBottom(line).toFloat()
        val left = if (showLineNumbers) lineNumberWidth() else 0f
        canvas.drawRect(left, lineTop, width.toFloat(), lineBottom, currentLinePaint)
    }

    private fun drawLineNumbers(canvas: Canvas) {
        if (!showLineNumbers || layout == null) return

        val firstLine = layout.getLineForVertical(scrollY)
        val lastLine = minOf(layout.getLineForVertical(scrollY + height), layout.lineCount - 1)
        val lnWidth = lineNumberWidth()

        for (line in firstLine..lastLine) {
            val lineTop = layout.getLineTop(line).toFloat()
            val lineBottom = layout.getLineBottom(line).toFloat()
            val fm = lineNumberPaint.fontMetrics
            val baseline = lineTop + (lineBottom - lineTop - (fm.descent - fm.ascent)) / 2 - fm.ascent
            canvas.drawText((line + 1).toString(), lnWidth - 12f, baseline, lineNumberPaint)
        }
    }

    private fun lineNumberWidth(): Float {
        if (!showLineNumbers) return 0f
        val lineCount = layout?.lineCount ?: 1
        val digits = max(2, lineCount.toString().length)
        return 40f + digits * 8f
    }

    // ==================== 语法高亮（后台线程） ====================

    private fun scheduleHighlight() {
        highlightTask?.let { removeCallbacks(it) }
        highlightTask = Runnable { applyHighlighting() }
        postDelayed(highlightTask, 300)
    }

    /**
     * 应用语法高亮
     *
     * 计算在 IO 线程完成，回到主线程只做 span 增删，不重建文本。
     */
    fun applyHighlighting() {
        val source = text?.toString() ?: return
        if (source.isEmpty()) return

        val myVersion = ++highlightVersion

        scope.launch {
            val json = withContext(Dispatchers.IO) {
                try {
                    NativeLib.highlightCode(source)
                } catch (e: Throwable) {
                    null
                }
            } ?: return@launch

            // 期间又有新的高亮请求，丢弃本次结果
            if (myVersion != highlightVersion) return@launch

            try {
                val ranges: List<HighlightRange> = gson.fromJson(
                    json,
                    object : TypeToken<List<HighlightRange>>() {}.type
                ) ?: return@launch

                val editable: Editable = text ?: return@launch
                // 文本可能在计算期间被改短了
                if (editable.length != source.length) return@launch

                for (span in editable.getSpans(0, editable.length, ForegroundColorSpan::class.java)) {
                    editable.removeSpan(span)
                }

                for (range in ranges) {
                    val start = range.start.coerceIn(0, editable.length)
                    val end = range.end.coerceIn(start, editable.length)
                    if (start >= end) continue

                    val color = when (range.type) {
                        1 -> COLOR_KEYWORD
                        2 -> COLOR_TYPE
                        3 -> COLOR_FUNCTION
                        4 -> COLOR_COMMENT
                        5 -> COLOR_STRING
                        6 -> COLOR_NUMBER
                        9 -> COLOR_TYPE
                        10 -> COLOR_NUMBER
                        else -> COLOR_TEXT
                    }
                    editable.setSpan(
                        ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            } catch (e: Throwable) {
                // 忽略高亮错误
            }
        }
    }

    // ==================== 代码补全（手动触发） ====================

    /**
     * 手动请求一次代码补全（由键盘栏「补全」按键调用）
     *
     * 计算在 IO 线程，绝不阻塞主线程。
     */
    fun requestCompletionsNow() {
        if (!autoCompleteEnabled) return

        val cursor = selectionStart
        val source = text?.toString() ?: return
        if (cursor < 0 || cursor > source.length) return

        val prefix = source.substring(0, cursor)
        if (prefix.isEmpty() || prefix.endsWith("\n")) return

        val currentWord = extractCurrentWord(source, cursor)

        // 只把光标前最近的一段发给原生，避免大文件 tokenize 卡死
        val nativeInput = if (prefix.length > MAX_NATIVE_CHARS) {
            prefix.substring(prefix.length - MAX_NATIVE_CHARS)
        } else {
            prefix
        }

        val myVersion = ++completionVersion

        scope.launch {
            val json = withContext(Dispatchers.IO) {
                try {
                    NativeLib.getCompletions(nativeInput)
                } catch (e: Throwable) {
                    null
                }
            } ?: return@launch

            if (myVersion != completionVersion) return@launch

            try {
                val items: List<CompletionItem> = gson.fromJson(
                    json,
                    object : TypeToken<List<CompletionItem>>() {}.type
                ) ?: return@launch

                // 光标已移动则丢弃
                if (selectionStart != cursor) return@launch

                if (items.isNotEmpty()) {
                    onCompletionRequest(items)
                } else if (currentWord.isNotEmpty()) {
                    // 没有补全项时给个提示，让用户知道按键生效了
                    onCompletionRequest(emptyList())
                }
            } catch (e: Throwable) {
                Log.e(TAG, "补全解析失败", e)
            }
        }
    }

    /** 提取光标前的当前单词 */
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

    fun insertCompletion(item: CompletionItem) {
        val cursor = selectionStart
        val source = text.toString()

        var wordStart = cursor
        while (wordStart > 0) {
            val c = source[wordStart - 1]
            if (c.isLetterOrDigit() || c == '_') wordStart-- else break
        }

        text.replace(wordStart, cursor, item.text)
        setSelection(wordStart + item.text.length)
    }

    // ==================== 文本操作 ====================

    fun insertTextAtCursor(insertText: String) {
        val start = selectionStart
        val end = selectionEnd
        text.replace(start, end, insertText)
        setSelection(start + insertText.length)
    }

    fun indentSelection() {
        val start = selectionStart
        val end = selectionEnd
        if (start < 0 || end < 0 || start >= text.length) return
        val source = text.toString()

        val lineStart = source.lastIndexOf('\n', start - 1) + 1
        val sb = StringBuilder(source)
        var pos = lineStart
        while (pos <= end && pos < sb.length) {
            sb.insert(pos, "    ")
            val nextLine = sb.indexOf('\n', pos + 4)
            if (nextLine == -1) break
            pos = nextLine + 1
        }
        val cursor = selectionStart
        setText(sb)
        setSelection(cursor + 4)
    }

    fun outdentSelection() {
        val start = selectionStart
        val end = selectionEnd
        if (start < 0 || end < 0 || start >= text.length) return
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
        val cursor = selectionStart
        setText(sb)
        setSelection(max(0, cursor - 4))
    }

    fun toggleComment() {
        val start = selectionStart
        if (start < 0 || start >= text.length) return
        val source = text.toString()

        val lineStart = source.lastIndexOf('\n', start - 1) + 1
        val lineEnd = source.indexOf('\n', start).let { if (it == -1) source.length else it }
        val line = source.substring(lineStart, lineEnd)

        val sb = StringBuilder(source)
        val delta: Int
        if (line.startsWith("#")) {
            sb.delete(lineStart, lineStart + 1)
            delta = -1
        } else {
            sb.insert(lineStart, "#")
            delta = 1
        }
        val cursor = selectionStart
        setText(sb)
        setSelection(max(0, cursor + delta))
    }

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

    fun getCurrentLineNumber(): Int {
        val offset = selectionStart
        val source = text.toString()
        var line = 1
        for (i in 0 until offset.coerceAtMost(source.length)) {
            if (source[i] == '\n') line++
        }
        return line
    }

    fun markClean() {
        isModified = false
        onTextChangedListener?.invoke(false)
    }

    /** 设置文件内容（仅用于首次打开文件） */
    fun setFileContent(content: String) {
        setText(content)
        isModified = false
        setSelection(0)
        applyHighlighting()
    }

    fun getFileContent(): String = text.toString()
}
