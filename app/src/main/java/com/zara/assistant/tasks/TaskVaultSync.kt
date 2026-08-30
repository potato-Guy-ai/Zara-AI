package com.zara.assistant.tasks

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.zara.assistant.memory.MemoryManager
import com.zara.assistant.utils.ZaraLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Phase 6 — asynchronous Obsidian vault mirror for the task system.
 *
 * TaskRepository remains the ONLY source of truth; this is a one-way,
 * best-effort mirror. Every entry point launches a coroutine on
 * [Dispatchers.IO] and returns immediately — callers inside TaskRepository
 * never block on disk I/O, and any failure is caught and logged so task
 * creation/completion/scheduling/notifications/widgets are unaffected.
 *
 * Vault destination (highest priority first):
 *  1. A SAF tree URI persisted via [setVaultTreeUri] — the real Obsidian
 *     vault the user picked with the Storage Access Framework. Writes go
 *     through DocumentsContract/ContentResolver, so scoped storage is not a
 *     blocker and the notes are actually visible to Obsidian.
 *  2. Absolute path stored in MemoryManager under [KEY_VAULT_PATH] (legacy
 *     configuration, plain File writes).
 *  3. An app-private fallback (Context.getExternalFilesDir) so the mirror
 *     stays exercised out of the box. NOTE: on Android 11+ scoped storage,
 *     other apps — including Obsidian — CANNOT see this default.
 *
 * Notes are plain Markdown designed to read naturally in Obsidian: YAML
 * frontmatter with tags/metadata plus a checkbox body line. Content is fully
 * REGENERATED from the TaskModel on each sync (idempotent overwrite of a
 * stable filename derived from creation date + slug + id prefix), so no
 * fragile parsing of previously written files.
 *
 * Concurrency: each sync launch runs on Dispatchers.IO and regenerates the
 * note from the current TaskModel (idempotent overwrite). No separate mutex
 * is needed because the Repository mutex already serializes all task
 * mutations, ensuring vault writes respect mutation order.
 */
object TaskVaultSync {

    private const val TAG = "[TaskVault]"

    /** DataStore key holding the absolute path of the user's Obsidian vault. */
    private const val KEY_VAULT_PATH = "zara_obsidian_vault_path"

    /** DataStore key holding the persisted SAF tree URI string. */
    private const val KEY_VAULT_URI = "zara_obsidian_tree_uri"

    private const val TASKS_DIR = "Zara Tasks"
    private const val ARCHIVE_DIR = "Archive"

    /** App-private fallback root subfolder, used until a real vault is configured. */
    private const val DEFAULT_VAULT_DIRNAME = "Zara Vault"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Logged only once when a configured vault destination is missing. */
    @Volatile
    private var warnedMissingVault = false

    // ── Public fire-and-forget API ────────────────────────────────────────────

    /** Create or refresh the note for [task]. */
    fun upsert(memory: MemoryManager, task: TaskModel) {
        launchSync(memory) { it.writeTask(task) }
    }

    /**
     * Mirrors TaskRepository.archiveOld(): moves each archived task's note
     * into "<vault>/Zara Tasks/Archive/" instead of deleting it, preserving
     * history in Obsidian. Only tasks the repository actually removed arrive
     * here — important and active OVERDUE tasks are excluded upstream by the
     * repository's policy and therefore never touched.
     */
    fun archive(memory: MemoryManager, tasks: List<TaskModel>) {
        if (tasks.isEmpty()) return
        launchSync(memory) { it.archiveTasks(tasks) }
    }

    /** Point the mirror at an absolute vault directory (plain File writes). */
    fun setVaultPath(memory: MemoryManager, absolutePath: String) {
        scope.launch {
            try {
                persistVaultPath(memory, absolutePath)
            } catch (e: Exception) {
                ZaraLogger.e("$TAG vault path set failed: ${e.message}")
            }
        }
    }

