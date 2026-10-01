package com.godot.devassistant.data

import android.content.Context
import android.net.Uri
import com.godot.devassistant.model.GodotProject
import com.godot.devassistant.model.ScriptFile
import com.godot.devassistant.util.GodotProjectInfo
import com.godot.devassistant.util.SafEntry
import com.godot.devassistant.util.SafFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 项目数据仓库
 * 封装 SAF 文件操作与项目扫描逻辑
 */
class ProjectRepository(private val context: Context) {

    /**
     * 扫描根目录下的所有 Godot 项目
     */
    suspend fun scanProjects(rootUri: Uri): List<GodotProject> = withContext(Dispatchers.IO) {
        val infos = SafFileManager.scanForGodotProjects(context, rootUri)
        infos.map { info ->
            // 为每个项目加载脚本列表
            val scripts = SafFileManager.listScriptsInProject(context, info.uri)
            GodotProject(
                name = info.name,
                uri = info.uri,
                projectFileUri = info.projectFileUri,
                scripts = scripts.map { script ->
                    ScriptFile(
                        name = script.name,
                        uri = script.uri,
                        relativePath = script.name
                    )
                }
            )
        }
    }

    /**
     * 加载项目脚本树
     */
    suspend fun loadScriptTree(projectUri: Uri): List<ScriptFile> = withContext(Dispatchers.IO) {
        val scripts = SafFileManager.listScriptsInProject(context, projectUri)
        scripts.map { script ->
            ScriptFile(
                name = script.name,
                uri = script.uri,
                relativePath = script.name
            )
        }
    }

    /**
     * 读取脚本内容
     */
    suspend fun readScript(scriptUri: Uri): String {
        return SafFileManager.readFile(context, scriptUri)
    }

    /**
     * 保存脚本内容
     */
    suspend fun saveScript(scriptUri: Uri, content: String): Boolean {
        return SafFileManager.writeFile(context, scriptUri, content)
    }

    /**
     * 创建新脚本文件
     */
    suspend fun createScript(parentUri: Uri, fileName: String): Uri? {
        return SafFileManager.createFile(context, parentUri, fileName, "text/plain")
    }

    /**
     * 删除脚本文件
     */
    suspend fun deleteScript(scriptUri: Uri): Boolean {
        return SafFileManager.deleteFile(context, scriptUri)
    }
}
