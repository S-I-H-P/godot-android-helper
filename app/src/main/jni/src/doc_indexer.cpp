/**
 * @file doc_indexer.cpp
 * @brief Godot 离线文档索引器实现
 * @author Agnes Assistant
 */

#include "godot/doc_indexer.h"
#include "godot/file_utils.h"
#include <fstream>
#include <sstream>
#include <regex>

namespace godot {

std::unordered_map<std::string, DocEntry> DocIndexer::s_docIndex;
std::vector<DocEntry> DocIndexer::s_docTree;
bool DocIndexer::s_initialized = false;

void DocIndexer::initialize(const std::string& assetsDir) {
    if (s_initialized) return;

    s_docIndex.clear();
    s_docTree.clear();
    loadDocsRecursive(assetsDir, 0);
    s_initialized = true;

    LOGI("文档索引初始化完成，共加载 %lu 个文档", s_docIndex.size());
}

void DocIndexer::loadDocsRecursive(const std::string& dirPath, int level) {
    auto entries = FileUtils::listDirectory(dirPath);

    for (const auto& entry : entries) {
        std::string fullPath = dirPath + "/" + entry;

        // 跳过隐藏文件
        if (entry[0] == '.') continue;

        struct stat st;
        if (stat(fullPath.c_str(), &st) != 0) continue;

        if (S_ISDIR(st.st_mode)) {
            // 递归扫描子目录
            loadDocsRecursive(fullPath, level + 1);
        } else if ((entry.size() >= 5 && entry.substr(entry.size() - 5) == ".html")) {
            // 解析 HTML 文档
            DocEntry doc = parseDocFile(fullPath);
            if (!doc.id.empty()) {
                s_docIndex[doc.id] = doc;
                s_docTree.push_back(doc);
            }
        }
    }
}

DocEntry DocIndexer::parseDocFile(const std::string& path) {
    DocEntry doc;
    doc.path = path;
    doc.level = 0;

    // 从路径提取 ID
    std::regex idRegex(R"(/([^/]+)\.html$)");
    std::smatch match;
    if (std::regex_search(path, match, idRegex)) {
        doc.id = match[1];
    }

    // 读取文件内容
    std::string content = FileUtils::readFile(path);
    if (content.empty()) {
        return doc;
    }
    doc.content = content;

    // 提取标题（从 <title> 或 <h1> 标签）
    std::regex titleRegex(R"(<title[^>]*>([^<]+)</title>)");
    if (std::regex_search(content, match, titleRegex)) {
        doc.title = match[1];
    } else {
        std::regex h1Regex(R"(<h1[^>]*>([^<]+)</h1>)");
        if (std::regex_search(content, match, h1Regex)) {
            doc.title = match[1];
        } else {
            doc.title = doc.id;
        }
    }

    // 提取标签（从 meta 标签或 class）
    std::regex tagRegex(R"(<meta[^>]*name=["']tags["'][^>]*content=["']([^"']+)["'])");
    if (std::regex_search(content, match, tagRegex)) {
        std::istringstream stream(match[1]);
        std::string tag;
        while (std::getline(stream, tag, ',')) {
            doc.tags.push_back(tag);
        }
    }

    return doc;
}

std::vector<DocEntry> DocIndexer::search(const std::string& keyword) {
    std::vector<DocEntry> results;

    if (keyword.empty() || !s_initialized) {
        return results;
    }

    std::string lowerKeyword = keyword;
    std::transform(lowerKeyword.begin(), lowerKeyword.end(),
                   lowerKeyword.begin(), ::tolower);

    for (const auto& pair : s_docIndex) {
        const auto& doc = pair.second;

        // 搜索标题
        std::string lowerTitle = doc.title;
        std::transform(lowerTitle.begin(), lowerTitle.end(),
                       lowerTitle.begin(), ::tolower);
        if (lowerTitle.find(lowerKeyword) != std::string::npos) {
            results.push_back(doc);
            continue;
        }

        // 搜索标签
        for (const auto& tag : doc.tags) {
            std::string lowerTag = tag;
            std::transform(lowerTag.begin(), lowerTag.end(),
                           lowerTag.begin(), ::tolower);
            if (lowerTag.find(lowerKeyword) != std::string::npos) {
                results.push_back(doc);
                break;
            }
        }

        // 搜索内容（前 500 字符）
        if (results.size() < 50) {  // 限制结果数量
            std::string contentPreview = doc.content.substr(0, 500);
            std::string lowerContent = contentPreview;
            std::transform(lowerContent.begin(), lowerContent.end(),
                           lowerContent.begin(), ::tolower);
            if (lowerContent.find(lowerKeyword) != std::string::npos) {
                results.push_back(doc);
            }
        }
    }

    return results;
}

std::string DocIndexer::getDocument(const std::string& docId) {
    if (s_docIndex.count(docId)) {
        return s_docIndex[docId].content;
    }
    return "";
}

std::vector<std::string> DocIndexer::getAllDocIds() {
    std::vector<std::string> ids;
    for (const auto& pair : s_docIndex) {
        ids.push_back(pair.first);
    }
    return ids;
}

std::vector<DocEntry> DocIndexer::getDocTree() {
    return s_docTree;
}

} // namespace godot
