/**
 * @file completion_engine.cpp
 * @brief GDScript 代码补全引擎实现
 */

#include "godot/completion_engine.h"
#include <algorithm>
#include <sstream>
#include <set>

namespace godot {

// 静态成员初始化
std::vector<std::string> CompletionEngine::s_keywords;
std::vector<std::string> CompletionEngine::s_builtinTypes;
std::unordered_map<std::string, std::vector<std::string>> CompletionEngine::s_classMethods;
std::vector<std::string> CompletionEngine::s_callbacks;

/** 一次最多返回的候选数量 */
static const size_t kMaxResults = 80;

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
        "true", "false", "null",
        "await", "match", "when",
        "self", "super", "and", "or", "not", "is",
        "static", "signal", "enum", "preload", "load", "assert",
        "print", "push_error", "push_warning", "range", "len",
        "typeof", "instanceof"
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
        "Label", "Button", "LineEdit", "TextEdit", "RichTextLabel",
        "Timer", "Camera2D", "Camera3D",
        "AudioStream", "AudioStreamPlayer",
        "Input", "InputEvent", "InputEventKey", "InputEventMouseButton",
        "Resource", "Script", "Callable", "Signal",
        "Dictionary", "Array", "StringName", "NodePath", "RID", "Variant",
        "PackedByteArray", "PackedInt32Array", "PackedFloat32Array",
        "PackedStringArray", "PackedVector2Array", "PackedVector3Array"
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
        "set_process", "set_physics_process", "get_index",
        "add_to_group", "is_in_group", "remove_from_group",
        "duplicate", "get_owner", "set_owner", "print_tree"
    };
    s_classMethods["Node2D"] = {
        "position", "rotation", "scale", "global_position",
        "global_rotation", "global_scale", "z_index", "visible",
        "look_at", "move_local_x", "move_local_y",
        "to_global", "to_local", "get_angle_to", "rotate"
    };
    s_classMethods["Node3D"] = {
        "position", "rotation", "scale", "global_position",
        "global_transform", "transform", "basis", "visible",
        "look_at", "translate", "rotate_x", "rotate_y", "rotate_z",
        "to_global", "to_local"
    };
    s_classMethods["Control"] = {
        "size", "position", "global_position", "custom_minimum_size",
        "add_child", "remove_child", "get_child",
        "grab_focus", "release_focus", "has_focus", "is_focus_owner",
        "set_focus_mode", "show", "hide", "set_anchors_preset",
        "get_theme_font", "add_theme_constant_override",
        "mouse_filter", "modulate", "self_modulate"
    };
    s_classMethods["CharacterBody2D"] = {
        "move_and_slide", "velocity", "is_on_floor", "is_on_wall",
        "is_on_ceiling", "get_floor_normal", "get_wall_normal",
        "get_slide_collision_count", "get_slide_collision",
        "get_last_slide_collision", "up_direction",
        "floor_max_angle", "motion_mode", "apply_floor_snap"
    };
    s_classMethods["RigidBody2D"] = {
        "apply_impulse", "apply_force", "apply_central_impulse",
        "apply_central_force", "apply_torque", "apply_torque_impulse",
        "linear_velocity", "angular_velocity", "mass", "gravity_scale",
        "set_deferred", "sleeping", "freeze"
    };
    s_classMethods["Area2D"] = {
        "body_entered", "body_exited", "area_entered", "area_exited",
        "get_overlapping_bodies", "get_overlapping_areas",
        "monitoring", "monitorable", "has_overlapping_bodies",
        "has_overlapping_areas"
    };
    s_classMethods["Timer"] = {
        "start", "stop", "is_stopped", "wait_time", "one_shot",
        "autostart", "timeout", "paused", "time_left"
    };
    s_classMethods["AnimationPlayer"] = {
        "play", "stop", "pause", "is_playing", "current_animation",
        "play_backwards", "seek", "get_animation", "has_animation",
        "animation_finished", "animation_started", "speed_scale",
        "set_speed_scale", "queue"
    };
    s_classMethods["Sprite2D"] = {
        "texture", "modulate", "self_modulate", "flip_h", "flip_v",
        "offset", "centered", "region_enabled", "region_rect", "frame"
    };
    s_classMethods["AnimatedSprite2D"] = {
        "play", "stop", "pause", "is_playing", "animation", "frame",
        "speed_scale", "sprite_frames", "flip_h", "flip_v",
        "animation_finished", "frame_changed", "set_frame_and_progress"
    };
    s_classMethods["Input"] = {
        "is_action_pressed", "is_action_just_pressed",
        "is_action_just_released", "get_axis", "get_vector",
        "get_action_strength", "is_key_pressed", "is_mouse_button_pressed",
        "get_mouse_position", "set_mouse_mode", "get_joy_axis"
    };
    s_classMethods["Array"] = {
        "append", "push_back", "push_front", "insert", "remove_at",
        "pop_back", "pop_front", "clear", "size", "is_empty",
        "has", "find", "rfind", "count", "sort", "sort_custom",
        "shuffle", "reverse", "duplicate", "slice", "map", "filter",
        "reduce", "any", "all", "min", "max", "front", "back",
        "resize", "fill", "erase"
    };
    s_classMethods["Dictionary"] = {
        "has", "get", "set", "erase", "clear", "keys", "values",
        "size", "is_empty", "duplicate", "merge", "find_key",
        "get_or_add", "hash"
    };
    s_classMethods["String"] = {
        "length", "is_empty", "to_upper", "to_lower", "strip_edges",
        "split", "join", "replace", "find", "rfind", "substr",
        "begins_with", "ends_with", "contains", "format", "to_int",
        "to_float", "is_valid_int", "is_valid_float", "sha256_text",
        "capitalize", "left", "right", "pad_zeros", "repeat"
    };
    s_classMethods["Vector2"] = {
        "x", "y", "length", "length_squared", "normalized", "distance_to",
        "angle", "angle_to", "dot", "cross", "direction_to", "lerp",
        "move_toward", "rotated", "floor", "ceil", "round", "abs",
        "clamped", "is_normalized", "normalized"
    };
    s_classMethods["Signal"] = {
        "connect", "emit", "is_connected", "disconnect",
        "get_connections", "get_name"
    };
}

