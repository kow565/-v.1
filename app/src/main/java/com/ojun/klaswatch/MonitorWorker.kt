package com.ojun.klaswatch

import android.content.Context
import android.webkit.CookieManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Connection
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MonitorWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val targets = AppPrefs.targets(applicationContext)
        if (targets.isEmpty()) {
            AppPrefs.setLastStatus(applicationContext, "감시 대상이 없습니다. 앱에서 KLAS 공지 페이지를 추가하세요.")
            return@withContext Result.success()
        }

        var newCount = 0
        var okCount = 0
        val errors = mutableListOf<String>()
        targets.forEach { target ->
            try {
                val listResponse = fetch(target.url)
                val finalUrl = listResponse.url().toString()
                val html = listResponse.body()
                if (looksLoggedOut(finalUrl, html)) {
                    NotificationHelper.loginExpired(applicationContext, target)
                    errors += "${target.name}: 로그인 만료"
                    return@forEach
                }

                val items = NoticeParser.parseList(html, target.url, target)
                if (items.isEmpty()) {
                    errors += "${target.name}: 공지 항목 0개(페이지 구조 확인 필요)"
                    return@forEach
                }
                okCount++
                val seen = AppPrefs.seen(applicationContext, target.id)

                if (!AppPrefs.hasBaseline(applicationContext, target.id)) {
                    AppPrefs.saveSeen(applicationContext, target.id, items.map { it.id })
                    AppPrefs.setBaseline(applicationContext, target.id)
                    return@forEach
                }

                val fresh = items.filterNot { seen.contains(it.id) }
                fresh.reversed().forEach { item ->
                    val detail = runCatching { NoticeParser.extractDetail(fetch(item.url).body(), item.url) }.getOrDefault("")
                    NotificationHelper.notice(applicationContext, target, item)
                    RelayClient.send(AppPrefs.webhook(applicationContext), AppPrefs.token(applicationContext), target, item, detail)
                    seen += item.id
                    newCount++
                }
                AppPrefs.saveSeen(applicationContext, target.id, seen)
            } catch (e: Exception) {
                errors += "${target.name}: ${e.javaClass.simpleName}"
            }
        }

        val now = SimpleDateFormat("MM/dd HH:mm", Locale.KOREA).format(Date())
        val status = buildString {
            append("$now 검사 완료 · 정상 $okCount/${targets.size} · 새 공지 ${newCount}건")
            if (errors.isNotEmpty()) append("\n" + errors.joinToString(" / "))
        }
        AppPrefs.setLastStatus(applicationContext, status)
        if (okCount == 0 && errors.isNotEmpty()) Result.retry() else Result.success()
    }

    private fun fetch(url: String): Connection.Response {
        val cookie = CookieManager.getInstance().getCookie(url).orEmpty()
        return Jsoup.connect(url)
            .userAgent("Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/140 Mobile Safari/537.36")
            .header("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.7")
            .apply { if (cookie.isNotBlank()) header("Cookie", cookie) }
            .followRedirects(true)
            .ignoreContentType(true)
            .timeout(20000)
            .execute()
    }

    private fun looksLoggedOut(url: String, html: String): Boolean {
        val u = url.lowercase()
        val h = html.lowercase()
        return u.contains("loginform") || u.contains("/login") ||
            (h.contains("id(학번 또는 사번)") && h.contains("password")) ||
            (h.contains("비밀번호 최초 등록") && h.contains("로그인"))
    }
}
