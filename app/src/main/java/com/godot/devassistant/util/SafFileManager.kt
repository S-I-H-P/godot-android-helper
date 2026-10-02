package com.godot.devassistant.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

object SafFileManager {

    private const val TAG = "SafFileManager"
    private const val PREFS_NAME = "saf_uris"
    private const val KEY_ROOT_URI = "root_uri"

    fun createOpenTreeIntent(): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        }
    }

    fun persistTreePermission(context: Context, treeUri: Uri): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        try {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            prefs.edit().putString(KEY_ROOT_URI, treeUri.toString()).apply()
            return treeUri.toString()
        } catch (e: SecurityException) {
            Log.e(TAG, "持久化授权失败", e)
            return ""
        }
    }

    fun getSavedRootUri(context: Context): Uri? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uriStr = prefs.getString(KEY_ROOT_URI, null) ?: return null
        return Uri.parse(uriStr)
    }

    fun clearSavedRootUri(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_ROOT_URI).apply()
    }

    suspend fun scanForGodotProjects(
        context: Context,
        rootUri: Uri,
        maxDepth: Int = 8
    ): List<GodotProjectInfo> = withContext(Dispatchers.IO) {
        val projects = mutableListOf<GodotProjectInfo>()
        val rootDoc = DocumentFile.fromTreeUri(context, rootUri)
        if (rootDoc != null) {
            scanRecursive(context, rootDoc, 0, maxDepth, projects)
        }
        projects
    }

    private fun scanRecursive(
        context: Context,
        parentDoc: DocumentFile,
        depth: Int,
        maxDepth: Int,
        result: MutableList<GodotProjectInfo>
    ) {
        if (depth > maxDepth) return

        val subDirs = parentDoc.listFiles()
        if (subDirs.isEmpty()) return

        for (entry in subDirs) {
            if (!entry.isDirectory) continue

            val projectFile = entry.findFile("project.godot")
            if (projectFile != null) {
                result.add(GodotProjectInfo(
                    name = entry.name ?: "unknown",
                    uri = entry.uri,
                    projectFileUri = projectFile.uri
                ))
            } else if (depth < maxDepth) {
                scanRecursive(context, entry, depth + 1, maxDepth, result)
            }
        }
    }

    suspend fun listScriptsInProject(
        context: Context,
        projectUri: Uri
    ): List<SafEntry> = withContext(Dispatchers.IO) {
        val scripts = mutableListOf<SafEntry>()
        collectScripts(context, projectUri, scripts, 0, 6)
        scripts
    }

    private fun collectScripts(
        context: Context,
        dirUri: Uri,
        result: MutableList<SafEntry>,
        depth: Int,
        maxDepth: Int
    ) {
        if (depth > maxDepth) return

        val doc = DocumentFile.fromTreeUri(context, dirUri) ?: return
        val files = doc.listFiles()
        if (files.isEmpty()) return

        for (entry in files) {
            val name = entry.name ?: continue
            if (entry.isDirectory) {
                if (!name.startsWith(".")) {
                    collectScripts(context, entry.uri, result, depth + 1, maxDepth)
                }
            } else if (name.endsWith(".gd")) {
                result.add(SafEntry(name, entry.uri, false, ""))
            }
        }
    }

    suspend fun readFile(context: Context, fileUri: Uri): String =
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(fileUri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                } ?: ""
            } catch (e: IOException) {
                Log.e(TAG, "读取文件失败", e)
                ""
            }
        }

    suspend fun writeFile(context: Context, fileUri: Uri, content: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(fileUri, "wt")?.use { stream ->
                    stream.write(content.toByteArray(Charsets.UTF_8))
                    true
                } ?: false
            } catch (e: IOException) {
                Log.e(TAG, "写入文件失败", e)
                false
            }
        }

    suspend fun createFile(
        context: Context,
        parentUri: Uri,
        fileName: String,
        mimeType: String = "text/plain"
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val doc = DocumentFile.fromTreeUri(context, parentUri)
            val created = doc?.createFile(mimeType, fileName)
            if (created != null && created.exists()) {
                created.uri
            } else {
                Log.w(TAG, "创建文件失败: $fileName")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "创建文件异常", e)
            null
        }
    }

    suspend fun deleteFile(context: Context, fileUri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            try {
                // 用 contentResolver 方式删除，避免 fromFileUri 不存在的问题
                context.contentResolver.delete(fileUri, null, null) > 0
            } catch (e: Exception) {
                Log.e(TAG, "删除文件失败", e)
                false
            }
        }
}

data class SafEntry(
    val name: String,
    val uri: Uri,
    val isDir: Boolean,
    val documentId: String
)

data class GodotProjectInfo(
    val name: String,
    val uri: Uri,
    val projectFileUri: Uri? = null
)
