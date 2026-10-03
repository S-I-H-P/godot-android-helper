package com.godot.devassistant.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.Editable
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.widget.EditText
import com.godot.devassistant.model.CompletionItem
import com.godot.devassistant.model.HighlightRange
import com.godot.devassistant.native.NativeLib
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlin.math.max

/**
 * GDScript 代码编辑器视图
 *
 * 注意：所有原生库调用都用 Throwable 捕获，
 * 因为 UnsatisfiedLinkError 属于 Error 而非 Exception。
 */
class GDScriptEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : EditText(context, attrs) {

    companion object {
        private val gson = Gson()

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

    private var highlightTask: Runnable? = null
    private var completionRunnable: Runnable? = null
    private var lastTextForCompletion = ""

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
                scheduleHighlight()
                scheduleCompletion()
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

    private fun scheduleHighlight() {
        highlightTask?.let { removeCallbacks(it) }
        highlightTask = Runnable { applyHighlighting() }
        postDelayed(highlightTask, 200)
    }

    /** 应用语法高亮 */
    fun applyHighlighting() {
        val source = text.toString()
        if (source.isEmpty()) return

        try {
            val json = NativeLib.highlightCode(source)
            val ranges: List<HighlightRange> = gson.fromJson(
                json,
                object : TypeToken<List<HighlightRange>>() {}.type
            ) ?: return

            val spannable = SpannableStringBuilder(source)

            for (span in spannable.getSpans(0, spannable.length, ForegroundColorSpan::class.java)) {
                spannable.removeSpan(span)
            }

            for (range in ranges) {
                val start = range.start.coerceIn(0, source.length)
                val end = range.end.coerceIn(start, source.length)
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
                spannable.setSpan(
                    ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            setText(spannable, BufferType.SPANNABLE)
        } catch (e: Throwable) {
            // 高亮失败（含原生库缺失）时保留原文
        }
    }

    private fun scheduleCompletion() {
        if (!autoCompleteEnabled) return
        completionRunnable?.let { removeCallbacks(it) }
        completionRunnable = Runnable { requestCompletions() }
        postDelayed(completionRunnable, 300)
    }

    private fun requestCompletions() {
        val cursor = selectionStart
        val source = text.toString()
        if (cursor < 0 || cursor > source.length) return

        val prefix = source.substring(0, cursor)
        if (prefix.isEmpty() || prefix.endsWith("\n")) return

        val currentWord = extractCurrentWord(source, cursor)
        if (currentWord.isEmpty()) return

        if (prefix == lastTextForCompletion) return
        lastTextForCompletion = prefix

        try {
            val json = NativeLib.getCompletions(prefix)
            val items: List<CompletionItem> = gson.fromJson(
                json,
                object : TypeToken<List<CompletionItem>>() {}.type
            ) ?: return
            if (items.isNotEmpty()) {
                onCompletionRequest(items)
            }
        } catch (e: Throwable) {
            // 忽略补全错误
        }
    }

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
        setText(sb)
        setSelection(start + 4, end + 4)
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
        setText(sb)
        setSelection(max(0, start - 4), max(0, end - 4))
    }

    fun toggleComment() {
        val start = selectionStart
        if (start < 0 || start >= text.length) return
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

    fun setFileContent(content: String) {
        setText(content)
        isModified = false
        applyHighlighting()
    }

    fun getFileContent(): String = text.toString()
}
