/**
 * @file jni_interface.cpp
 * @brief JNI 接口实现：连接 Java/Kotlin 与 C++ 核心逻辑
 * @author Agnes Assistant
 */

#include <jni.h>
#include <string>
#include <sstream>
#include <android/log.h>
#include "godot/file_utils.h"
#include "godot/project_scanner.h"
#include "godot/tokenizer.h"
#include "godot/completion_engine.h"
#include "godot/syntax_highlighter.h"
#include "godot/doc_indexer.h"
#include "godot/settings_manager.h"

#define LOG_TAG "GodotDevJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// 辅助函数：将 std::string 转换为 jstring
static jstring stdStringToJstring(JNIEnv* env, const std::string& str) {
    if (str.empty()) return nullptr;
    return env->NewStringUTF(str.c_str());
}

// 辅助函数：将 jstring 转换为 std::string
static std::string jstringToStdString(JNIEnv* env, jstring jstr) {
    if (jstr == nullptr) return "";
    const char* chars = env->GetStringUTFChars(jstr, nullptr);
    std::string result(chars);
    env->ReleaseStringUTFChars(jstr, chars);
    return result;
}

// 辅助函数：生成 JSON 数组开始
static std::string jsonStart() { return "["; }
static std::string jsonEnd() { return "]"; }
static std::string jsonNull() { return "null"; }

