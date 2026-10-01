/**
 * @file settings_manager.h
 * @brief 应用设置管理器：管理用户偏好设置（如默认项目目录）
 * @author Agnes Assistant
 */

#pragma once

#include <string>

namespace godot {

/**
 * @brief 应用设置管理器（单例）
 */
class SettingsManager {
public:
    /**
     * @brief 获取单例实例
     */
    static SettingsManager& getInstance();

    /**
     * @brief 初始化（传入 Android ContentResolver 的 Context）
     */
    void initialize(void* context);

    /**
     * @brief 获取默认项目根目录
     */
    std::string getDefaultProjectDir() const;

    /**
     * @brief 设置默认项目根目录
     */
    void setDefaultProjectDir(const std::string& path);

    /**
     * @brief 获取上次打开的项目路径
     */
    std::string getLastProjectPath() const;

    /**
     * @brief 设置上次打开的项目路径
     */
    void setLastProjectPath(const std::string& path);

    /**
     * @brief 获取代码补全启用状态
     */
    bool isAutoCompleteEnabled() const;

    /**
     * @brief 设置代码补全启用状态
     */
    void setAutoCompleteEnabled(bool enabled);

    /**
     * @brief 获取主题模式（0=自动, 1=浅色, 2=深色）
     */
    int getThemeMode() const;

    /**
     * @brief 设置主题模式
     */
    void setThemeMode(int mode);

    /**
     * @brief 获取字体大小
     */
    int getFontSize() const;

    /**
     * @brief 设置字体大小
     */
    void setFontSize(int size);

private:
    SettingsManager() = default;
    void* mContext = nullptr;

    // 缓存的设置值
    std::string m_defaultProjectDir;
    std::string m_lastProjectPath;
    bool m_autoCompleteEnabled;
    int m_themeMode;
    int m_fontSize;
};

} // namespace godot
