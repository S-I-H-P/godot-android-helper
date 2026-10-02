package com.godot.devassistant.`native`

import android.content.Context

/**
 * 原生库桥接类
 * 通过 JNI 调用 C++ 核心逻辑
 */
object NativeLib {

    init {
        System.loadLibrary("godot-dev-assistant")
    }

    @JvmStatic
    external fun init(context: Context)

    @JvmStatic
    external fun scanProjects(baseDir: String): String

    @JvmStatic
    external fun readFile(filePath: String): String?

    @JvmStatic
    external fun writeFile(filePath: String, content: String): Boolean

    @JvmStatic
    external fun highlightCode(code: String): String

    @JvmStatic
    external fun getCompletions(code: String): String

    @JvmStatic
    external fun searchDocs(keyword: String): String

    @JvmStatic
    external fun getDocContent(docId: String): String?

    @JvmStatic
    external fun getAllDocIds(): String

    @JvmStatic
    external fun saveSetting(key: String, value: String)

    @JvmStatic
    external fun getSetting(key: String, defaultValue: String): String
}
