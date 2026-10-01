/**
 * @file project_scanner.h
 * @brief Godot 项目扫描器：通过 SAF 路径扫描并识别 Godot 项目
 * @author Agnes Assistant
 */

#pragma once

#include <string>
#include <vector>
#include <unordered_map>

namespace godot {

/**
 * @brief Godot 项目信息
 */
struct GodotProject {
    std::string id;               // 项目唯一标识（通常为路径）
    std::string name;             // 项目名称
    std::string path;             // 项目根目录路径
    std::string version;          // Godot 版本（从 project.godot 解析）
    std::vector<std::string> scripts;  // 项目中的 .gd 脚本列表
};

/**
 * @brief Godot 项目扫描器
 */
class ProjectScanner {
public:
    /**
     * @brief 扫描指定目录下的所有 Godot 项目
     * @param baseDir 基础目录（如 /storage/emulated/0/Documents/）
     * @return 找到的项目列表
     */
    static std::vector<GodotProject> scanProjects(const std::string& baseDir);

    /**
     * @brief 检查指定路径是否为有效的 Godot 项目
     */
    static bool isValidProject(const std::string& path);

    /**
     * @brief 解析 project.godot 文件内容
     */
    static std::unordered_map<std::string, std::string> parseProjectFile(
        const std::string& content);
};

} // namespace godot
