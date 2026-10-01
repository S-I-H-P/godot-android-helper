package com.godot.devassistant.model

import android.net.Uri

/**
 * Godot 项目数据模型
 */
data class GodotProject(
    val name: String,
    val uri: Uri,
    val projectFileUri: Uri?,
    val scripts: List<ScriptFile> = emptyList()
)

/**
 * GDScript 文件
 */
data class ScriptFile(
    val name: String,
    val uri: Uri,
    val relativePath: String,
    val content: String = ""
)

/**
 * 补全项
 */
data class CompletionItem(
    val text: String,
    val label: String,
    val kind: String,
    val documentation: String = ""
)

/**
 * 高亮范围
 */
data class HighlightRange(
    val start: Int,
    val end: Int,
    val type: Int
)

/**
 * 文档条目
 */
data class DocEntry(
    val id: String,
    val title: String,
    val path: String,
    val level: Int = 0
)

/**
 * 设置项
 */
data class AppSettings(
    val projectRootUri: String? = null,
    val autoCompleteEnabled: Boolean = true,
    val themeMode: Int = 0,  // 0=自动, 1=浅色, 2=深色
    val fontSize: Int = 14,
    val tabWidth: Int = 4,
    val lineNumbersVisible: Boolean = true
)
