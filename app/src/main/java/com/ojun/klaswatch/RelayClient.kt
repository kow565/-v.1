package com.ojun.klaswatch

import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

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
        return postJson(webhook, payload)
    }

    fun sendTest(webhook: String, token: String): Boolean {
        val payload = JSONObject().apply {
            put("token", token)
            put("target", "KLAS Watch")
            put("category", "TEST")
            put("title", "[KLAS Watch] Gmail 중계 테스트")
            put("dateText", "")
            put("url", "https://klas.kw.ac.kr/")
            put("detail", "이 메일이 도착하면 Android 앱 → Gmail → ChatGPT 경로의 Gmail 중계 구간이 정상입니다.")
            put("detectedAt", System.currentTimeMillis())
        }
        return postJson(webhook, payload)
    }

    private fun postJson(endpoint: String, payload: JSONObject): Boolean {
        val conn = URL(endpoint).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
        val code = conn.responseCode
        val response = runCatching { conn.inputStream.bufferedReader().use { it.readText() } }.getOrDefault("")
        conn.disconnect()
        return code in 200..299 && !response.contains("\"ok\":false")
    }
}