    /** Suspend variant of [setVaultPath] for callers that want to await the write. */
    suspend fun persistVaultPath(memory: MemoryManager, absolutePath: String) {
        memory.set(KEY_VAULT_PATH, absolutePath)
        memory.delete(KEY_VAULT_URI)
        ZaraLogger.d("$TAG vault path set: $absolutePath")
    }

    /**
     * Point the mirror at a SAF tree URI. The caller is responsible for having
     * persisted the read+write permission (takePersistableUriPermission)
     * before calling this, so the grant survives process restarts. Suspends
     * until the configuration lands in DataStore, so callers can immediately
     * re-mirror tasks without racing the async wrapper below.
     */
    suspend fun persistVaultTreeUri(memory: MemoryManager, treeUri: Uri) {
        memory.set(KEY_VAULT_URI, treeUri.toString())
        memory.delete(KEY_VAULT_PATH)
        ZaraLogger.d("$TAG vault tree URI set: $treeUri")
    }

    /** Fire-and-forget wrapper around [persistVaultTreeUri]. */
    fun setVaultTreeUri(memory: MemoryManager, treeUri: Uri) {
        scope.launch {
            try {
                persistVaultTreeUri(memory, treeUri)
            } catch (e: Exception) {
                ZaraLogger.e("$TAG vault tree URI set failed: ${e.message}")
            }
        }
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private fun launchSync(memory: MemoryManager, block: suspend (VaultSink) -> Unit) {
        scope.launch {
            try {
                resolveSink(memory)?.let { block(it) }
            } catch (e: Exception) {
                ZaraLogger.e("$TAG sync failed: ${e.message}")
            }
        }
    }

    /**
     * Selects the vault sink: persisted SAF tree URI first, then the legacy
     * absolute path, then the app-private default directory. Returns null only
     * when even the fallback is unavailable (getExternalFilesDir() null).
     */
    private suspend fun resolveSink(memory: MemoryManager): VaultSink? {
        val uriStr = readKey(memory, KEY_VAULT_URI)
        if (uriStr.isNotEmpty()) {
            val parsed = runCatching { Uri.parse(uriStr) }.getOrNull()
            if (parsed != null && hasPersistedWrite(memory.context, parsed)) {
                warnedMissingVault = false
                return TreeVaultSink(memory.context, parsed)
            }
            if (!warnedMissingVault) {
                warnedMissingVault = true
                ZaraLogger.e("$TAG saved vault tree URI not accessible — using default vault instead")
            }
        }

        val configured = readKey(memory, KEY_VAULT_PATH)
        if (configured.isNotEmpty()) {
            val dir = File(configured)
            if (dir.isDirectory) {
                warnedMissingVault = false
                return FileVaultSink(dir)
            }
            if (!warnedMissingVault) {
                warnedMissingVault = true
                ZaraLogger.e("$TAG configured vault path missing: $configured — using default vault instead")
            }
        }

        val appRoot = memory.context.getExternalFilesDir(null) ?: return null
        return FileVaultSink(File(appRoot, DEFAULT_VAULT_DIRNAME).apply { mkdirs() })
    }

    private suspend fun readKey(memory: MemoryManager, key: String): String = try {
        memory.get(key)?.trim().orEmpty()
    } catch (e: Exception) {
        ZaraLogger.e("$TAG read $key failed: ${e.message}")
        ""
    }

    private fun hasPersistedWrite(context: Context, uri: Uri): Boolean = try {
        context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }
    } catch (e: Exception) {
        false
    }

