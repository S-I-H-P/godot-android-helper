/**
 * @file project_scanner.cpp
 * @brief Godot 项目扫描器实现
 * @author Agnes Assistant
 */

#include "godot/project_scanner.h"
#include "godot/file_utils.h"
#include <algorithm>
#include <regex>

namespace godot {

std::vector<GodotProject> ProjectScanner::scanProjects(const std::string& baseDir) {
    std::vector<GodotProject> projects;

    // 列出基础目录下的所有一级子目录
    auto entries = FileUtils::listDirectory(baseDir);
    for (const auto& entry : entries) {
        std::string subdir = baseDir + "/" + entry;

        // 检查是否为目录
        struct stat st;
        if (stat(subdir.c_str(), &st) != 0 || !S_ISDIR(st.st_mode)) {
            continue;
        }

        // 检查是否存在 project.godot
        std::string projectFile = subdir + "/project.godot";
        if (FileUtils::fileExists(projectFile)) {
            GodotProject project;
            project.id = subdir;
            project.path = subdir;
            project.name = entry;  // 默认使用目录名

            // 读取并解析 project.godot
            std::string content = FileUtils::readFile(projectFile);
            if (!content.empty()) {
                project.name = FileUtils::parseProjectName(content);
                auto parsed = parseProjectFile(content);
                if (parsed.count("config/version")) {
                    project.version = parsed["config/version"];
                }
            }

            // 扫描 .gd 文件
            std::function<void(const std::string&)> scanScripts = [&](const std::string& dir) {
                auto files = FileUtils::listDirectory(dir);
                for (const auto& file : files) {
                    std::string fullPath = dir + "/" + file;
                    struct stat fileSt;
                    if (stat(fullPath.c_str(), &fileSt) == 0) {
                        if (S_ISDIR(fileSt.st_mode)) {
                            // 递归扫描子目录（跳过 .godot 等隐藏目录）
                            if (file[0] != '.') {
                                scanScripts(fullPath);
                            }
                        } else if (FileUtils::isGdScriptFile(file)) {
                            project.scripts.push_back(fullPath);
                        }
                    }
                }
            };
            scanScripts(subdir);

            projects.push_back(project);
        }
    }

    // 按名称排序
    std::sort(projects.begin(), projects.end(),
        [](const GodotProject& a, const GodotProject& b) {
            return a.name < b.name;
        });

    return projects;
}

bool ProjectScanner::isValidProject(const std::string& path) {
    return FileUtils::fileExists(path + "/project.godot");
}

std::unordered_map<std::string, std::string> ProjectScanner::parseProjectFile(
    const std::string& content) {
    std::unordered_map<std::string, std::string> result;

    // 简单的键值对解析（适用于 project.godot 的简化格式）
    std::istringstream stream(content);
    std::string line;
    std::regex keyValRegex(R"(^\s*(\S+)\s*=\s*"([^"]*)")");

    while (std::getline(stream, line)) {
        std::smatch match;
        if (std::regex_match(line, match, keyValRegex)) {
            result[match[1]] = match[2];
        }
    }

    return result;
}

} // namespace godot
