/**
 * @file project_scanner.cpp
 * @brief Godot 项目扫描器实现
 * @author Agnes Assistant
 */

#include "godot/project_scanner.h"
#include "godot/file_utils.h"
#include <algorithm>
#include <sys/stat.h>
#include <sstream>
#include <functional>

namespace godot {

std::vector<GodotProject> ProjectScanner::scanProjects(const std::string& baseDir) {
    std::vector<GodotProject> projects;

    auto entries = FileUtils::listDirectory(baseDir);
    for (const auto& entry : entries) {
        std::string subdir = baseDir + "/" + entry;

        struct stat st;
        if (stat(subdir.c_str(), &st) != 0 || !S_ISDIR(st.st_mode)) {
            continue;
        }

        std::string projectFile = subdir + "/project.godot";
        if (FileUtils::fileExists(projectFile)) {
            GodotProject project;
            project.id = subdir;
            project.path = subdir;
            project.name = entry;

            std::string content = FileUtils::readFile(projectFile);
            if (!content.empty()) {
                project.name = FileUtils::parseProjectName(content);
                auto parsed = parseProjectFile(content);
                if (parsed.count("config/version")) {
                    project.version = parsed["config/version"];
                }
            }

            std::function<void(const std::string&)> scanScripts = [&](const std::string& dir) {
                auto files = FileUtils::listDirectory(dir);
                for (const auto& file : files) {
                    std::string fullPath = dir + "/" + file;
                    struct stat fileSt;
                    if (stat(fullPath.c_str(), &fileSt) == 0) {
                        if (S_ISDIR(fileSt.st_mode)) {
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

    // 简单的键值对解析：查找 key = "value" 模式
    std::istringstream stream(content);
    std::string line;

    while (std::getline(stream, line)) {
        // 跳过注释行和空行
        if (line.empty() || line[0] == '#') continue;

        // 查找等号
        size_t eqPos = line.find('=');
        if (eqPos == std::string::npos) continue;

        // 提取键名（等号前，去掉空格）
        std::string key = line.substr(0, eqPos);
        while (!key.empty() && (key.back() == ' ' || key.back() == '\t')) key.pop_back();
        while (!key.empty() && (key.front() == ' ' || key.front() == '\t')) key.erase(0, 1);

        if (key.empty()) continue;

        // 提取值（等号后，查找引号对）
        size_t valStart = eqPos + 1;
        while (valStart < line.size() && line[valStart] == ' ') valStart++;
        if (valStart >= line.size()) continue;

        if (line[valStart] == '"') {
            size_t valEnd = valStart + 1;
            while (valEnd < line.size() && line[valEnd] != '"') valEnd++;
            if (valEnd < line.size()) {
                result[key] = line.substr(valStart + 1, valEnd - valStart - 1);
            }
        }
    }

    return result;
}

} // namespace godot
