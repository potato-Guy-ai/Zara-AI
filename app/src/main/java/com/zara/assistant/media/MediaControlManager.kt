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
        ZaraLogger.d("[MediaControl] Trying Level 1 (MediaSession)")

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
                    ZaraLogger.d("[MediaControl] Level 1 failed -> fallback to Level 2")
                    dispatchMediaKey(context, action)
                }
            }
        }

        ZaraLogger.d("[MediaControl] Level 1 failed -> fallback to Level 2")
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
            ZaraLogger.d("[MediaControl] Dispatching Level 2 key event: $action")
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keycode))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keycode))
            val response = when (action) {
                MediaControlAction.PLAY     -> "Resuming."
                MediaControlAction.PAUSE    -> "Paused."
                MediaControlAction.STOP     -> "Stopped."
                MediaControlAction.NEXT     -> "Next track."
                MediaControlAction.PREVIOUS -> "Previous track."
            }
            ZaraLogger.d("[MediaControl] Level 2 success")
            response
        } catch (e: Exception) {
            ZaraLogger.e("[MediaControl] Level 2 failed: ${e.message}")
            "Couldn't control media playback."
        }
    }
}
