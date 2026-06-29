package com.zara.assistant.playback

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.zara.assistant.utils.ZaraLogger

/**
 * Layer 6.6B Phase 2B — FreePlaybackEngine.
 *
 * Assisted launch engine for FREE_ASSISTED route only. Opens Spotify,
 * injects a search deeplink, brings it to foreground. No autoplay.
 * No accessibility. No OCR. No gesture automation. No playback control —
 * that remains MediaControlManager's responsibility (Layer 6.5F).
 *
 * No threads. No timers. No polling. No services. No coroutines.
 */
object FreePlaybackEngine {

    private const val SPOTIFY_PACKAGE = "com.spotify.music"

    /**
     * Runs the assisted-launch flow for a FREE_ASSISTED plan.
     * Ignores any plan whose route is not FREE_ASSISTED.
     */
    fun run(context: Context, plan: PlaybackExecutionPlan): FreePlaybackResult {
        if (plan.route != PlaybackRoute.FREE_ASSISTED) {
            return FreePlaybackResult(
                type = FreePlaybackResultType.FAILED,
                state = FreePlaybackState.IDLE,
                message = "Not a FREE_ASSISTED plan."
            )
        }

        // Step 1: validate Spotify is installed.
        if (!isSpotifyInstalled(context)) {
            return FreePlaybackResult(
                type = FreePlaybackResultType.FAILED,
                state = FreePlaybackState.FAILED,
                message = "Spotify not installed."
            )
        }

        // Step 2: generate search deeplink via existing DeepLinkManager.
        val target = PlaybackTarget(
            query = plan.resolvedQuery,
            type = executionTypeToPlaybackType(plan.executionType)
        )
        val deeplink = DeepLinkManager.generate(target)

        // Step 3: launch deeplink, fallback to generic app open on failure.
        if (deeplink != null) {
            val launched = launchUri(context, deeplink)
            if (launched) {
                return FreePlaybackResult(
                    type = FreePlaybackResultType.SUCCESS,
                    state = FreePlaybackState.READY,
                    message = "Spotify opened at correct target."
                )
            }
        }

        // Fallback: generic Spotify launch (package only, no search).
        val fallbackLaunched = launchPackage(context, SPOTIFY_PACKAGE)
        return if (fallbackLaunched) {
            FreePlaybackResult(
                type = FreePlaybackResultType.FALLBACK_USED,
                state = FreePlaybackState.READY,
                message = "Opened Spotify."
            )
        } else {
            FreePlaybackResult(
                type = FreePlaybackResultType.FAILED,
                state = FreePlaybackState.FAILED,
                message = "Couldn't open Spotify."
            )
        }
    }

    private fun isSpotifyInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(SPOTIFY_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        } catch (e: Exception) {
            ZaraLogger.e("[FreePlaybackEngine] package check failed: ${e.message}")
            false
        }
    }

    private fun launchUri(context: Context, uri: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                setPackage(SPOTIFY_PACKAGE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            ZaraLogger.e("[FreePlaybackEngine] deeplink launch failed: ${e.message}")
            false
        }
    }

    private fun launchPackage(context: Context, pkg: String): Boolean {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                ?: return false
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            true
        } catch (e: Exception) {
            ZaraLogger.e("[FreePlaybackEngine] package launch failed: ${e.message}")
            false
        }
    }

    private fun executionTypeToPlaybackType(type: PlaybackExecutionType): PlaybackType = when (type) {
        PlaybackExecutionType.PLAY_SONG -> PlaybackType.SONG
        PlaybackExecutionType.PLAY_PLAYLIST -> PlaybackType.PLAYLIST
        PlaybackExecutionType.PLAY_LIKED -> PlaybackType.LIKED
        PlaybackExecutionType.PLAY_RECOMMENDATION -> PlaybackType.RECOMMENDATION
        PlaybackExecutionType.SEARCH_ONLY -> PlaybackType.UNKNOWN
    }
}
