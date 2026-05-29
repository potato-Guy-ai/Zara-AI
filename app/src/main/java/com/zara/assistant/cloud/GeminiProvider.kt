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
 * Gemini provider implementation.
 * Plugged in only when user opts into cloud reasoning.
 * API key stored securely (not in source).
 */
class GeminiProvider(private val apiKey: String) : AiProvider {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val endpoint =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"

    override suspend fun query(prompt: String): String = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("contents", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", org.json.JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        })
                    })
                })
            }.toString()

            val request = Request.Builder()
                .url("$endpoint?key=$apiKey")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val json = JSONObject(response.body?.string() ?: return@withContext "No response")
            json.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
        } catch (e: Exception) {
            ZaraLogger.e("Gemini error: ${e.message}")
            "Cloud reasoning unavailable."
        }
    }
}
