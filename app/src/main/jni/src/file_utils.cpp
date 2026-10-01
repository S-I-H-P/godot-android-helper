/**
 * @file file_utils.cpp
 * @brief 文件工具类实现
 * @author Agnes Assistant
 */

#include "godot/file_utils.h"
#include <fstream>
#include <sstream>
#include <algorithm>
#include <dirent.h>
#include <sys/stat.h>

namespace godot {

bool FileUtils::fileExists(const std::string& path) {
    struct stat buffer;
    return (stat(path.c_str(), &buffer) == 0);
}

std::string FileUtils::readFile(const std::string& path) {
    std::ifstream file(path);
    if (!file.is_open()) {
        LOGE("无法打开文件: %s", path.c_str());
        return "";
    }
    std::stringstream buffer;
    buffer << file.rdbuf();
    return buffer.str();
}

bool FileUtils::writeFile(const std::string& path, const std::string& content) {
    std::ofstream file(path, std::ios::trunc);
    if (!file.is_open()) {
        LOGE("无法写入文件: %s", path.c_str());
        return false;
    }
    file << content;
    file.close();
    return true;
}

bool FileUtils::appendFile(const std::string& path, const std::string& content) {
    std::ofstream file(path, std::ios::app);
    if (!file.is_open()) {
        LOGE("无法追加文件: %s", path.c_str());
        return false;
    }
    file << content;
    file.close();
    return true;
}

std::vector<std::string> FileUtils::listDirectory(const std::string& path) {
    std::vector<std::string> result;
    DIR* dir = opendir(path.c_str());
    if (dir == nullptr) {
        LOGE("无法打开目录: %s", path.c_str());
        return result;
    }

    struct dirent* entry;
    while ((entry = readdir(dir)) != nullptr) {
        std::string name = entry->d_name;
        // 跳过 . 和 ..
        if (name == "." || name == "..") {
            continue;
        }
        result.push_back(name);
    }
    closedir(dir);
    std::sort(result.begin(), result.end());
    return result;
}

std::string FileUtils::getParentDir(const std::string& path) {
    size_t pos = path.find_last_of('/');
    if (pos == std::string::npos) {
        return ".";
    }
    return path.substr(0, pos);
}

std::string FileUtils::getFileName(const std::string& path) {
    size_t pos = path.find_last_of('/');
    if (pos == std::string::npos) {
        return path;
    }
    return path.substr(pos + 1);
}

std::string FileUtils::getFileExtension(const std::string& path) {
    size_t pos = path.find_last_of('.');
    if (pos == std::string::npos) {
        return "";
    }
    return path.substr(pos + 1);
}

bool FileUtils::isGdScriptFile(const std::string& path) {
    return getFileExtension(path) == "gd";
}

bool FileUtils::isProjectFile(const std::string& path) {
    return getFileName(path) == "project.godot";
}

std::string FileUtils::parseProjectName(const std::string& content) {
    // 简单的解析：查找 name="..." 行
    size_t namePos = content.find("name=");
    if (namePos == std::string::npos) {
        return "Unnamed Project";
    }
    size_t quoteStart = content.find('"', namePos + 5);
    size_t quoteEnd = content.find('"', quoteStart + 1);
    if (quoteStart != std::string::npos && quoteEnd != std::string::npos) {
        return content.substr(quoteStart + 1, quoteEnd - quoteStart - 1);
    }
    return "Unnamed Project";
}

} // namespace godot
