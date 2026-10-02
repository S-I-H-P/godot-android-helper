/**
#include <unordered_set>
#include <sstream>
#include <unordered_set>
#include <sstream>
 * @file syntax_highlighter.h
 * @brief GDScript 语法高亮器：为编辑器提供着色信息
 * @author Agnes Assistant
 */

#pragma once

#include <string>
#include <vector>
#include "tokenizer.h"

namespace godot {

/**
 * @brief 高亮类型枚举
 */
enum class HighlightType : uint8_t {
    NONE,
    KEYWORD,       // 关键字：if, func, var 等
    TYPE,          // 类型：int, String, Node 等
    BUILTIN_FUNC,  // 内置函数：print(), node.get_node() 等
    COMMENT,       // 注释
    STRING,        // 字符串
    NUMBER,        // 数字
    SIGNAL,        // 信号：signal xxx
    SIGNAL_CONN,   // 连接调用：connect(), emit_signal()
    CLASS_DEF,     // 类定义：class_name, extends
    CONSTANT,
    NULL_VALUE,      // 常量
    IDENTIFIER,    // 普通标识符
    OPERATOR,      // 运算符
    PUNCTUATION,   // 标点符号
};

/**
 * @brief 高亮范围
 */
struct HighlightRange {
    int startOffset;   // 起始偏移
    int endOffset;     // 结束偏移
    HighlightType type;
};

/**
 * @brief GDScript 语法高亮器
 */
class SyntaxHighlighter {
public:
    /**
     * @brief 对源代码进行语法高亮分析
     * @param source 源代码
     * @return 高亮范围列表
     */
    static std::vector<HighlightRange> highlight(const std::string& source);

    /**
     * @brief 获取高亮类型的颜色值（HTML 格式）
     */
    static std::string getHighlightColor(HighlightType type);

    /**
     * @brief 将高亮结果转换为 HTML（用于预览）
     */
    static std::string toHtml(const std::string& source);

private:
    static std::unordered_set<std::string> s_builtinFunctions;
    static std::unordered_set<std::string> s_builtinSignals;
    static void initBuiltinFunctions();
};

} // namespace godot
