package ru.cardly.mobile.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import ru.cardly.core.model.BusinessCard
import ru.cardly.core.model.Card

/**
 * Автоматическая резервная копия: раз в день или раз в неделю, хранятся последние [KEEP] файлов.
 * Папка — «Cardly» в корне памяти (выбирается один раз через системное окно).
 * Пока папка не выбрана, копии пишутся в «Загрузки/Cardly».
 * Копия не создаётся, если карты и визитки не менялись с прошлого раза.
 */
object AutoBackup {
    private const val KEEP = 10
    private const val PREFIX = "Cardly-auto-"
    private const val PREFS = "cardly_auto_backup"
    private const val DAY_MS = 24L * 60 * 60 * 1000

    data class State(val lastTime: Long, val folderUri: String?)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun state(context: Context) = prefs(context).let { State(it.getLong("last", 0L), it.getString("folder", null)) }

    fun setFolder(context: Context, uri: Uri?) {
        prefs(context).edit().putString("folder", uri?.toString()).putBoolean("asked", true).apply()
    }

    /** Нужно ли один раз предложить выбрать папку «Cardly» в корне памяти. */
    fun shouldAskFolder(context: Context): Boolean {
        val p = prefs(context)
        return Build.VERSION.SDK_INT >= 26 && p.getString("folder", null) == null && !p.getBoolean("asked", false)
    }

    fun markAsked(context: Context) { prefs(context).edit().putBoolean("asked", true).apply() }

    /** Окно выбора папки открывается сразу в корне внутренней памяти (или в «Cardly», если она уже есть). */
    fun initialFolderUri(): Uri =
        DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:")

    /** Название выбранной папки для показа в настройках. */
    fun folderTitle(context: Context): String {
        val folder = state(context).folderUri ?: return "Загрузки › Cardly"
        return runCatching {
            val id = Uri.decode(DocumentsContract.getTreeDocumentId(Uri.parse(folder)))
            val path = id.substringAfter(':').trim('/').ifBlank { "Cardly" }.replace("/", " › ")
            if (id.startsWith("primary:")) "Память › $path" else path
        }.getOrDefault("Выбранная папка")
    }

    /** Делает копию, если пора и данные менялись. Вызывать из фонового потока. */
    fun runIfDue(context: Context, everyDays: Int, cards: List<Card>, contacts: List<BusinessCard>, force: Boolean = false): Boolean {
        android.util.Log.i("CardlyBackup", "check every=$everyDays cards=${cards.size} contacts=${contacts.size}")
        if (everyDays <= 0 && !force) return false
        if (cards.isEmpty() && contacts.isEmpty()) return false
        val prefs = prefs(context)
        val now = System.currentTimeMillis()
        val signature = (cards.joinToString { "${it.id}:${it.updatedAt}" } + "|" + contacts.joinToString { "${it.id}:${it.updatedAt}" }).hashCode()
        if (!force) {
            if (now - prefs.getLong("last", 0L) < everyDays * DAY_MS) return false
            if (prefs.getInt("signature", 0) == signature && prefs.getLong("last", 0L) > 0) return false
        }
        val name = PREFIX + SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date(now)) + ".json"
        val content = Backup.Content(cards, contacts)
        val ok = runCatching {
            val folder = prefs.getString("folder", null)
            when {
                folder != null -> writeToTree(context, Uri.parse(folder), name, content)
                Build.VERSION.SDK_INT >= 29 -> writeToDownloads(context, name, content)
                else -> writeToAppFolder(context, name, content)
            }
        }.onFailure { android.util.Log.w("CardlyBackup", "auto backup failed", it) }.getOrDefault(false)
        android.util.Log.i("CardlyBackup", "auto backup $name ok=$ok")
        if (ok) prefs.edit().putLong("last", now).putInt("signature", signature).apply()
        return ok
    }

    // ---------- Выбранная пользователем папка ----------

    private fun writeToTree(context: Context, tree: Uri, name: String, content: Backup.Content): Boolean {
        val resolver = context.contentResolver
        val parent = cardlyFolder(context, tree) ?: return false
        val file = DocumentsContract.createDocument(resolver, parent, "application/json", name) ?: return false
        if (!Backup.export(context, file, content)) return false
        // Оставляем только последние KEEP автокопий.
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(parent))
        val found = mutableListOf<Pair<String, String>>() // имя, id
        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID),
            null, null, null,
        )?.use { c -> while (c.moveToNext()) found += c.getString(0).orEmpty() to c.getString(1) }
        found.filter { it.first.startsWith(PREFIX) }.sortedByDescending { it.first }.drop(KEEP).forEach { (_, id) ->
            runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, id)) }
        }
        return true
    }

    /**
     * Если выбран корень памяти (можно на Android 8–10), сами создаём в нём папку «Cardly».
     * Иначе пишем прямо в выбранную папку.
     */
    private fun cardlyFolder(context: Context, tree: Uri): Uri? {
        val resolver = context.contentResolver
        val treeId = DocumentsContract.getTreeDocumentId(tree)
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, treeId)
        if (!treeId.endsWith(":")) return root
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId)
        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(0) == "Cardly" && c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR)
                    return DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(1))
            }
        }
        return DocumentsContract.createDocument(resolver, root, DocumentsContract.Document.MIME_TYPE_DIR, "Cardly")
    }

    // ---------- «Загрузки/Cardly» (Android 10+) ----------

    private fun writeToDownloads(context: Context, name: String, content: Backup.Content): Boolean {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Cardly")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: return false
        val ok = Backup.export(context, uri, content)
        if (!ok) { resolver.delete(uri, null, null); return false }
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        // Приложение видит только свои файлы — удаляем лишние старые автокопии.
        val old = mutableListOf<Long>()
        resolver.query(
            collection, arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?", arrayOf("$PREFIX%"),
            "${MediaStore.MediaColumns.DISPLAY_NAME} DESC",
        )?.use { c -> while (c.moveToNext()) old += c.getLong(0) }
        old.drop(KEEP).forEach { id -> runCatching { resolver.delete(android.content.ContentUris.withAppendedId(collection, id), null, null) } }
        return true
    }

    // ---------- Android 8–9: папка приложения ----------

    private fun writeToAppFolder(context: Context, name: String, content: Backup.Content): Boolean {
        val dir = File(context.getExternalFilesDir(null), "Cardly").apply { mkdirs() }
        val file = File(dir, name)
        if (!Backup.export(context, Uri.fromFile(file), content)) return false
        dir.listFiles()?.filter { it.name.startsWith(PREFIX) }?.sortedByDescending { it.name }?.drop(KEEP)?.forEach { it.delete() }
        return true
    }
}
