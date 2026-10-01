/**
 * @file completion_engine.cpp
 * @brief GDScript 代码补全引擎实现
 * @author Agnes Assistant
 */

#include "godot/completion_engine.h"
#include <algorithm>
#include <sstream>

namespace godot {

// 静态成员初始化
std::vector<std::string> CompletionEngine::s_keywords;
std::vector<std::string> CompletionEngine::s_builtinTypes;
std::unordered_map<std::string, std::vector<std::string>> CompletionEngine::s_classMethods;
std::vector<std::string> CompletionEngine::s_callbacks;

void CompletionEngine::initialize() {
    initKeywords();
    initTypes();
    initClasses();
    initCallbacks();
}

void CompletionEngine::initKeywords() {
    s_keywords = {
        "if", "elif", "else", "while", "for", "in",
        "break", "continue", "pass", "return", "yield",
        "class", "class_name", "extends", "var", "const", "func",
        "const", "true", "false", "null",
        "async", "await", "match", "when",
        "self", "super", "and", "or", "not", "is"
    };
}

void CompletionEngine::initTypes() {
    s_builtinTypes = {
        "int", "float", "bool", "String", "void", "null",
        "Vector2", "Vector2i", "Vector3", "Vector3i", "Vector4", "Vector4i",
        "Color", "Rect2", "Rect2i", "Recti",
        "AABB", "Plane", "Quaternion",
        "Mat2", "Mat3", "Mat4",
        "Transform2D", "Transform3D",
        "Projection", "Basis",
        "Node", "Node2D", "Node3D", "Control", "CanvasItem",
        "CharacterBody2D", "CharacterBody3D",
        "RigidBody2D", "RigidBody3D",
        "Area2D", "Area3D",
        "StaticBody2D", "StaticBody3D",
        "AnimatedSprite2D", "AnimatedSprite3D",
        "Sprite2D", "Sprite3D",
        "AudioStream", "Input", "InputEvent",
        "Resource", "Script", "Callable", "Signal",
        "Dictionary", "Array", "StringName", "NodePath", "RID", "Variant"
    };
}

void CompletionEngine::initClasses() {
    // 常见 Godot 类的方法列表（简化版）
    s_classMethods["Node"] = {
        "add_child", "remove_child", "get_child", "get_children",
        "get_parent", "has_node", "get_node", "get_node_or_null",
        "find_node", "get_path", "is_inside_tree", "get_tree",
        "get_root", "get_name", "set_name", "queue_free",
        "emit_signal", "connect", "disconnect", "is_connected",
        "_ready", "_process", "_physics_process", "_input",
        "_unhandled_input", "_ready", "_exit_tree", "_enter_tree"
    };
    s_classMethods["Control"] = {
        "get_size", "set_size", "get_position", "set_position",
        "get_global_position", "set_global_position",
        "add_child", "remove_child", "get_child",
        "grab_focus", "is_focus_owner", "set_focus_mode",
        "_gui_input", "_unhandled_input"
    };
    s_classMethods["CharacterBody2D"] = {
        "move_and_slide", "get_velocity", "set_velocity",
        "get_move_and_slide_angle", "is_on_floor", "is_on_wall",
        "get_floor_velocity", "get_slide_collision"
    };
    s_classMethods["Area2D"] = {
        "body_entered", "body_exit_ed", "area_entered", "area_exited",
        "get_overlapping_bodies", "get_overlapping_areas"
    };
    s_classMethods["Timer"] = {
        "start", "stop", "is_stopped", "wait_time", "one_shot",
        "autostart", "timeout"
    };
    s_classMethods["Signal"] = {
        "connect", "emit", "is_connected", "disconnect"
    };
}

void CompletionEngine::initCallbacks() {
    s_callbacks = {
        "_ready", "_process", "_physics_process", "_exit_tree",
        "_enter_tree", "_ready", "_input", "_unhandled_input",
        "_gui_input", "_unhandled_key_input", "_focus_entered",
        "_focus_exited", "_ready", "_process_input",
        "_process_delta", "_physics_process_delta",
        "_on_body_entered", "_on_body_exited",
        "_on_area_entered", "_on_area_exited",
        "_on_timer_timeout", "_ready"
    };
}

std::vector<CompletionItem> CompletionEngine::getKeywordCompletions() {
    std::vector<CompletionItem> items;
    for (const auto& kw : s_keywords) {
        CompletionItem item;
        item.text = kw;
        item.label = kw;
        item.kind = "keyword";
        items.push_back(item);
    }
    return items;
}

std::vector<CompletionItem> CompletionEngine::getTypeCompletions() {
    std::vector<CompletionItem> items;
    for (const auto& type : s_builtinTypes) {
        CompletionItem item;
        item.text = type;
        item.label = type;
        item.kind = "type";
        items.push_back(item);
    }
    return items;
}

std::vector<CompletionItem> CompletionEngine::getCompletions(
    const std::vector<Token>& tokens,
    const std::string& partialInput) {
    std::vector<CompletionItem> result;

    if (partialInput.empty()) {
        return result;
    }

    // 根据最后几个 token 判断上下文
    std::string lastToken;
    if (!tokens.empty()) {
        lastToken = tokens.back().lexeme;
    }

    // 获取所有可能的补全项
    auto keywords = getKeywordCompletions();
    auto types = getTypeCompletions();
    auto callbacks = getGodotCallbackNames();

    // 过滤匹配的项
    auto filterItems = [&](const std::vector<CompletionItem>& items) {
        for (auto& item : items) {
            if (item.label.find(partialInput) != std::string::npos) {
                result.push_back(item);
            }
        }
    };

    filterItems(keywords);
    filterItems(types);

    // 如果是回调上下文，添加生命周期方法
    bool isCallbackContext = false;
    if (tokens.size() >= 2) {
        std::string secondLast = tokens[tokens.size() - 2].lexeme;
        if (secondLast == "func") {
            isCallbackContext = true;
        }
    }

    if (isCallbackContext) {
        for (const auto& cb : callbacks) {
            CompletionItem item;
            item.text = cb + "()";
            item.label = cb;
            item.kind = "function";
            item.documentation = "Godot 生命周期回调";
            if (cb.find(partialInput) != std::string::npos) {
                result.push_back(item);
            }
        }
    }

    // 如果最后是点号，尝试类方法补全
    if (lastToken == "." && tokens.size() >= 2) {
        std::string prevToken = tokens[tokens.size() - 2].lexeme;
        if (s_classMethods.count(prevToken)) {
            for (const auto& method : s_classMethods[prevToken]) {
                CompletionItem item;
                item.text = method;
                item.label = method;
                item.kind = "method";
                if (method.find(partialInput) != std::string::npos) {
                    result.push_back(item);
                }
            }
        }
    }

    // 去重并排序
    std::sort(result.begin(), result.end(),
        [](const CompletionItem& a, const CompletionItem& b) {
            return a.label < b.label;
        });

    return result;
}

std::vector<std::string> CompletionEngine::getClassMethodCompletions(
    const std::string& className) {
    if (s_classMethods.count(className)) {
        return s_classMethods[className];
    }
    return {};
}

std::vector<std::string> CompletionEngine::getGodotCallbackNames() {
    return s_callbacks;
}

} // namespace godot
