package com.godot.devassistant.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.godot.devassistant.GodotDevApp
import com.godot.devassistant.R
import com.godot.devassistant.adapter.ProjectAdapter
import com.godot.devassistant.databinding.ActivityMainBinding
import com.godot.devassistant.util.SafFileManager

/**
 * 主界面：项目列表
 *
 * 功能：
 * - 通过 SAF 选择项目根目录
 * - 自动扫描子文件夹中的 Godot 项目
 * - 显示项目列表，点击进入编辑器
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var projectAdapter: ProjectAdapter

    // SAF 目录选择回调
    private val openTreeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                SafFileManager.persistTreePermission(this, uri)
                viewModel.setRootDirectory(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        setupRecyclerView()
        observeViewModel()
        checkSavedRootUri()
    }

    /**
     * 设置项目列表
     */
    private fun setupRecyclerView() {
        projectAdapter = ProjectAdapter { project ->
            // 点击项目，进入编辑器
            val intent = Intent(this, EditorActivity::class.java).apply {
                putExtra("project_name", project.name)
                putExtra("project_uri", project.uri.toString())
            }
            startActivity(intent)
        }
        binding.recyclerProjects.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = projectAdapter
        }
    }

    /**
     * 观察 ViewModel 数据
     */
    private fun observeViewModel() {
        viewModel.projects.observe(this) { projects ->
            projectAdapter.submitList(projects)
            binding.emptyView.visibility = if (projects.isEmpty()) View.VISIBLE else View.GONE
            if (projects.isEmpty()) {
                binding.emptyView.setOnClickListener {
                    openTreeLauncher.launch(SafFileManager.createOpenTreeIntent())
                }
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(this) { error ->
            error?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    /**
     * 检查已保存的根目录
     */
    private fun checkSavedRootUri() {
        SafFileManager.getSavedRootUri(this)?.let { uri ->
            viewModel.setRootDirectory(uri)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_select_dir -> {
                // 选择项目根目录
                openTreeLauncher.launch(SafFileManager.createOpenTreeIntent())
                true
            }
            R.id.action_refresh -> {
                viewModel.refresh()
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_docs -> {
                startActivity(Intent(this, DocBrowserActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
