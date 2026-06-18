package com.zara.assistant.media

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import com.zara.assistant.execution.ActiveTask
import com.zara.assistant.execution.TaskRegistry
import com.zara.assistant.utils.ZaraLogger

/**
 * Layer 6.5F — Media Control Manager.
 *
 * Level 1: MediaSession transport controls via MediaSessionLocator.
 * Level 2: AudioManager media key dispatch (fallback if Level 1 fails).
 *
 * Execution order: Level 1 → Level 2 on failure.
 * User-facing strings are identical regardless of which level executes.
 * No polling. No threads. No timers. No services.
 */
object MediaControlManager {

    fun execute(context: Context, action: MediaControlAction): String {
        ZaraLogger.d("[MediaControl] contextClass=${context.javaClass.name}")
        ZaraLogger.d("[MediaControl] package=${context.packageName}")

        // Level 1: MediaSession
        val session = MediaSessionLocator.findActiveSession(context)
        if (session != null) {
            val controls = session.controller.transportControls
            if (controls != null) {
                return try {
                    val response = when (action) {
                        MediaControlAction.PLAY     -> { controls.play();           "Resuming." }
                        MediaControlAction.PAUSE    -> { controls.pause();          "Paused." }
                        MediaControlAction.STOP     -> { controls.stop();           "Stopped." }
                        MediaControlAction.NEXT     -> { controls.skipToNext();     "Next track." }
                        MediaControlAction.PREVIOUS -> { controls.skipToPrevious(); "Previous track." }
                    }
                    TaskRegistry.register(
                        ActiveTask(
                            type        = "music",
                            label       = session.packageName,
                            packageName = session.packageName
                        )
                    )
                    ZaraLogger.d("[MediaControlManager] L1 $action on ${session.packageName}")
                    response
                } catch (e: Exception) {
                    ZaraLogger.e("[MediaControlManager] L1 failed: ${e.message} — falling back to L2")
                    dispatchMediaKey(context, action)
                }
            }
        }

        // Level 2: AudioManager key dispatch
        ZaraLogger.d("[MediaControlManager] L1 unavailable — falling back to L2")
        return dispatchMediaKey(context, action)
    }

    private fun dispatchMediaKey(context: Context, action: MediaControlAction): String {
        val keycode = when (action) {
            MediaControlAction.PLAY     -> KeyEvent.KEYCODE_MEDIA_PLAY
            MediaControlAction.PAUSE    -> KeyEvent.KEYCODE_MEDIA_PAUSE
            MediaControlAction.STOP     -> KeyEvent.KEYCODE_MEDIA_STOP
            MediaControlAction.NEXT     -> KeyEvent.KEYCODE_MEDIA_NEXT
            MediaControlAction.PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
        }
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return try {
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keycode))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keycode))
            val response = when (action) {
                MediaControlAction.PLAY     -> "Resuming."
                MediaControlAction.PAUSE    -> "Paused."
                MediaControlAction.STOP     -> "Stopped."
                MediaControlAction.NEXT     -> "Next track."
                MediaControlAction.PREVIOUS -> "Previous track."
            }
            ZaraLogger.d("[MediaControlManager] L2 $action dispatched")
            response
        } catch (e: Exception) {
            ZaraLogger.e("[MediaControlManager] L2 failed: ${e.message}")
            "Couldn't control media playback."
        }
    }
}
