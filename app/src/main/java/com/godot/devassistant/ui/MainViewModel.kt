package com.godot.devassistant.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.godot.devassistant.data.ProjectRepository
import com.godot.devassistant.model.GodotProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 主界面 ViewModel
 * 管理项目扫描、加载状态
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ProjectRepository(application)

    /** 项目列表 */
    private val _projects = MutableLiveData<List<GodotProject>>()
    val projects: LiveData<List<GodotProject>> = _projects

    /** 加载状态 */
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    /** 错误信息 */
    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    /** 根目录 Uri */
    private val _rootUri = MutableLiveData<Uri?>(null)
    val rootUri: LiveData<Uri?> = _rootUri

    /** 当前选中的项目 */
    private val _selectedProject = MutableLiveData<GodotProject?>(null)
    val selectedProject: LiveData<GodotProject?> = _selectedProject

    /**
     * 设置根目录并扫描项目
     */
    fun setRootDirectory(uri: Uri) {
        _rootUri.value = uri
        scanProjects(uri)
    }

    /**
     * 扫描项目
     */
    fun scanProjects(rootUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.postValue(true)
            _error.postValue(null)
            try {
                val list = repository.scanProjects(rootUri)
                _projects.postValue(list)
            } catch (e: Exception) {
                _error.postValue("扫描项目失败: ${e.message}")
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    /**
     * 刷新当前根目录
     */
    fun refresh() {
        _rootUri.value?.let { scanProjects(it) }
    }

    /**
     * 选择项目
     */
    fun selectProject(project: GodotProject) {
        _selectedProject.value = project
    }
}
