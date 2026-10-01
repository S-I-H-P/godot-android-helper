/**
 * @file syntax_highlighter.cpp
 * @brief GDScript 语法高亮器实现
 * @author Agnes Assistant
 */

#include "godot/syntax_highlighter.h"
#include <unordered_set>

namespace godot {

// 内置函数集合（简化版）
std::unordered_set<std::string> SyntaxHighlighter::s_builtinFunctions = {
    "print", "len", "str", "int", "float", "bool", "abs", "min", "max",
    "typeof", "is_instance_of", "preload", "load", "assert",
    "nodepath", "yield", "await", "call_deferred", "delay_call",
    "set_breakpoint", "remove_breakpoint", "has_breakpoint",
    "parse_json", "to_json", "weakref", "dict_from_array",
    "range", "pi", "inf", "nan", "sin", "cos", "tan", "asin", "acos",
    "atan", "atan2", "sqrt", "pow", "log", "floor", "ceil", "round",
    "randf", "randi", "seed", "randomize", "is_inf", "is_nan",
    "Color8", "Vector2", "Vector3", "Vector4", "Color", "Rect2",
    "Rect2i", "AABB", "Plane", "Quaternion", "Mat2", "Mat3", "Mat4",
    "Transform2D", "Transform3D", "Projection", "Basis",
    "get_slice", "count", "find", "rfind", "match", "regex_match",
    "regex_search", "begins_with", "ends_with", "hash", "is_empty",
    "is_subsequence_of", "to_lower", "to_upper", "capitalize",
    "num", "num_int", "num_float", "chars_to_int", "chr",
    "get_script", "has_method", "call", "callv", "get", "set",
    "instance_from_id", "get_instance_id", "is_connected",
    "connect", "disconnect", "emit_signal", "get_signal_list",
    "get_signal_connection_list", "get_script", "has_signal",
    "get_indexed", "set_indexed", "is_ready", "_ready"
};

std::unordered_set<std::string> SyntaxHighlighter::s_builtinSignals = {
    "body_entered", "body_exited", "area_entered", "area_exited",
    "mouse_entered", "mouse_exited", "focus_entered", "focus_exited",
    "pressed", "toggled", "item_selected", "item_activated",
    "popup_hide", "popup_show", "timeout", "value_changed",
    "changed", "text_changed", "text_set", "focus_entered",
    "focus_exited", "gui_input", "unhandled_input",
    "tree_selection_changed", "tree_item_selected",
    "tree_item_activated", "drag_started", "drag_ended",
    "custom_action", "pressed", "released", "shortcuts_changed"
};

void SyntaxHighlighter::initBuiltinFunctions() {
    // 已在类外初始化
}

std::vector<HighlightRange> SyntaxHighlighter::highlight(const std::string& source) {
    std::vector<HighlightRange> ranges;

    // 简单的行级高亮（实际生产环境应使用完整的 token 流）
    std::istringstream stream(source);
    std::string line;
    int lineNum = 0;
    int offset = 0;

    while (std::getline(stream, line)) {
        lineNum++;
        int lineStart = offset;

        // 注释
        size_t commentPos = line.find('#');
        if (commentPos != std::string::npos) {
            ranges.push_back({lineStart + commentPos, lineStart + line.size(), HighlightType::COMMENT});
        }

        // 字符串（简化处理）
        for (size_t i = 0; i < line.size(); i++) {
            if (line[i] == '"') {
                size_t start = i;
                i++;
                while (i < line.size() && line[i] != '"') i++;
                if (i < line.size()) i++;
                ranges.push_back({lineStart + start, lineStart + i, HighlightType::STRING});
                i--;  // for 循环会再++
            }
        }

        // 关键字高亮（简化）
        static const std::vector<std::pair<std::string, HighlightType>> keywordPairs = {
            {"if", HighlightType::KEYWORD}, {"elif", HighlightType::KEYWORD},
            {"else", HighlightType::KEYWORD}, {"while", HighlightType::KEYWORD},
            {"for", HighlightType::KEYWORD}, {"in", HighlightType::KEYWORD},
            {"break", HighlightType::KEYWORD}, {"continue", HighlightType::KEYWORD},
            {"pass", HighlightType::KEYWORD}, {"return", HighlightType::KEYWORD},
            {"class", HighlightType::KEYWORD}, {"var", HighlightType::KEYWORD},
            {"const", HighlightType::KEYWORD}, {"func", HighlightType::KEYWORD},
            {"extends", HighlightType::CLASS_DEF}, {"self", HighlightType::KEYWORD},
            {"true", HighlightType::CONSTANT}, {"false", HighlightType::CONSTANT},
            {"null", HighlightType::NULL_VALUE}, {"async", HighlightType::KEYWORD},
            {"await", HighlightType::KEYWORD}, {"match", HighlightType::KEYWORD},
            {"when", HighlightType::KEYWORD}, {"and", HighlightType::KEYWORD},
            {"or", HighlightType::KEYWORD}, {"not", HighlightType::KEYWORD},
            {"is", HighlightType::KEYWORD}
        };

        for (const auto& pair : keywordPairs) {
            size_t pos = 0;
            while ((pos = line.find(pair.first, pos)) != std::string::npos) {
                // 检查是否为完整单词
                bool isWord = true;
                if (pos > 0 && (std::isalnum(line[pos - 1]) || line[pos - 1] == '_')) {
                    isWord = false;
                }
                if (pos + pair.first.size() < line.size() &&
                    (std::isalnum(line[pos + pair.first.size()]) || line[pos + pair.first.size()] == '_')) {
                    isWord = false;
                }
                if (isWord) {
                    ranges.push_back({lineStart + pos, lineStart + pos + pair.first.size(), pair.second});
                }
                pos += pair.first.size();
            }
        }

        offset += line.size() + 1;  // +1 for newline
    }

    return ranges;
}

std::string SyntaxHighlighter::getHighlightColor(HighlightType type) {
    switch (type) {
        case HighlightType::KEYWORD:       return "#C678DD";  // 紫色
        case HighlightType::TYPE:          return "#E5C07B";  // 黄色
        case HighlightType::BUILTIN_FUNC:  return "#61AFEF";  // 蓝色
        case HighlightType::COMMENT:       return "#5C6370";  // 灰色
        case HighlightType::STRING:        return "#98C379";  // 绿色
        case HighlightType::NUMBER:        return "#D19A66";  // 橙色
        case HighlightType::SIGNAL:        return "#E06C75";  // 红色
        case HighlightType::CLASS_DEF:     return "#61AFEF";  // 蓝色
        case HighlightType::CONSTANT:      return "#D19A66";  // 橙色
        case HighlightType::OPERATOR:      return "#56B6C2";  // 青色
        case HighlightType::PUNCTUATION:   return "#ABB2BF";  // 浅灰
        default:                           return "#ABB2BF";
    }
}

std::string SyntaxHighlighter::toHtml(const std::string& source) {
    auto ranges = highlight(source);
    std::string html = "<pre style=\"background:#282C34;color:#ABB2BF;font-family:Consolas,Monaco,monospace;font-size:14px;padding:10px;\">";

    // 简单实现：按范围分段着色
    int lastEnd = 0;
    for (const auto& range : ranges) {
        // 普通文本
        if (range.startOffset > lastEnd) {
            html += source.substr(lastEnd, range.startOffset - lastEnd);
        }
        // 着色文本
        html += "<span style=\"color:" + getHighlightColor(range.type) + "\">";
        html += source.substr(range.startOffset, range.endOffset - range.startOffset);
        html += "</span>";
        lastEnd = range.endOffset;
    }
    // 剩余文本
    if (lastEnd < (int)source.size()) {
        html += source.substr(lastEnd);
    }

    html += "</pre>";
    return html;
}

} // namespace godot
