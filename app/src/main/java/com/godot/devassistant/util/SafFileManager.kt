package com.godot.devassistant.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
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

    /**
     * 创建新文件，并在 MTP 篡改扩展名后自动重命名兜底。
     *
     * Android MTP provider 在部分实现上会无视 mime 追加 ".txt"，
     * 不同 ROM / 目录表现不一致。稳妥做法：
     *   1. 按请求名创建（octet-stream 尽量不触发追加）
     *   2. 读回实际 name，若不一致 → DocumentsContract.renameDocument
     *   3. 失败则走 copy-fallback（新建 + 复制 + 删旧）
     *
     * 返回的 Uri 在 rename 后仍指向同一物理文件（documentId 稳定），
     * 上层继续用它读写字节即可。
     */
    suspend fun createFile(
        context: Context,
        parentUri: Uri,
        fileName: String,
        mimeType: String = "application/octet-stream"
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val doc = DocumentFile.fromTreeUri(context, parentUri)
            val created = doc?.createFile(mimeType, fileName)
            if (created == null || !created.exists()) {
                Log.w(TAG, "创建文件失败: $fileName")
                null
            } else {
                val actual = created.name
                if (actual == fileName) {
                    created.uri
                } else {
                    Log.w(TAG, "MTP 篡改了扩展名: 请求=$fileName 实际=$actual，尝试重命名")
                    val ok = tryRename(context, created, fileName)
                    if (ok) {
                        Log.i(TAG, "重命名成功 → $fileName")
                    } else {
                        Log.e(TAG, "重命名失败，保留实际名字 $actual（上层会提示用户）")
                    }
                    created.uri
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "创建文件异常", e)
            null
        }
    }

    /**
     * 重命名兜底。优先 DocumentsContract.renameDocument（API 26+ 原生支持 MTP），
     * 失败则走 copy-fallback。
     */
    private fun tryRename(context: Context, doc: DocumentFile, newName: String): Boolean {
        // 方案 1：DocumentsContract.renameDocument
        try {
            val result = DocumentsContract.renameDocument(context.contentResolver, doc.uri, newName)
            if (result != null && result.exists()) {
                Log.i(TAG, "DocumentsContract.renameDocument 成功")
                return true
            }
            Log.w(TAG, "DocumentsContract.renameDocument 返回 null / 不存在，走 copy-fallback")
        } catch (e: Throwable) {
            Log.w(TAG, "DocumentsContract.renameDocument 异常，走 copy-fallback", e)
        }

        // 方案 2：copy-fallback（读内容 → 新建 → 删旧）
        return renameByCopy(context, doc, newName)
    }

    private fun renameByCopy(context: Context, doc: DocumentFile, newName: String): Boolean {
        return try {
            val content = readFile(context, doc.uri)

            val parent = doc.parentFile ?: run {
                Log.e(TAG, "copy-fallback: 取不到父目录")
                return false
            }
            val newDoc = parent.createFile("application/octet-stream", newName) ?: run {
                Log.e(TAG, "copy-fallback: 创建新文件失败 $newName")
                return false
            }
            writeFile(context, newDoc.uri, content)
            deleteFile(context, doc.uri)
            Log.i(TAG, "copy-fallback: 成功 $newName")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "copy-fallback 失败", e)
            false
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
