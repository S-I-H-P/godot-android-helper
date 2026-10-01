/**
 * @file doc_indexer.h
 * @brief Godot 离线文档索引器：解析并索引内置的 HTML 文档
 * @author Agnes Assistant
 */

#pragma once

#include <string>
#include <vector>
#include <unordered_map>

namespace godot {

/**
 * @brief 文档条目
 */
struct DocEntry {
    std::string id;              // 文档唯一标识
    std::string title;           // 标题
    std::string path;            // 在 assets 中的相对路径
    std::string content;         // 文档内容（HTML）
    std::vector<std::string> tags;  // 标签（用于搜索）
    int level;                   // 层级深度
};

/**
 * @brief Godot 离线文档索引器
 */
class DocIndexer {
public:
    /**
     * @brief 初始化文档索引（从 assets 加载）
     * @param assetsDir assets 目录路径
     */
    static void initialize(const std::string& assetsDir);

    /**
     * @brief 根据关键词搜索文档
     */
    static std::vector<DocEntry> search(const std::string& keyword);

    /**
     * @brief 根据 ID 获取文档内容
     */
    static std::string getDocument(const std::string& docId);

    /**
     * @brief 获取所有文档 ID 列表
     */
    static std::vector<std::string> getAllDocIds();

    /**
     * @brief 获取文档树结构
     */
    static std::vector<DocEntry> getDocTree();

private:
    static std::unordered_map<std::string, DocEntry> s_docIndex;
    static std::vector<DocEntry> s_docTree;
    static bool s_initialized;

    /**
     * @brief 从 assets 目录递归加载文档
     */
    static void loadDocsRecursive(const std::string& dirPath, int level);

    /**
     * @brief 解析单个 HTML 文档
     */
    static DocEntry parseDocFile(const std::string& path);
};

} // namespace godot