void CompletionEngine::initCallbacks() {
    s_callbacks = {
        "_ready", "_process", "_physics_process", "_exit_tree",
        "_enter_tree", "_input", "_unhandled_input", "_unhandled_key_input",
        "_gui_input", "_focus_entered", "_focus_exited",
        "_notification", "_draw", "_init",
        "_on_body_entered", "_on_body_exited",
        "_on_area_entered", "_on_area_exited",
        "_on_timer_timeout", "_on_button_pressed",
        "_on_animation_finished", "_on_visibility_changed"
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

    // 注意：partialInput 为空时不再直接返回空。
    // 光标在行首 / 缩进 / 空白处时没有前缀词，此时应给出全部候选，
    // 否则用户点「补全」会毫无反应。

    // 最后两个 token（用于判断上下文）
    std::string lastToken;
    std::string secondLastToken;
    if (!tokens.empty()) {
        lastToken = tokens.back().lexeme;
    }
    if (tokens.size() >= 2) {
        secondLastToken = tokens[tokens.size() - 2].lexeme;
    }

    // ---- 上下文 1：点号后的成员补全 ----
    if (lastToken == "." && !secondLastToken.empty()) {
        if (s_classMethods.count(secondLastToken)) {
            for (const auto& method : s_classMethods[secondLastToken]) {
                if (!partialInput.empty() &&
                    method.find(partialInput) == std::string::npos) {
                    continue;
                }
                CompletionItem item;
                item.text = method;
                item.label = method;
                item.kind = "method";
                result.push_back(item);
            }
            // 成员补全只返回方法，不混入关键字
            if (result.size() > kMaxResults) result.resize(kMaxResults);
            return result;
        }
    }

    // ---- 上下文 2：通用补全（关键字 + 类型 + 回调）----
    std::set<std::string> seen;

    auto filterItems = [&](const std::vector<CompletionItem>& items) {
        for (auto& item : items) {
            if (result.size() >= kMaxResults) break;
            if (!partialInput.empty() &&
                item.label.find(partialInput) == std::string::npos) {
                continue;
            }
            if (seen.count(item.label)) continue;
            seen.insert(item.label);
            result.push_back(item);
        }
    };

    filterItems(getKeywordCompletions());
    filterItems(getTypeCompletions());

    // 生命周期回调：始终提供（用 _ 开头时尤其有用）
    {
        for (const auto& cb : s_callbacks) {
            if (result.size() >= kMaxResults) break;
            if (!partialInput.empty() &&
                cb.find(partialInput) == std::string::npos) {
                continue;
            }
            if (seen.count(cb)) continue;
            seen.insert(cb);

            CompletionItem item;
            item.text = cb + "()";
            item.label = cb;
            item.kind = "function";
            item.documentation = "Godot 生命周期回调";
            result.push_back(item);
        }
    }

    // 去重并排序（短标签优先，便于阅读）
    std::sort(result.begin(), result.end(),
        [](const CompletionItem& a, const CompletionItem& b) {
            if (a.label.size() != b.label.size()) {
                return a.label.size() < b.label.size();
            }
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
