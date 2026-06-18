package com.zara.assistant.media

import android.content.Context
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import com.zara.assistant.utils.ZaraLogger

/**
 * Layer 6.5F Phase 1 — MediaSession discovery.
 *
 * Locates the active Android MediaSession on demand (no polling, no background loops).
 * Uses MediaSessionManager to enumerate active sessions.
 * Requires android.permission.MEDIA_CONTENT_CONTROL or
 * an active NotificationListenerService to access sessions.
 *
 * Priority: currently PLAYING session > any active session > null.
 * Returns null if no active session found — caller must handle gracefully.
 *
 * Phase 1 only: no app-specific filtering, no Spotify logic.
 */
object MediaSessionLocator {

    private const val MEDIA_TASK_TYPE = "music"

    /**
     * Find the best active MediaController.
     * Called only when a media command is executed.
     * @return MediaController token package name + controller, or null.
     */
    fun findActiveSession(context: Context): ActiveMediaSession? {
        return try {
            val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE)
                as? MediaSessionManager ?: return null

            // Requires NotificationListenerService component name to be provided.
            // We pass the ZaraNotificationListener component.
            val listenerComponent = android.content.ComponentName(
                context,
                "com.zara.assistant.services.ZaraNotificationListener"
            )

            ZaraLogger.d("[MediaSessionLocator] listener=${listenerComponent.flattenToString()}")

            val controllers = try {
                msm.getActiveSessions(listenerComponent)
            } catch (se: SecurityException) {
                ZaraLogger.e("[MediaSessionLocator] No permission to list sessions: ${se.message}")
                return null
            }

            if (controllers.isEmpty()) {
                ZaraLogger.d("[MediaSessionLocator] No active sessions")
                return null
            }

            // Prefer a session that is actively PLAYING
            val playing = controllers.firstOrNull {
                it.playbackState?.state == PlaybackState.STATE_PLAYING
            }
            val best = playing ?: controllers.firstOrNull()

            best?.let {
                ZaraLogger.d("[MediaSessionLocator] Found session: ${it.packageName}")
                ActiveMediaSession(controller = it, packageName = it.packageName)
            }
        } catch (e: Exception) {
            ZaraLogger.e("[MediaSessionLocator] Error: ${e.message}")
            null
        }
    }
}

data class ActiveMediaSession(
    val controller: android.media.session.MediaController,
    val packageName: String
)
