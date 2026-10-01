/**
 * @file jni_interface.h
 * @brief JNI 接口层：将 C++ 功能暴露给 Java/Kotlin 调用
 * @author Agnes Assistant
 */

#pragma once

#include <jni.h>
#include <string>

// 导出函数声明（供 JNI 调用）
extern "C" {

/**
 * @brief 初始化 JNI（由 Java 层调用）
 */
JNIEXPORT void JNICALL
Java_com_godot_devassistant_native_NativeLib_init(JNIEnv* env, jobject thiz, jobject context);

/**
 * @brief 扫描 Godot 项目
 * @param baseDir 基础目录路径
 * @return JSON 格式的项目列表字符串
 */
JNIEXPORT jstring JNICALL
Java_com_godot_devassistant_native_NativeLib_scanProjects(
    JNIEnv* env, jobject thiz, jstring baseDir);

/**
 * @brief 读取 GDScript 文件内容
 * @param filePath 文件路径
 * @return 文件内容，失败返回 null
 */
JNIEXPORT jstring JNICALL
Java_com_godot_devassistant_native_NativeLib_readFile(
    JNIEnv* env, jobject thiz, jstring filePath);

/**
 * @brief 写入 GDScript 文件内容
 * @param filePath 文件路径
 * @param content 内容
 * @return 成功返回 true
 */
JNIEXPORT jboolean JNICALL
Java_com_godot_devassistant_native_NativeLib_writeFile(
    JNIEnv* env, jobject thiz, jstring filePath, jstring content);

/**
 * @brief 对 GDScript 代码进行语法高亮分析
 * @param code 源代码
 * @return JSON 格式的高亮范围列表
 */
JNIEXPORT jstring JNICALL
Java_com_godot_devassistant_native_NativeLib_highlightCode(
    JNIEnv* env, jobject thiz, jstring code);

/**
 * @brief 获取代码补全建议
 * @param code 当前代码（到光标处）
 * @return JSON 格式的补全项列表
 */
JNIEXPORT jstring JNICALL
Java_com_godot_devassistant_native_NativeLib_getCompletions(
    JNIEnv* env, jobject thiz, jstring code);

/**
 * @brief 搜索离线文档
 * @param keyword 关键词
 * @return JSON 格式的搜索结果列表
 */
JNIEXPORT jstring JNICALL
Java_com_godot_devassistant_native_NativeLib_searchDocs(
    JNIEnv* env, jobject thiz, jstring keyword);

/**
 * @brief 获取文档内容
 * @param docId 文档 ID
 * @return HTML 内容，失败返回 null
 */
JNIEXPORT jstring JNICALL
Java_com_godot_devassistant_native_NativeLib_getDocContent(
    JNIEnv* env, jobject thiz, jstring docId);

/**
 * @brief 获取所有文档 ID
 * @return JSON 格式的文档 ID 列表
 */
JNIEXPORT jstring JNICALL
Java_com_godot_devassistant_native_NativeLib_getAllDocIds(
    JNIEnv* env, jobject thiz);

/**
 * @brief 保存设置
 */
JNIEXPORT void JNICALL
Java_com_godot_devassistant_native_NativeLib_saveSetting(
    JNIEnv* env, jobject thiz, jstring key, jstring value);

/**
 * @brief 读取设置
 */
JNIEXPORT jstring JNICALL
Java_com_godot_devassistant_native_NativeLib_getSetting(
    JNIEnv* env, jobject thiz, jstring key, jstring defaultValue);

} // extern "C"
