package com.godot.devassistant.native

import android.content.Context
import android.util.Log

/**
 * 原生库桥接类
 *
 * external 函数名与 C++ 导出符号严格对应：
 * Java_com_godot_devassistant_native_NativeLib_nativeXxx
 */
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

    /** 原生库是否可用 */
    val isAvailable: Boolean get() = loaded

    @JvmStatic
    fun init(context: Context) {
        if (!loaded) return
        try { nativeInit(context) } catch (e: Throwable) { Log.e("NativeLib", "init 失败", e) }
    }

    @JvmStatic
    fun scanProjects(baseDir: String): String {
        if (!loaded) return "[]"
        return try { nativeScanProjects(baseDir) } catch (e: Throwable) { "[]" }
    }

    @JvmStatic
    fun readFile(filePath: String): String? {
        if (!loaded) return null
        return try { nativeReadFile(filePath) } catch (e: Throwable) { null }
    }

    @JvmStatic
    fun writeFile(filePath: String, content: String): Boolean {
        if (!loaded) return false
        return try { nativeWriteFile(filePath, content) } catch (e: Throwable) { false }
    }

    @JvmStatic
    fun highlightCode(code: String): String {
        if (!loaded) return "[]"
        return try { nativeHighlight(code) } catch (e: Throwable) { "[]" }
    }

    @JvmStatic
    fun getCompletions(code: String): String {
        if (!loaded) return "[]"
        return try { nativeGetCompletions(code) } catch (e: Throwable) { "[]" }
    }

    @JvmStatic
    fun searchDocs(keyword: String): String {
        if (!loaded) return "[]"
        return try { nativeSearchDocs(keyword) } catch (e: Throwable) { "[]" }
    }

    @JvmStatic
    fun getDocContent(docId: String): String? {
        if (!loaded) return null
        return try { nativeGetDocContent(docId) } catch (e: Throwable) { null }
    }

    @JvmStatic
    fun getAllDocIds(): String {
        if (!loaded) return "[\"gdscript_reference\"]"
        return try { nativeGetAllDocIds() } catch (e: Throwable) { "[\"gdscript_reference\"]" }
    }

    @JvmStatic
    fun saveSetting(key: String, value: String) {
        if (!loaded) return
        try { nativeSaveSetting(key, value) } catch (e: Throwable) { }
    }

    @JvmStatic
    fun getSetting(key: String, defaultValue: String): String {
        if (!loaded) return defaultValue
        return try { nativeGetSetting(key, defaultValue) } catch (e: Throwable) { defaultValue }
    }

    // ==================== JNI 声明 ====================
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
