package com.godot.devassistant.native

import android.content.Context
import android.util.Log

object NativeLib {

    private var loaded = false

    init {
        try {
            System.loadLibrary("godot-dev-assistant")
            loaded = true
            Log.d("NativeLib", "原生库加载成功")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("NativeLib", "无法加载原生库: ${e.message}")
        }
    }

    private fun ensureLoaded() {
        if (!loaded) {
            throw RuntimeException("原生库不可用")
        }
    }

    @JvmStatic
    fun init(context: Context) {
        if (!loaded) return
        nativeInit(context)
    }

    @JvmStatic
    fun scanProjects(baseDir: String): String {
        if (!loaded) return "[]"
        return nativeScanProjects(baseDir)
    }

    @JvmStatic
    fun readFile(filePath: String): String? {
        if (!loaded) return null
        return nativeReadFile(filePath)
    }

    @JvmStatic
    fun writeFile(filePath: String, content: String): Boolean {
        if (!loaded) return false
        return nativeWriteFile(filePath, content)
    }

    @JvmStatic
    fun highlightCode(code: String): String {
        if (!loaded) return "[]"
        return nativeHighlight(code)
    }

    @JvmStatic
    fun getCompletions(code: String): String {
        if (!loaded) return "[]"
        return nativeGetCompletions(code)
    }

    @JvmStatic
    fun searchDocs(keyword: String): String {
        if (!loaded) return "[]"
        return nativeSearchDocs(keyword)
    }

    @JvmStatic
    fun getDocContent(docId: String): String? {
        if (!loaded) return null
        return nativeGetDocContent(docId)
    }

    @JvmStatic
    fun getAllDocIds(): String {
        if (!loaded) return "[\"gdscript_reference\"]"
        return nativeGetAllDocIds()
    }

    @JvmStatic
    fun saveSetting(key: String, value: String) {
        if (!loaded) return
        nativeSaveSetting(key, value)
    }

    @JvmStatic
    fun getSetting(key: String, defaultValue: String): String {
        if (!loaded) return defaultValue
        return nativeGetSetting(key, defaultValue)
    }

    // JNI 声明
    private external fun nativeInit(context: Context)
    private external fun nativeScanProjects(baseDir: String): String
    private external fun nativeReadFile(filePath: String): String?
    private external fun nativeWriteFile(filePath: String, content: String): Boolean
    private external fun nativeHighlight(code: String): String
    private external fun nativeGetCompletions(code: String): String
    private external fun nativeSearchDocs(keyword: String): String
    private external fun nativeGetDocContent(docId: String): String?
    private external fun nativeGetAllDocIds(): String
    private external fun nativeSaveSetting(key: String, value: String)
    private external fun nativeGetSetting(key: String, defaultValue: String): String
}
