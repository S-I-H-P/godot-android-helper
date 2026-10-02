package com.godot.devassistant.ui
import com.godot.devassistant.R

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.godot.devassistant.GodotDevApp
import com.godot.devassistant.databinding.ActivitySettingsBinding

/**
 * 设置界面
 *
 * 功能：
 * - 修改默认项目目录
 * - 切换主题模式
 * - 调整字体大小
 * - 开关自动补全
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "设置"
            setDisplayHomeAsUpEnabled(true)
        }

        loadSettings()
        setupListeners()
    }

    /**
     * 加载当前设置
     */
    private fun loadSettings() {
        val prefs = GodotDevApp.prefs

        // 项目目录
        val rootUri = prefs.getString("root_uri", null)
        binding.textProjectDir.text = rootUri ?: "未设置（默认: /storage/emulated/0/Documents/）"

        // 自动补全
        binding.switchAutoComplete.isChecked = prefs.getBoolean("auto_complete", true)

        // 主题模式
        when (prefs.getInt("theme_mode", 0)) {
            0 -> binding.radioGroupTheme.check(R.id.radio_theme_auto)
            1 -> binding.radioGroupTheme.check(R.id.radio_theme_light)
            2 -> binding.radioGroupTheme.check(R.id.radio_theme_dark)
        }

        // 字体大小
        binding.sliderFontSize.value = prefs.getInt("font_size", 14).toFloat()
        binding.textFontSize.text = "${prefs.getInt("font_size", 14)} sp"

        // 行号显示
        binding.switchLineNumbers.isChecked = prefs.getBoolean("line_numbers", true)
    }

    /**
     * 设置监听器
     */
    private fun setupListeners() {
        // 项目目录选择
        binding.btnSelectDir.setOnClickListener {
            // 这里可以触发 SAF 目录选择
            Snackbar.make(binding.root, "请在主界面选择项目目录", Snackbar.LENGTH_SHORT).show()
        }

        // 自动补全开关
        binding.switchAutoComplete.setOnCheckedChangeListener { _, isChecked ->
            GodotDevApp.prefs.edit().putBoolean("auto_complete", isChecked).apply()
        }

        // 主题模式
        binding.radioGroupTheme.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.radio_theme_auto -> 0
                R.id.radio_theme_light -> 1
                R.id.radio_theme_dark -> 2
                else -> 0
            }
            GodotDevApp.prefs.edit().putInt("theme_mode", mode).apply()
        }

        // 字体大小
        binding.sliderFontSize.addOnChangeListener { _, value, _ ->
            val size = value.toInt()
            binding.textFontSize.text = "$size sp"
            GodotDevApp.prefs.edit().putInt("font_size", size).apply()
        }

        // 行号显示
        binding.switchLineNumbers.setOnCheckedChangeListener { _, isChecked ->
            GodotDevApp.prefs.edit().putBoolean("line_numbers", isChecked).apply()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
