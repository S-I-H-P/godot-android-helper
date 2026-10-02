package com.godot.devassistant.native

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONArray
import org.json.JSONObject

/**
 * 原生库桥接类（纯 Kotlin 实现，不依赖 C++）
 */
object NativeLib {

    private val gson = Gson()

    fun init(context: Context) {
        // 纯 Kotlin 版本无需初始化
    }

    fun scanProjects(baseDir: String): String {
        return "[]"
    }

    fun readFile(filePath: String): String? {
        return null
    }

    fun writeFile(filePath: String, content: String): Boolean {
        return false
    }

    fun highlightCode(code: String): String {
        return "[]"
    }

    fun getCompletions(code: String): String {
        return "[]"
    }

    fun searchDocs(keyword: String): String {
        return "[]"
    }

    fun getDocContent(docId: String): String? {
        return null
    }

    fun getAllDocIds(): String {
        return "[\"gdscript_reference\"]"
    }

    fun saveSetting(key: String, value: String) {}

    fun getSetting(key: String, defaultValue: String): String {
        return defaultValue
    }
}
