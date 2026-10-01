package com.godot.devassistant

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.godot.devassistant.native.NativeLib

/**
 * 应用入口类
 * 负责初始化原生库与全局状态
 */
class GodotDevApp : Application() {

    companion object {
        lateinit var instance: GodotDevApp
            private set

        /** 应用设置 */
        lateinit var prefs: SharedPreferences
            private set

        const val PREF_NAME = "godot_dev_assistant_prefs"
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        // 初始化原生库
        NativeLib.init(this)
    }
}
