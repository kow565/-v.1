package com.ojun.klaswatch

import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class RelayResult(val ok: Boolean, val message: String)

object RelayClient {
    fun send(webhook: String, token: String, target: MonitorTarget, notice: NoticeItem, detail: String): Boolean {
        if (webhook.isBlank()) return false
        val payload = JSONObject().apply {
            put("token", token)
            put("target", target.name)
            put("category", notice.category)
            put("title", notice.title)
            put("dateText", notice.dateText)
            put("url", notice.url)
            put("detail", detail)
            put("detectedAt", System.currentTimeMillis())
        }
        return postJsonDetailed(webhook, payload).ok
    }

    fun sendTest(webhook: String, token: String): RelayResult {
        if (webhook.isBlank()) return RelayResult(false, "중계 주소가 비어 있습니다.")
        val payload = JSONObject().apply {
            put("token", token)
            put("target", "KLAS Watch")
            put("category", "TEST")
            put("title", "Gmail 중계 테스트")
            put("dateText", "")
            put("url", "https://klas.kw.ac.kr/")
            put("detail", "이 메일이 도착하면 KLAS Watch → Gmail 중계가 정상입니다.")
            put("detectedAt", System.currentTimeMillis())
        }
        return postJsonDetailed(webhook, payload)
    }

    private fun postJsonDetailed(endpoint: String, payload: JSONObject): RelayResult {
        return try {
            val conn = URL(endpoint).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val response = runCatching { stream?.bufferedReader()?.use { it.readText() }.orEmpty() }.getOrDefault("")
            conn.disconnect()

            if (code !in 200..299) {
                RelayResult(false, "HTTP $code ${response.take(160)}".trim())
            } else {
                val parsed = runCatching { JSONObject(response) }.getOrNull()
                val ok = parsed?.optBoolean("ok", false) ?: !response.contains("\"ok\":false")
                if (ok) RelayResult(true, "중계 서버가 테스트 메일을 전송했습니다.")
                else RelayResult(false, parsed?.optString("error")?.takeIf { it.isNotBlank() } ?: response.take(160).ifBlank { "중계 서버가 실패를 반환했습니다." })
            }
        } catch (e: Exception) {
            RelayResult(false, "${e.javaClass.simpleName}: ${e.message.orEmpty()}".take(180))
        }
    }
}
