package com.zara.assistant.media

import android.content.Context
import android.view.KeyEvent
import com.zara.assistant.execution.ActiveTask
import com.zara.assistant.execution.TaskRegistry
import com.zara.assistant.utils.ZaraLogger

/**
 * Layer 6.5F Phase 1 — Media Control Manager.
 *
 * Executes media transport commands via Android MediaSession.
 * Uses MediaSessionLocator to find the active session on demand.
 * Updates TaskRegistry after successful interaction.
 *
 * Safety: returns "No active media is playing." when no session found.
 * No polling. No timers. No background work. No app launch. No search.
 *
 * Phase 2 will add: Spotify deep links, accessibility fallback,
 * app-specific handlers, song/artist matching.
 */
object MediaControlManager {

    /**
     * Execute a MediaControlAction.
     * Finds active session, dispatches transport control, updates TaskRegistry.
     * @return User-facing response string.
     */
    fun execute(context: Context, action: MediaControlAction): String {
        ZaraLogger.d("[MediaControl] contextClass=${context.javaClass.name}")
        ZaraLogger.d("[MediaControl] package=${context.packageName}")

        val session = MediaSessionLocator.findActiveSession(context)
            ?: return "No active media is playing."

        val controls = session.controller.transportControls
            ?: return "No active media is playing."

        return try {
            when (action) {
                MediaControlAction.PLAY     -> { controls.play();               "Resuming." }
                MediaControlAction.PAUSE    -> { controls.pause();              "Paused." }
                MediaControlAction.STOP     -> { controls.stop();               "Stopped." }
                MediaControlAction.NEXT     -> { controls.skipToNext();         "Next track." }
                MediaControlAction.PREVIOUS -> { controls.skipToPrevious();     "Previous track." }
            }.also {
                // Update TaskRegistry with the session that responded
                TaskRegistry.register(
                    ActiveTask(
                        type        = "music",
                        label       = session.packageName,
                        packageName = session.packageName
                    )
                )
                ZaraLogger.d("[MediaControlManager] $action on ${session.packageName}")
            }
        } catch (e: Exception) {
            ZaraLogger.e("[MediaControlManager] Error executing $action: ${e.message}")
            "Couldn't control media playback."
        }
    }
}
