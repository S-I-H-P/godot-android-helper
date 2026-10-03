/**
 * @file jni_interface.cpp
 * @brief JNI 接口实现：连接 Kotlin 与 C++ 核心逻辑
 *
 * 重要：函数名必须与 Kotlin NativeLib 中的 external 声明严格对应。
 * Kotlin 声明为 external fun nativeXxx(...)，因此这里导出
 * Java_com_godot_devassistant_native_NativeLib_nativeXxx
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

// 将 std::string 转换为 jstring
static jstring stdStringToJstring(JNIEnv* env, const std::string& str) {
    if (str.empty()) return env->NewStringUTF("");
    return env->NewStringUTF(str.c_str());
}

// 将 jstring 转换为 std::string
static std::string jstringToStdString(JNIEnv* env, jstring jstr) {
    if (jstr == nullptr) return "";
    const char* chars = env->GetStringUTFChars(jstr, nullptr);
    std::string result(chars ? chars : "");
    if (chars) env->ReleaseStringUTFChars(jstr, chars);
    return result;
}

extern "C" {

// ==================== nativeInit ====================
JNIEXPORT void JNICALL
Java_com_godot_devassistant_native_NativeLib_nativeInit(
    JNIEnv* env, jobject thiz, jobject context) {
    godot::SettingsManager::getInstance().initialize(context);
    godot::CompletionEngine::initialize();
    LOGD("Native 库初始化完成");
}

// ==================== nativeScanProjects ====================
JNIEXPORT jstring JNICALL
Java_com_godot_devassistant_native_NativeLib_nativeScanProjects(
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

// ==================== nativeReadFile ====================
JNIEXPORT jstring JNICALL
Java_com_godot_devassistant_native_NativeLib_nativeReadFile(
    JNIEnv* env, jobject thiz, jstring filePath) {
    std::string path = jstringToStdString(env, filePath);
    std::string content = godot::FileUtils::readFile(path);
    return stdStringToJstring(env, content);
}

// ==================== nativeWriteFile ====================
JNIEXPORT jboolean JNICALL
Java_com_godot_devassistant_native_NativeLib_nativeWriteFile(
    JNIEnv* env, jobject thiz, jstring filePath, jstring content) {
    std::string path = jstringToStdString(env, filePath);
    std::string text = jstringToStdString(env, content);
    return godot::FileUtils::writeFile(path, text) ? JNI_TRUE : JNI_FALSE;
}

// ==================== nativeHighlight ====================
JNIEXPORT jstring JNICALL
Java_com_godot_devassistant_native_NativeLib_nativeHighlight(
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

// ==================== nativeGetCompletions ====================
JNIEXPORT jstring JNICALL
Java_com_godot_devassistant_native_NativeLib_nativeGetCompletions(
    JNIEnv* env, jobject thiz, jstring code) {
    std::string src = jstringToStdString(env, code);

    size_t lastNewline = src.rfind('\n');
    std::string lastLine = (lastNewline == std::string::npos)
        ? src : src.substr(lastNewline + 1);

    std::string partialInput;
    for (int i = (int)lastLine.size() - 1; i >= 0; i--) {
        char c = lastLine[i];
        if (std::isalnum((unsigned char)c) || c == '_') {
            partialInput = c + partialInput;
        } else {
            break;
        }
    }

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

// ==================== nativeSearchDocs ====================
JNIEXPORT jstring JNICALL
Java_com_godot_devassistant_native_NativeLib_nativeSearchDocs(
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

// ==================== nativeGetDocContent ====================
JNIEXPORT jstring JNICALL
Java_com_godot_devassistant_native_NativeLib_nativeGetDocContent(
    JNIEnv* env, jobject thiz, jstring docId) {
    std::string id = jstringToStdString(env, docId);
    std::string content = godot::DocIndexer::getDocument(id);
    return stdStringToJstring(env, content);
}

// ==================== nativeGetAllDocIds ====================
JNIEXPORT jstring JNICALL
Java_com_godot_devassistant_native_NativeLib_nativeGetAllDocIds(
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

// ==================== nativeSaveSetting ====================
JNIEXPORT void JNICALL
Java_com_godot_devassistant_native_NativeLib_nativeSaveSetting(
    JNIEnv* env, jobject thiz, jstring key, jstring value) {
    std::string k = jstringToStdString(env, key);
    std::string v = jstringToStdString(env, value);
    LOGD("保存设置: %s = %s", k.c_str(), v.c_str());
}

// ==================== nativeGetSetting ====================
JNIEXPORT jstring JNICALL
Java_com_godot_devassistant_native_NativeLib_nativeGetSetting(
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
