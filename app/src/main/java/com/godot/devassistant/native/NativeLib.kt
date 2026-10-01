package com.godot.devassistant.native

import android.content.Context

/**
 * 原生库桥接类
 * 通过 JNI 调用 C++ 核心逻辑
 */
object NativeLib {

    // 加载原生库
    init {
        System.loadLibrary("godot-dev-assistant")
    }

    /**
     * 初始化原生库
     */
    @JvmStatic
    external fun init(context: Context)

    /**
     * 扫描指定目录下的 Godot 项目
     * @param baseDir 基础目录路径
     * @return JSON 格式的项目列表
     */
    @JvmStatic
    external fun scanProjects(baseDir: String): String

    /**
     * 读取文件内容
     */
    @JvmStatic
    external fun readFile(filePath: String): String?

    /**
     * 写入文件内容
     */
    @JvmStatic
    external fun writeFile(filePath: String, content: String): Boolean

    /**
     * 对 GDScript 代码进行语法高亮分析
     * @return JSON 格式的高亮范围列表
     */
    @JvmStatic
    external fun highlightCode(code: String): String

    /**
     * 获取代码补全建议
     * @param code 当前代码（到光标处）
     * @return JSON 格式的补全项列表
     */
    @JvmStatic
    external fun getCompletions(code: String): String

    /**
     * 搜索离线文档
     */
    @JvmStatic
    external fun searchDocs(keyword: String): String

    /**
     * 获取文档内容
     */
    @JvmStatic
    external fun getDocContent(docId: String): String?

    /**
     * 获取所有文档 ID
     */
    @JvmStatic
    external fun getAllDocIds(): String

    /**
     * 保存设置
     */
    @JvmStatic
    external fun saveSetting(key: String, value: String)

    /**
     * 读取设置
     */
    @JvmStatic
    external fun getSetting(key: String, defaultValue: String): String
}
