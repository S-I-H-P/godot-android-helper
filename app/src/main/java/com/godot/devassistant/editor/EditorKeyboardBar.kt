package com.godot.devassistant.editor

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.godot.devassistant.R

/**
 * 编辑器键盘上方工具栏
 *
 * 类似安卓版 Godot 编辑器的快捷按键栏，
 * 在输入法上方显示常用按键：Tab, Shift, 方向键, 括号, 引号等。
 */
class EditorKeyboardBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    /** 按键点击回调 */
    var onKeyPressed: (KeyType, String) -> Unit = { _, _ -> }

    /** Shift 状态 */
    private var shiftOn = false

    /** 定义所有按键 */
    private val keys = listOf(
        KeyDef("Tab", KeyType.TAB, "\t"),
        KeyDef("⇧", KeyType.SHIFT, ""),
        KeyDef("←", KeyType.ARROW_LEFT, ""),
        KeyDef("→", KeyType.ARROW_RIGHT, ""),
        KeyDef("↑", KeyType.ARROW_UP, ""),
        KeyDef("↓", KeyType.ARROW_DOWN, ""),
        KeyDef("{", KeyType.TEXT, "{"),
        KeyDef("}", KeyType.TEXT, "}"),
        KeyDef("(", KeyType.TEXT, "("),
        KeyDef(")", KeyType.TEXT, ")"),
        KeyDef("[", KeyType.TEXT, "["),
        KeyDef("]", KeyType.TEXT, "]"),
        KeyDef("\"", KeyType.TEXT, "\""),
        KeyDef("'", KeyType.TEXT, "'"),
        KeyDef(":", KeyType.TEXT, ":"),
        KeyDef(".", KeyType.TEXT, "."),
        KeyDef(",", KeyType.TEXT, ","),
        KeyDef("=", KeyType.TEXT, "="),
        KeyDef("!", KeyType.TEXT, "!"),
        KeyDef("<", KeyType.TEXT, "<"),
        KeyDef(">", KeyType.TEXT, ">"),
        KeyDef("&", KeyType.TEXT, "&"),
        KeyDef("|", KeyType.TEXT, "|"),
        KeyDef("+", KeyType.TEXT, "+"),
        KeyDef("-", KeyType.TEXT, "-"),
        KeyDef("*", KeyType.TEXT, "*"),
        KeyDef("/", KeyType.TEXT, "/"),
        KeyDef("%", KeyType.TEXT, "%"),
        KeyDef("#", KeyType.TEXT, "#"),
        KeyDef("@", KeyType.TEXT, "@"),
        KeyDef("$", KeyType.TEXT, "$"),
        KeyDef("_", KeyType.TEXT, "_"),
        KeyDef("\\n", KeyType.NEWLINE, "\n"),
        KeyDef("Del", KeyType.DELETE, ""),
        KeyDef("←Del", KeyType.BACKSPACE, "")
    )

    init {
        orientation = HORIZONTAL
        setupViews()
    }

    private fun setupViews() {
        // 使用 HorizontalScrollView 包裹，支持滑动查看更多按键
        val scrollView = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        for (keyDef in keys) {
            val button = MaterialButton(context).apply {
                text = keyDef.label
                textSize = 14f
                minHeight = 0
                minimumHeight = 0
                setPadding(12, 8, 12, 8)
                layoutParams = LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ).apply {
                    marginEnd = 4
                }
                setOnClickListener {
                    handleKeyPress(keyDef)
                }
                // Shift 键特殊样式
                if (keyDef.type == KeyType.SHIFT) {
                    id = R.id.editor_btn_shift
                }
            }
            row.addView(button)
        }

        scrollView.addView(row)
        addView(scrollView)
    }

    /**
     * 处理按键点击
     */
    private fun handleKeyPress(keyDef: KeyDef) {
        when (keyDef.type) {
            KeyType.SHIFT -> {
                shiftOn = !shiftOn
                // 更新 Shift 按钮样式
                findViewById<MaterialButton>(R.id.editor_btn_shift)?.let { btn ->
                    btn.isChecked = shiftOn
                }
                onKeyPressed(KeyType.SHIFT, "")
            }
            KeyType.TAB -> onKeyPressed(KeyType.TAB, "\t")
            KeyType.NEWLINE -> onKeyPressed(KeyType.NEWLINE, "\n")
            KeyType.BACKSPACE -> onKeyPressed(KeyType.BACKSPACE, "")
            KeyType.DELETE -> onKeyPressed(KeyType.DELETE, "")
            KeyType.ARROW_LEFT -> onKeyPressed(KeyType.ARROW_LEFT, "")
            KeyType.ARROW_RIGHT -> onKeyPressed(KeyType.ARROW_RIGHT, "")
            KeyType.ARROW_UP -> onKeyPressed(KeyType.ARROW_UP, "")
            KeyType.ARROW_DOWN -> onKeyPressed(KeyType.ARROW_DOWN, "")
            KeyType.TEXT -> {
                val text = if (shiftOn) keyDef.text.uppercase() else keyDef.text
                onKeyPressed(KeyType.TEXT, text)
                if (shiftOn) {
                    shiftOn = false
                    findViewById<MaterialButton>(R.id.editor_btn_shift)?.isChecked = false
                }
            }
        }
    }

    /** 按键类型 */
    enum class KeyType {
        TAB, SHIFT, TEXT, NEWLINE, BACKSPACE, DELETE,
        ARROW_LEFT, ARROW_RIGHT, ARROW_UP, ARROW_DOWN
    }

    /** 按键定义 */
    private data class KeyDef(
        val label: String,
        val type: KeyType,
        val text: String
    )
}
