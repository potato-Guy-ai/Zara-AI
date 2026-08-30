package com.zara.assistant.tasks

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
 * Vault location: absolute path stored in MemoryManager (DataStore) under
 * [KEY_VAULT_PATH] when the user has configured one. Until a settings screen
 * exists to write that key, [resolveVaultRoot] falls back to an app-private
 * default directory (Context.getExternalFilesDir) so the task↔note mirror is
 * exercised out of the box instead of being permanently dormant.
 *
 * IMPORTANT — default-vault caveat: on Android 11+ (scoped storage),
 * getExternalFilesDir() is app-private storage. Other apps, including
 * Obsidian, CANNOT see it. This default proves the mirror logic works and
 * gives the user real Markdown files to inspect (via a file manager with
 * "show hidden/app data" access, or `adb pull`), but it is NOT a substitute
 * for pointing at a real shared Obsidian vault. Doing that for real requires
 * a settings screen that writes [KEY_VAULT_PATH] to a location the user
 * picks (ideally via Storage Access Framework, since scoped storage blocks
 * arbitrary shared paths without it) — not yet built.
 *
 * Notes are plain Markdown designed to read naturally in Obsidian: YAML
 * frontmatter with tags/metadata plus a checkbox body line. Content is fully
 * REGENERATED from the TaskModel on each sync (idempotent overwrite of a
 * stable filename derived from creation date + slug + id prefix), so no
 * fragile parsing of previously written files.
 *
 * Concurrency: each sync launch runs on Dispatchers.IO and regenerates
  the note from the current TaskModel (idempotent overwrite). No separate
  mutex is needed because the Repository mutex already serializes all
  task mutations, ensuring vault writes respect mutation order.
 */
object TaskVaultSync {

    private const val TAG = "[TaskVault]"

    /** DataStore key holding the absolute path of the user's Obsidian vault. */
    private const val KEY_VAULT_PATH = "zara_obsidian_vault_path"

    private const val TASKS_DIR = "Zara Tasks"
    private const val ARCHIVE_DIR = "Archive"

    /** App-private fallback root subfolder, used until a real vault path is configured. */
    private const val DEFAULT_VAULT_DIRNAME = "Zara Vault"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Logged only once when a configured vault directory is missing. */
    @Volatile
    private var warnedMissingVault = false

    // ── Public fire-and-forget API ────────────────────────────────────────────

    /** Create or refresh the note for [task]. */
    fun upsert(memory: MemoryManager, task: TaskModel) {
        launchSync(memory) { root -> writeNote(root, task) }
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
        launchSync(memory) { root ->
            val dir = tasksDir(root)
            for (task in tasks) {
                val src = File(dir, noteFileName(task) + ".md")
                if (!src.isFile) continue  // never synced or already moved
                val archiveDir = File(dir, ARCHIVE_DIR).apply { mkdirs() }
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

    // ── Internals ─────────────────────────────────────────────────────────────

    private fun launchSync(memory: MemoryManager, block: suspend (File) -> Unit) {
        scope.launch {
            try {
                val root = resolveVaultRoot(memory) ?: return@launch
                block(root)
            } catch (e: Exception) {
                ZaraLogger.e("$TAG sync failed: ${e.message}")
            }
        }
    }

    /**
     * Resolves the vault root to write into. Prefers the user-configured
     * [KEY_VAULT_PATH] when it's set and exists; otherwise falls back to an
     * app-private default directory so the mirror is never permanently
     * dormant. Returns null only when even the fallback is unavailable
     * (getExternalFilesDir() returning null — e.g. media unmounted).
     */
    private suspend fun resolveVaultRoot(memory: MemoryManager): File? {
        val configured = try {
            memory.get(KEY_VAULT_PATH)?.trim().orEmpty()
        } catch (e: Exception) {
            ZaraLogger.e("$TAG vault path read failed: ${e.message}")
            ""
        }
        if (configured.isNotEmpty()) {
            val dir = File(configured)
            if (dir.isDirectory) {
                warnedMissingVault = false
                return dir
            }
            if (!warnedMissingVault) {
                warnedMissingVault = true
                ZaraLogger.e("$TAG configured vault path missing: $configured — using default vault instead")
            }
        }
        val appRoot = memory.context.getExternalFilesDir(null) ?: return null
        return File(appRoot, DEFAULT_VAULT_DIRNAME).apply { mkdirs() }
    }

    private fun tasksDir(root: File): File = File(root, TASKS_DIR).apply { mkdirs() }

    private fun writeNote(root: File, task: TaskModel) {
        val file = File(tasksDir(root), noteFileName(task) + ".md")
        file.writeText(buildMarkdown(task))
        ZaraLogger.d("$TAG wrote ${file.name} state=${task.state}")
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
}
