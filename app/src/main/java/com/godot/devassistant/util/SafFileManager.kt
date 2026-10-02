package com.godot.devassistant.util

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException

/**
 * SAF（Storage Access Framework）文件管理器
 *
 * 职责：
 * 1. 通过 ACTION_OPEN_DOCUMENT_TREE 获取目录访问权限
 * 2. 持久化保存目录授权（takePersistableUriPermission）
 * 3. 扫描目录树，查找 project.godot
 * 4. 读写 .gd 文件
 */
object SafFileManager {

    private const val TAG = "SafFileManager"
    private const val PREFS_NAME = "saf_uris"
    private const val KEY_ROOT_URI = "root_uri"

    /** 请求目录权限的 Intent */
    fun createOpenTreeIntent(): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        }
    }

    /**
     * 保存目录授权（持久化）
     */
    fun persistTreePermission(context: Context, treeUri: Uri): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        try {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            prefs.edit().putString(KEY_ROOT_URI, treeUri.toString()).apply()
            Log.d(TAG, "已持久化目录授权: $treeUri")
            return treeUri.toString()
        } catch (e: SecurityException) {
            Log.e(TAG, "持久化授权失败", e)
            return ""
        }
    }

    /**
     * 获取已保存的根目录 URI
     */
    fun getSavedRootUri(context: Context): Uri? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uriStr = prefs.getString(KEY_ROOT_URI, null) ?: return null
        return Uri.parse(uriStr)
    }

    /**
     * 清除已保存的目录授权
     */
    fun clearSavedRootUri(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_ROOT_URI).apply()
    }

    /**
     * 获取树 URI 对应的文档 ID
     */
    fun getTreeDocumentId(treeUri: Uri): String {
        return DocumentsContract.getTreeDocumentId(treeUri)
    }

    /**
     * 构建子文档的 Uri
     */
    fun buildChildUri(treeUri: Uri, parentDocId: String, childName: String): Uri {
        val childDocId = "$parentDocId/$childName"
        return DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
    }

    /**
     * 列出目录下的所有子项（递归扫描）
     *
     * @param context 上下文
     * @param dirUri 目录 Uri
     * @return 子项列表（包含 name, uri, isDir）
     */
    suspend fun listDirectory(context: Context, dirUri: Uri): List<SafEntry> =
        withContext(Dispatchers.IO) {
            val entries = mutableListOf<SafEntry>()
            val resolver = context.contentResolver

            try {
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                    dirUri, DocumentsContract.getDocumentId(dirUri)
                )

                val projection = arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                )

                resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)

                    while (cursor.moveToNext()) {
                        val docId = cursor.getString(idCol)
                        val name = cursor.getString(nameCol) ?: "unnamed"
                        val mime = cursor.getString(mimeCol) ?: ""

                        val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                        val uri = DocumentsContract.buildDocumentUriUsingTree(dirUri, docId)

                        entries.add(SafEntry(name, uri, isDir, docId))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "列出目录失败: ${dirUri}", e)
            }

            entries
        }

    /**
     * 递归扫描整个目录树，查找所有 Godot 项目
     *
     * 判定规则：子文件夹内存在 project.godot 文件即视为项目，
     * 项目名取文件夹名。
     */
    suspend fun scanForGodotProjects(
        context: Context,
        rootUri: Uri,
        maxDepth: Int = 8
    ): List<GodotProjectInfo> = withContext(Dispatchers.IO) {
        val projects = mutableListOf<GodotProjectInfo>()
        scanRecursive(context, rootUri, 0, maxDepth, projects)
        projects
    }

    private fun scanRecursive(
        context: Context,
        dirUri: Uri,
        depth: Int,
        maxDepth: Int,
        result: MutableList<GodotProjectInfo>
    ) {
        if (depth > maxDepth) return

        // 列出当前目录
        val entries = runCatching {
            // 使用同步方式列出（已在 IO 线程）
            listDirectorySync(context, dirUri)
        }.getOrDefault(emptyList())

        for (entry in entries) {
            if (!entry.isDir) continue

            // 检查子目录内是否有 project.godot
            val hasProjectFile = runCatching {
                findFileInDir(context, entry.uri, "project.godot")
            }.getOrDefault(false)

            if (hasProjectFile) {
                result.add(GodotProjectInfo(
                    name = entry.name,
                    uri = entry.uri,
                    projectFileUri = findFileUri(context, entry.uri, "project.godot")
                ))
            } else if (depth < maxDepth) {
                // 递归扫描子目录（限定深度，避免过深扫描）
                scanRecursive(context, entry.uri, depth + 1, maxDepth, result)
            }
        }
    }

    /** 同步版列出目录（供 IO 线程内部使用） */
    private fun listDirectorySync(context: Context, dirUri: Uri): List<SafEntry> {
        val entries = mutableListOf<SafEntry>()
        val resolver = context.contentResolver

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            dirUri, DocumentsContract.getDocumentId(dirUri)
        )

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )

        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)

            while (cursor.moveToNext()) {
                val docId = cursor.getString(idCol)
                val name = cursor.getString(nameCol) ?: "unnamed"
                val mime = cursor.getString(mimeCol) ?: ""
                val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                val uri = DocumentsContract.buildDocumentUriUsingTree(dirUri, docId)
                entries.add(SafEntry(name, uri, isDir, docId))
            }
        }
        return entries
    }

    /**
     * 在指定目录中查找文件
     * @return 是否找到
     */
    private fun findFileInDir(context: Context, dirUri: Uri, fileName: String): Boolean {
        return findFileUri(context, dirUri, fileName) != null
    }

    /**
     * 在指定目录中查找文件并返回其 Uri
     */
    private fun findFileUri(context: Context, dirUri: Uri, fileName: String): Uri? {
        val resolver = context.contentResolver
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            dirUri, DocumentsContract.getDocumentId(dirUri)
        )

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )

        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val name = cursor.getString(nameCol) ?: continue
                if (name == fileName) {
                    val docId = cursor.getString(idCol)
                    return DocumentsContract.buildDocumentUriUsingTree(dirUri, docId)
                }
            }
        }
        return null
    }

    /**
     * 列出项目内所有 .gd 脚本
     */
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

        val entries = runCatching {
            listDirectorySync(context, dirUri)
        }.getOrDefault(emptyList())

        for (entry in entries) {
            if (entry.isDir) {
                // 跳过 .godot 缓存目录
                if (entry.name.startsWith(".")) continue
                collectScripts(context, entry.uri, result, depth + 1, maxDepth)
            } else if (entry.name.endsWith(".gd")) {
                result.add(entry)
            }
        }
    }

    /**
     * 读取文件内容（通过 SAF）
     */
    suspend fun readFile(context: Context, fileUri: Uri): String =
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(fileUri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                } ?: throw FileNotFoundException("无法打开文件: $fileUri")
            } catch (e: IOException) {
                Log.e(TAG, "读取文件失败", e)
                ""
            }
        }

    /**
     * 写入文件内容（通过 SAF）
     */
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
     * 创建新文件
     * @return 新文件的 Uri，失败返回 null
     */
    suspend fun createFile(
        context: Context,
        parentUri: Uri,
        fileName: String,
        mimeType: String = "text/plain"
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val docId = DocumentsContract.getDocumentId(parentUri)
            val newDocId = "$docId/$fileName"
            val newUri = DocumentsContract.buildDocumentUriUsingTree(parentUri, newDocId)

            // 使用 createDocument 创建
            val created = DocumentsContract.createDocument(
                context.contentResolver, parentUri, mimeType, fileName
            )
            created
        } catch (e: Exception) {
            Log.e(TAG, "创建文件失败", e)
            null
        }
    }

    /**
     * 删除文件
     */
    suspend fun deleteFile(context: Context, fileUri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            try {
                DocumentsContract.deleteDocument(context.contentResolver, fileUri)
            } catch (e: Exception) {
                Log.e(TAG, "删除文件失败", e)
                false
            }
        }
}

/** SAF 目录条目 */
data class SafEntry(
    val name: String,
    val uri: Uri,
    val isDir: Boolean,
    val documentId: String
)

/** Godot 项目信息 */
data class GodotProjectInfo(
    val name: String,
    val uri: Uri,
    val projectFileUri: Uri? = null
)
