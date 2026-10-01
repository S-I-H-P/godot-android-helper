/**
 * @file completion_engine.h
 * @brief GDScript 代码补全引擎：提供关键字、类型、方法等补全建议
 * @author Agnes Assistant
 */

#pragma once

#include <string>
#include <vector>
#include <unordered_map>
#include "tokenizer.h"

namespace godot {

/**
 * @brief 补全项
 */
struct CompletionItem {
    std::string text;              // 补全文本
    std::string label;             // 显示标签
    std::string kind;              // 类型：keyword/type/function/class/constant
    std::string documentation;     // 文档说明（可选）
    int insertTextLength;          // 插入文本长度
    int replacementLength;         // 替换长度
};

/**
 * @brief GDScript 代码补全引擎
 */
class CompletionEngine {
public:
    /**
     * @brief 初始化补全引擎（加载关键词、类型等）
     */
    static void initialize();

    /**
     * @brief 获取关键字补全列表
     */
    static std::vector<CompletionItem> getKeywordCompletions();

    /**
     * @brief 获取内置类型补全列表
     */
    static std::vector<CompletionItem> getTypeCompletions();

    /**
     * @brief 根据当前输入的 token 流获取上下文补全建议
     * @param tokens 当前行的 token 列表
     * @param partialInput 当前正在输入的词
     * @return 补全建议列表
     */
    static std::vector<CompletionItem> getCompletions(
        const std::vector<Token>& tokens,
        const std::string& partialInput);

    /**
     * @brief 获取 Godot 内置类的方法补全
     * @param className 类名
     * @return 方法名列表
     */
    static std::vector<std::string> getClassMethodCompletions(
        const std::string& className);

    /**
     * @brief 获取预定义的函数名称（如 _ready, _process 等）
     */
    static std::vector<std::string> getGodotCallbackNames();

private:
    // 内部静态数据初始化
    static void initKeywords();
    static void initTypes();
    static void initClasses();
    static void initCallbacks();

    // 静态数据
    static std::vector<std::string> s_keywords;
    static std::vector<std::string> s_builtinTypes;
    static std::unordered_map<std::string, std::vector<std::string>> s_classMethods;
    static std::vector<std::string> s_callbacks;
};

} // namespace godot
