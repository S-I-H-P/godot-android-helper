package com.godot.devassistant

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.godot.devassistant.native.NativeLib

class GodotDevApp : Application() {

    companion object {
        lateinit var instance: GodotDevApp
            private set
        lateinit var prefs: SharedPreferences
            private set
        const val PREF_NAME = "godot_dev_assistant_prefs"
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        // 初始化原生库（加异常处理）
        try {
            NativeLib.init(this)
            Log.d("GodotDevApp", "原生库加载成功")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("GodotDevApp", "原生库加载失败: ${e.message}")
            // 不影响应用启动
        } catch (e: Exception) {
            Log.e("GodotDevApp", "初始化异常: ${e.message}")
        }
    }
}