extern "C" {

JNIEXPORT void JNICALL
Java_com_godot_devassistant_native_NativeLib_init(
    JNIEnv* env, jobject thiz, jobject context) {
    godot::SettingsManager::getInstance().initialize(context);
    godot::CompletionEngine::initialize();
    LOGD("Native 库初始化完成");
}

JNIEXPORT jstring JNICALL
Java_com_godot_devassistant_native_NativeLib_scanProjects(
    JNIEnv* env, jobject thiz, jstring baseDir) {
    std::string dir = jstringToStdString(env, baseDir);
    auto projects = godot::ProjectScanner::scanProjects(dir);

    std::ostringstream json;
    json << "[";
    for (size_t i = 0; i < projects.size(); i++) {
        if (i > 0) json << ",";
        json << "{";
        json << "\"id\":\"" << projects[i].id << "\",";
        json << "\"name\":\"" << projects[i].name << "\",";
        json << "\"path\":\"" << projects[i].path << "\",";
        json << "\"version\":\"" << projects[i].version << "\",";
        json << "\"scriptCount\":" << projects[i].scripts.size();
        json << "}";
    }
    json << "]";

    return stdStringToJstring(env, json.str());
}

JNIEXPORT jstring JNICALL
Java_com_godot_devassistant_native_NativeLib_readFile(
    JNIEnv* env, jobject thiz, jstring filePath) {
    std::string path = jstringToStdString(env, filePath);
    std::string content = godot::FileUtils::readFile(path);
    return stdStringToJstring(env, content);
}

JNIEXPORT jboolean JNICALL
Java_com_godot_devassistant_native_NativeLib_writeFile(
    JNIEnv* env, jobject thiz, jstring filePath, jstring content) {
    std::string path = jstringToStdString(env, filePath);
    std::string text = jstringToStdString(env, content);
    return godot::FileUtils::writeFile(path, text) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_godot_devassistant_native_NativeLib_highlightCode(
    JNIEnv* env, jobject thiz, jstring code) {
    std::string src = jstringToStdString(env, code);
    auto ranges = godot::SyntaxHighlighter::highlight(src);

    std::ostringstream json;
    json << "[";
    for (size_t i = 0; i < ranges.size(); i++) {
        if (i > 0) json << ",";
        json << "{\"start\":" << ranges[i].startOffset
             << ",\"end\":" << ranges[i].endOffset
             << ",\"type\":" << (int)ranges[i].type << "}";
    }
    json << "]";

    return stdStringToJstring(env, json.str());
}

JNIEXPORT jstring JNICALL
Java_com_godot_devassistant_native_NativeLib_getCompletions(
    JNIEnv* env, jobject thiz, jstring code) {
    std::string src = jstringToStdString(env, code);

    // 获取最后一行的输入
    size_t lastNewline = src.rfind('\n');
    std::string lastLine = (lastNewline == std::string::npos)
        ? src : src.substr(lastNewline + 1);

    // 提取当前词（部分输入）
    std::string partialInput;
    for (int i = (int)lastLine.size() - 1; i >= 0; i--) {
        char c = lastLine[i];
        if (std::isalnum(c) || c == '_') {
            partialInput = c + partialInput;
        } else {
            break;
        }
    }

    // 获取 token 流
    auto tokens = godot::Tokenizer::tokenize(src);
    auto completions = godot::CompletionEngine::getCompletions(tokens, partialInput);

    std::ostringstream json;
    json << "[";
    for (size_t i = 0; i < completions.size(); i++) {
        if (i > 0) json << ",";
        json << "{";
        json << "\"text\":\"" << completions[i].text << "\",";
        json << "\"label\":\"" << completions[i].label << "\",";
        json << "\"kind\":\"" << completions[i].kind << "\"";
        if (!completions[i].documentation.empty()) {
            json << ",\"doc\":\"" << completions[i].documentation << "\"";
        }
        json << "}";
    }
    json << "]";

    return stdStringToJstring(env, json.str());
}

JNIEXPORT jstring JNICALL
Java_com_godot_devassistant_native_NativeLib_searchDocs(
    JNIEnv* env, jobject thiz, jstring keyword) {
    std::string kw = jstringToStdString(env, keyword);
    auto results = godot::DocIndexer::search(kw);

    std::ostringstream json;
    json << "[";
    for (size_t i = 0; i < results.size(); i++) {
        if (i > 0) json << ",";
        json << "{";
        json << "\"id\":\"" << results[i].id << "\",";
        json << "\"title\":\"" << results[i].title << "\",";
        json << "\"level\":" << results[i].level;
        json << "}";
    }
    json << "]";

    return stdStringToJstring(env, json.str());
}

JNIEXPORT jstring JNICALL
Java_com_godot_devassistant_native_NativeLib_getDocContent(
    JNIEnv* env, jobject thiz, jstring docId) {
    std::string id = jstringToStdString(env, docId);
    std::string content = godot::DocIndexer::getDocument(id);
    return stdStringToJstring(env, content);
}

JNIEXPORT jstring JNICALL
Java_com_godot_devassistant_native_NativeLib_getAllDocIds(
    JNIEnv* env, jobject thiz) {
    auto ids = godot::DocIndexer::getAllDocIds();

    std::ostringstream json;
    json << "[";
    for (size_t i = 0; i < ids.size(); i++) {
        if (i > 0) json << ",";
        json << "\"" << ids[i] << "\"";
    }
    json << "]";

    return stdStringToJstring(env, json.str());
}

JNIEXPORT void JNICALL
Java_com_godot_devassistant_native_NativeLib_saveSetting(
    JNIEnv* env, jobject thiz, jstring key, jstring value) {
    std::string k = jstringToStdString(env, key);
    std::string v = jstringToStdString(env, value);
    // 这里可以通过 JNI 调用 Java 层的 SharedPreferences
    LOGD("保存设置: %s = %s", k.c_str(), v.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_godot_devassistant_native_NativeLib_getSetting(
    JNIEnv* env, jobject thiz, jstring key, jstring defaultValue) {
    std::string k = jstringToStdString(env, key);
    auto& settings = godot::SettingsManager::getInstance();

    if (k == "default_project_dir") {
        return stdStringToJstring(env, settings.getDefaultProjectDir());
    } else if (k == "last_project_path") {
        return stdStringToJstring(env, settings.getLastProjectPath());
    } else if (k == "auto_complete_enabled") {
        return stdStringToJstring(env, settings.isAutoCompleteEnabled() ? "true" : "false");
    } else if (k == "theme_mode") {
        return stdStringToJstring(env, std::to_string(settings.getThemeMode()));
    } else if (k == "font_size") {
        return stdStringToJstring(env, std::to_string(settings.getFontSize()));
    }

    return stdStringToJstring(env, jstringToStdString(env, defaultValue));
}

} // extern "C"