    /** Stable across state changes → updates overwrite the same note. */
    private fun noteFileName(task: TaskModel): String {
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(task.createdAt))
        return "$day-${slug(task.body)}-${task.id.take(8)}"
    }

    private fun slug(body: String): String =
        body.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(40)
            .ifEmpty { "task" }

    private fun buildMarkdown(task: TaskModel): String {
        val createdFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val sb = StringBuilder()

        // ── Frontmatter ──
        sb.append("---\n")
        sb.append("tags:\n  - zara-task\n")
        if (task.isImportant()) sb.append("  - important\n")
        sb.append("zara-id: ").append(task.id).append('\n')
        sb.append("state: ").append(task.state.name.lowercase(Locale.US)).append('\n')
        sb.append("created: ").append(createdFmt.format(Date(task.createdAt))).append('\n')
        task.effectiveTriggerMs()?.let {
            sb.append("due: ").append(createdFmt.format(Date(it))).append('\n')
        }
        task.completedAt?.let {
            sb.append("completed: ").append(createdFmt.format(Date(it))).append('\n')
        }
        task.recurrence?.let {
            sb.append("recurrence: ").append(it.type.name.lowercase(Locale.US)).append('\n')
        }
        sb.append("---\n\n")

        // ── Body ──
        val box = if (task.state == TaskState.DONE || task.state == TaskState.CANCELLED) "x" else " "
        sb.append("- [$box] ").append(task.body).append('\n')

        val trigger = task.effectiveTriggerMs()
        val humanTime = SimpleDateFormat("EEE, MMM d yyyy h:mm a", Locale.getDefault())
        when {
            task.recurrence != null && trigger != null ->
                sb.append("\nRecurring ").append(task.recurrence.type.name.lowercase(Locale.US))
                    .append(", next ").append(humanTime.format(Date(trigger))).append(".\n")
            trigger != null && task.state != TaskState.DONE && task.state != TaskState.CANCELLED ->
                sb.append("\nScheduled for **").append(humanTime.format(Date(trigger))).append("**.\n")
            task.completedAt != null ->
                sb.append("\nCompleted ").append(humanTime.format(Date(task.completedAt))).append(".\n")
        }
        return sb.toString()
    }

    // ── Vault sinks ───────────────────────────────────────────────────────────

    private interface VaultSink {
        fun writeTask(task: TaskModel)
        fun archiveTasks(tasks: List<TaskModel>)
    }

    /**
     * Plain-File sink used for the legacy absolute path and the app-private
     * default vault. Same behavior as the original Phase 6 implementation.
     */
    private class FileVaultSink(private val root: File) : VaultSink {
        private fun tasksDir(): File = File(root, TASKS_DIR).apply { mkdirs() }

        override fun writeTask(task: TaskModel) {
            val file = File(tasksDir(), noteFileName(task) + ".md")
            file.writeText(buildMarkdown(task))
            ZaraLogger.d("$TAG wrote ${file.name} state=${task.state}")
        }

        override fun archiveTasks(tasks: List<TaskModel>) {
            val dir = tasksDir()
            val archiveDir = File(dir, ARCHIVE_DIR).apply { mkdirs() }
            for (task in tasks) {
                val src = File(dir, noteFileName(task) + ".md")
                if (!src.isFile) continue  // never synced or already moved
                var dst = File(archiveDir, src.name)
                if (dst.exists()) {
                    dst = File(archiveDir, src.nameWithoutExtension + "-" + System.currentTimeMillis() + ".md")
                }
                if (!src.renameTo(dst)) {
                    ZaraLogger.d("$TAG move failed for ${src.name}")
                }
            }
        }
    }

    /**
     * SAF sink — writes into the user-picked Obsidian vault through
     * DocumentsContract/ContentResolver so scoped storage is not a blocker.
     * Used only when the tree URI has a persisted write permission.
     */
    private class TreeVaultSink(
        private val context: Context,
        private val treeUri: Uri
    ) : VaultSink {

        private val resolver get() = context.contentResolver

        override fun writeTask(task: TaskModel) {
            val dir = findOrCreateDir(treeUri, TASKS_DIR) ?: return
            val base = noteFileName(task) + ".md"
            val doc = findChild(dir, base) ?: createFile(dir, base) ?: return
            val ok = try {
                val out = resolver.openOutputStream(doc, "wt")
                if (out == null) {
                    false
                } else {
                    out.use { it.write(buildMarkdown(task).toByteArray(Charsets.UTF_8)) }
                    true
                }
            } catch (e: Exception) {
                ZaraLogger.e("$TAG write $base failed: ${e.message}")
                false
            }
            if (ok) ZaraLogger.d("$TAG wrote $base state=${task.state}")
        }

        override fun archiveTasks(tasks: List<TaskModel>) {
            val dir = findOrCreateDir(treeUri, TASKS_DIR) ?: return
            val archiveDir = findOrCreateDir(dir, ARCHIVE_DIR) ?: return
            for (task in tasks) {
                val base = noteFileName(task) + ".md"
                val src = findChild(dir, base) ?: continue
                if (!moveDoc(src, archiveDir, base)) {
                    ZaraLogger.d("$TAG move failed for $base")
                }
            }
        }

        private fun findOrCreateDir(parent: Uri, name: String): Uri? {
            findChild(parent, name)?.let { return it }
            return try {
                DocumentsContract.createDocument(
                    resolver, parent, DocumentsContract.Document.MIME_TYPE_DIR, name
                )
            } catch (e: Exception) {
                ZaraLogger.e("$TAG create dir $name failed: ${e.message}")
                null
            }
        }

        private fun createFile(parent: Uri, name: String): Uri? = try {
            DocumentsContract.createDocument(resolver, parent, "text/markdown", name)
        } catch (e: Exception) {
            ZaraLogger.e("$TAG create file $name failed: ${e.message}")
            null
        }

        private fun findChild(parent: Uri, name: String): Uri? {
            val match = children(parent).firstOrNull { it.displayName == name } ?: return null
            return DocumentsContract.buildDocumentUriUsingTree(treeUri, match.docId)
        }

        private fun children(parent: Uri): List<ChildDoc> = try {
            val parentDocId = DocumentsContract.getDocumentId(parent)
            val childrenUri =
                DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
            val cols = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            )
            val result = mutableListOf<ChildDoc>()
            resolver.query(childrenUri, cols, null, null, null)?.use { c ->
                val idCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (c.moveToNext()) {
                    val docId = if (idCol >= 0) c.getString(idCol) else null
                    if (docId == null) continue
                    result.add(
                        ChildDoc(
                            docId = docId,
                            displayName = if (nameCol >= 0) c.getString(nameCol) ?: "" else "",
                            mimeType = if (mimeCol >= 0) c.getString(mimeCol) ?: "" else ""
                        )
                    )
                }
            }
            result
        } catch (e: Exception) {
            ZaraLogger.e("$TAG list children failed: ${e.message}")
            emptyList()
        }

        /**
         * Moves [src] into [destDir] by copy + delete. DocumentFile-style
         * rename can't move across directories on SAF, so always copy content,
         * then delete the source. Succeeds even when read/write are split
         * across providers.
         */
        private fun moveDoc(src: Uri, destDir: Uri, baseName: String): Boolean {
            var target = baseName
            if (findChild(destDir, target) != null) {
                target = baseName.removeSuffix(".md") + "-" + System.currentTimeMillis() + ".md"
            }
            val copied = try {
                val input = resolver.openInputStream(src) ?: return false
                input.use { stream ->
                    val created = createFile(destDir, target) ?: return false
                    val out = resolver.openOutputStream(created, "w") ?: return false
                    out.use { stream.copyTo(it) }
                }
                true
            } catch (e: Exception) {
                ZaraLogger.e("$TAG copy $baseName failed: ${e.message}")
                false
            }
            if (!copied) return false
            return try {
                DocumentsContract.deleteDocument(resolver, src)
                true
            } catch (e: Exception) {
                ZaraLogger.e("$TAG delete source $baseName failed: ${e.message} (copy left in place)")
                false
            }
        }

        private data class ChildDoc(val docId: String, val displayName: String, val mimeType: String)
    }
}