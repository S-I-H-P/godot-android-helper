/**
 * @file file_utils.h
 * @brief 文件工具类：提供跨平台的文件读写、目录扫描功能
 * @author Agnes Assistant
 */

#pragma once

#include <string>
#include <vector>
#include <android/log.h>

// 日志宏定义
#define LOG_TAG "GodotDevAssistant"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace godot {

/**
 * @brief 文件工具类
 */
class FileUtils {
public:
    /**
     * @brief 检查文件是否存在
     */
    static bool fileExists(const std::string& path);

    /**
     * @brief 读取文件全部内容
     * @return 文件内容，失败返回空字符串
     */
    static std::string readFile(const std::string& path);

    /**
     * @brief 写入文件（覆盖模式）
     * @return 成功返回 true
     */
    static bool writeFile(const std::string& path, const std::string& content);

    /**
     * @brief 追加写入文件
     * @return 成功返回 true
     */
    static bool appendFile(const std::string& path, const std::string& content);

    /**
     * @brief 列出目录下的所有子项
     */
    static std::vector<std::string> listDirectory(const std::string& path);

    /**
     * @brief 获取父目录路径
     */
    static std::string getParentDir(const std::string& path);

    /**
     * @brief 获取文件名（不含路径）
     */
    static std::string getFileName(const std::string& path);

    /**
     * @brief 获取文件扩展名（不含点）
     */
    static std::string getFileExtension(const std::string& path);

    /**
     * @brief 判断是否为 GDScript 文件
     */
    static bool isGdScriptFile(const std::string& path);

    /**
     * @brief 判断是否为 project.godot 文件
     */
    static bool isProjectFile(const std::string& path);

    /**
     * @brief 解析 project.godot 获取项目名称
     */
    static std::string parseProjectName(const std::string& content);
};

} // namespace godot
