/**
 * @file settings_manager.cpp
 * @brief 应用设置管理器实现（单例模式）
 * @author Agnes Assistant
 */

#include "godot/settings_manager.h"
#include <android/log.h>
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "GodotDev", __VA_ARGS__)
#include <android/native_activity.h>
#include <android/log.h>
#include <sys/stat.h>
#include <unistd.h>

namespace godot {

SettingsManager& SettingsManager::getInstance() {
    static SettingsManager instance;
    return instance;
}

void SettingsManager::initialize(void* context) {
    mContext = context;
    // 默认设置
    m_defaultProjectDir = "/storage/emulated/0/Documents";
    m_lastProjectPath = "";
    m_autoCompleteEnabled = true;
    m_themeMode = 0;  // 自动
    m_fontSize = 14;
}

std::string SettingsManager::getDefaultProjectDir() const {
    return m_defaultProjectDir;
}

void SettingsManager::setDefaultProjectDir(const std::string& path) {
    m_defaultProjectDir = path;
    LOGI("默认项目目录已设置为: %s", path.c_str());
}

std::string SettingsManager::getLastProjectPath() const {
    return m_lastProjectPath;
}

void SettingsManager::setLastProjectPath(const std::string& path) {
    m_lastProjectPath = path;
}

bool SettingsManager::isAutoCompleteEnabled() const {
    return m_autoCompleteEnabled;
}

void SettingsManager::setAutoCompleteEnabled(bool enabled) {
    m_autoCompleteEnabled = enabled;
}

int SettingsManager::getThemeMode() const {
    return m_themeMode;
}

void SettingsManager::setThemeMode(int mode) {
    m_themeMode = mode;
}

int SettingsManager::getFontSize() const {
    return m_fontSize;
}

void SettingsManager::setFontSize(int size) {
    if (size >= 10 && size <= 32) {
        m_fontSize = size;
    }
}

} // namespace godot
