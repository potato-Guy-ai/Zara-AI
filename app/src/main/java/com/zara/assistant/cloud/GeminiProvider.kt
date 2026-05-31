package com.zara.assistant.cloud

import com.zara.assistant.utils.ZaraLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Gemini Flash provider.
 * Plugged in only when user opts into cloud reasoning.
 * API key is passed at construction — never stored in source or shared prefs.
 *
 * C6 fix: response body is now closed in a finally block on every code path,
 * preventing OkHttp connection pool exhaustion.
 */
class GeminiProvider(private val apiKey: String) : AiProvider {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val endpoint =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"

    override suspend fun query(prompt: String): String = withContext(Dispatchers.IO) {
        val requestBody = buildRequestBody(prompt)
        val request = Request.Builder()
            .url("$endpoint?key=$apiKey")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: Exception) {
            ZaraLogger.e("Gemini network error: ${e.message}")
            return@withContext "Cloud reasoning unavailable."
        }

        // C6: use() closes body on both success and exception paths
        return@withContext try {
            response.body?.use { body ->
                val raw = body.string()
                if (raw.isBlank()) return@withContext "No response from cloud."
                parseGeminiResponse(raw)
            } ?: "No response from cloud."
        } catch (e: Exception) {
            ZaraLogger.e("Gemini parse error: ${e.message}")
            "Cloud reasoning unavailable."
        } finally {
            // Defensive close — use() handles it, but this guards against future refactors
            if (!response.body.let { it == null || it.source().exhausted() }) {
                runCatching { response.close() }
            }
        }
    }

    private fun buildRequestBody(prompt: String): String =
        JSONObject().apply {
            put("contents", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", org.json.JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            })
        }.toString()

    private fun parseGeminiResponse(raw: String): String =
        JSONObject(raw)
            .getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
}
