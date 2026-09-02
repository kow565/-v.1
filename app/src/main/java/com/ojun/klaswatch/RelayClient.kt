package com.ojun.klaswatch

import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class RelayResult(
    val ok: Boolean,
    val httpStatus: Int?,
    val serverMessage: String,
) {
    fun displayMessage(): String {
        val http = httpStatus?.let { "HTTP $it" } ?: "HTTP 응답 없음"
        return "$http · $serverMessage"
    }
}

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
        if (webhook.isBlank()) return RelayResult(false, null, "중계 주소가 비어 있습니다.")
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
                RelayResult(false, code, response.take(240).ifBlank { "서버 오류 본문이 비어 있습니다." })
            } else {
                val parsed = runCatching { JSONObject(response) }.getOrNull()
                if (parsed == null) {
                    RelayResult(false, code, "JSON 응답이 아닙니다: ${response.take(200).ifBlank { "빈 응답" }}")
                } else if (parsed.optBoolean("ok", false)) {
                    RelayResult(true, code, parsed.optString("message", "중계 서버가 메일을 전송했습니다."))
                } else {
                    val error = parsed.optString("error").ifBlank { "중계 서버가 실패를 반환했습니다." }
                    val errorCode = parsed.optString("code")
                    RelayResult(false, code, listOf(errorCode, error).filter { it.isNotBlank() }.joinToString(": "))
                }
            }
        } catch (e: Exception) {
            RelayResult(false, null, "${e.javaClass.simpleName}: ${e.message.orEmpty()}".take(240))
        }
    }
}

