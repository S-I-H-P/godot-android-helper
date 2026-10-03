package com.godot.devassistant.editor

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 编辑器键盘工具栏（Termux 风格 + M3E 视觉）
 *
 * 特点：
 * - 三行固定键盘，不需要滑动，所有按键一屏可见
 * - 等宽分布（每行按键平均分配宽度）
 * - M3E 风格：圆角胶囊按键、色调表面、按压涟漪
 * - CTRL / ALT 为粘滞修饰键：按下后下一个字符会转成控制字符 / ESC+字符
 * - 「补全」按键：手动触发一次代码补全（不再随输入自动触发）
 */
class EditorKeyboardBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    /** 按键回调（KeyType 与 EditorActivity 中的处理保持一致） */
    var onKeyPressed: (KeyType, String) -> Unit = { _, _ -> }

    private var ctrlOn = false
    private var altOn = false

    private var ctrlKey: TextView? = null
    private var altKey: TextView? = null

    // 颜色
    private val colorSpecialBg = 0xFF3D3D55.toInt()
    private val colorAccentBg = 0xFF4A90D9.toInt()
    private val colorCompleteBg = 0xFF2E7D5B.toInt()
    private val colorText = 0xFFCDD6F4.toInt()
    private val colorTextDim = 0xFF9CA3AF.toInt()

    /** 按键定义 */
    private data class KeyDef(
        val label: String,
        val type: KeyType,
        val text: String = "",
        val special: Boolean = false,
        val flexible: Float = 1f
    )

    private val rows: List<List<KeyDef>> = listOf(
        // 第一行：控制键 + 补全
        listOf(
            KeyDef("ESC", KeyType.TEXT, "\u001b", special = true),
            KeyDef("TAB", KeyType.TAB, "\t", special = true),
            KeyDef("CTRL", KeyType.CTRL, special = true),
            KeyDef("ALT", KeyType.ALT, special = true),
            KeyDef("补全", KeyType.COMPLETE, special = true, flexible = 1.6f),
            KeyDef("⌫", KeyType.BACKSPACE, special = true),
            KeyDef("DEL", KeyType.DELETE, special = true),
            KeyDef("←", KeyType.ARROW_LEFT, special = true),
            KeyDef("↑", KeyType.ARROW_UP, special = true),
            KeyDef("↓", KeyType.ARROW_DOWN, special = true),
            KeyDef("→", KeyType.ARROW_RIGHT, special = true)
        ),
        // 第二行：运算符与括号
        listOf(
            KeyDef("-", KeyType.TEXT, "-"),
            KeyDef("_", KeyType.TEXT, "_"),
            KeyDef("=", KeyType.TEXT, "="),
            KeyDef("+", KeyType.TEXT, "+"),
            KeyDef("{", KeyType.TEXT, "{"),
            KeyDef("}", KeyType.TEXT, "}"),
            KeyDef("[", KeyType.TEXT, "["),
            KeyDef("]", KeyType.TEXT, "]"),
            KeyDef("(", KeyType.TEXT, "("),
            KeyDef(")", KeyType.TEXT, ")"),
            KeyDef("/", KeyType.TEXT, "/"),
            KeyDef("\\", KeyType.TEXT, "\\")
        ),
        // 第三行：标点与其它
        listOf(
            KeyDef(";", KeyType.TEXT, ";"),
            KeyDef(":", KeyType.TEXT, ":"),
            KeyDef("\"", KeyType.TEXT, "\""),
            KeyDef("'", KeyType.TEXT, "'"),
            KeyDef(",", KeyType.TEXT, ","),
            KeyDef(".", KeyType.TEXT, "."),
            KeyDef("<", KeyType.TEXT, "<"),
            KeyDef(">", KeyType.TEXT, ">"),
            KeyDef("!", KeyType.TEXT, "!"),
            KeyDef("?", KeyType.TEXT, "?"),
            KeyDef("|", KeyType.TEXT, "|"),
            KeyDef("~", KeyType.TEXT, "~")
        )
    )

    init {
        orientation = VERTICAL
        setBackgroundColor(0xFF1A1A2E.toInt())
        val pad = dp(2)
        setPadding(pad, pad, pad, pad)
        buildKeys()
    }

    private fun buildKeys() {
        for (row in rows) {
            val rowLayout = LinearLayout(context).apply {
                orientation = HORIZONTAL
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
            }
            for (key in row) {
                rowLayout.addView(createKeyView(key))
            }
            addView(rowLayout)
        }
    }

    private fun createKeyView(key: KeyDef): TextView {
        val tv = TextView(context).apply {
            text = key.label
            gravity = Gravity.CENTER
            setTextColor(colorText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setBackgroundResource(
                if (key.special) com.godot.devassistant.R.drawable.bg_key_special
                else com.godot.devassistant.R.drawable.bg_key
            )
            isClickable = true
            isFocusable = true
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, key.flexible).apply {
                val m = dp(2)
                setMargins(m, m, m, m)
            }
            setOnClickListener { handleKey(key) }
        }

        // 「补全」按键用绿色底，视觉上区分开
        if (key.type == KeyType.COMPLETE) {
            tv.setBackgroundColor(colorCompleteBg)
        }

        when (key.type) {
            KeyType.CTRL -> ctrlKey = tv
            KeyType.ALT -> altKey = tv
            else -> {}
        }

        return tv
    }

    private fun handleKey(key: KeyDef) {
        when (key.type) {
            KeyType.CTRL -> {
                ctrlOn = !ctrlOn
                if (ctrlOn) altOn = false
                refreshModifierState()
                return
            }
            KeyType.ALT -> {
                altOn = !altOn
                if (altOn) ctrlOn = false
                refreshModifierState()
                return
            }
            KeyType.COMPLETE -> {
                onKeyPressed(KeyType.COMPLETE, "")
                return
            }
            else -> {}
        }

        var outText = key.text
        val outType = key.type

        // 修饰键转换（仅对普通文本键生效）
        if (key.type == KeyType.TEXT && key.text.isNotEmpty()) {
            val ch = key.text[0]
            if (ctrlOn) {
                val upper = ch.uppercaseChar()
                if (upper in 'A'..'Z') {
                    outText = ((upper.code - 'A'.code + 1).toChar()).toString()
                } else if (ch == '[') {
                    outText = "\u001b"
                } else if (ch == '\\') {
                    outText = "\u001c"
                } else if (ch == ']') {
                    outText = "\u001d"
                }
                ctrlOn = false
            } else if (altOn) {
                outText = "\u001b$ch"
                altOn = false
            }
            refreshModifierState()
        }

        onKeyPressed(outType, outText)
    }

    private fun refreshModifierState() {
        ctrlKey?.let {
            it.setBackgroundColor(if (ctrlOn) colorAccentBg else colorSpecialBg)
            it.setTextColor(if (ctrlOn) Color.WHITE else colorTextDim)
        }
        altKey?.let {
            it.setBackgroundColor(if (altOn) colorAccentBg else colorSpecialBg)
            it.setTextColor(if (altOn) Color.WHITE else colorTextDim)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    /** 按键类型 */
    enum class KeyType {
        TAB, SHIFT, TEXT, NEWLINE, BACKSPACE, DELETE,
        ARROW_LEFT, ARROW_RIGHT, ARROW_UP, ARROW_DOWN,
        CTRL, ALT,
        /** 手动触发代码补全 */
        COMPLETE
    }
}
