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
        val seedUrl = AppPrefs.autoSeedUrl(applicationContext)
        var targets = AppPrefs.targets(applicationContext)

        if (seedUrl.isNotBlank()) {
            val nowMs = System.currentTimeMillis()
            val hasAuto = targets.any { it.name.startsWith("[자동]") }
            val discoveryOld = nowMs - AppPrefs.lastDiscovery(applicationContext) > 12L * 60L * 60L * 1000L
            if (!hasAuto || discoveryOld) {
                val cookie = CookieManager.getInstance().getCookie(seedUrl).orEmpty()
                val discovered = AutoDiscovery.discover(seedUrl, cookie)
                if (discovered.isNotEmpty()) {
                    AppPrefs.replaceAutoTargets(applicationContext, discovered)
                    AppPrefs.markDiscovery(applicationContext)
                    targets = AppPrefs.targets(applicationContext)
                }
            }
        }

        if (targets.isEmpty()) {
            AppPrefs.setLastStatus(
                applicationContext,
                if (seedUrl.isBlank()) "KLAS 로그인 대기 중입니다. 앱에서 한 번 로그인하세요."
                else "로그인은 확인됐지만 자동 감시 페이지를 아직 찾지 못했습니다. 잠시 뒤 다시 시도합니다."
            )
            return@withContext Result.retry()
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
                    errors += "${target.name}: 항목 0개"
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
        val autoCount = targets.count { it.name.startsWith("[자동]") }
        val status = buildString {
            append("$now 자동 검사 완료 · 감시 ${autoCount}개 · 정상 $okCount/${targets.size} · 새 공지 ${newCount}건")
            if (errors.isNotEmpty()) append("\n" + errors.take(5).joinToString(" / "))
        }
        AppPrefs.setLastStatus(applicationContext, status)
        if (okCount == 0 && errors.isNotEmpty()) Result.retry() else Result.success()
    }

    private fun fetch(url: String): Connection.Response {
        val cookieManager = CookieManager.getInstance()
        val cookie = cookieManager.getCookie(url).orEmpty()
        val response = Jsoup.connect(url)
            .userAgent("Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/140 Mobile Safari/537.36 KLASWatch/0.5")
            .header("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.7")
            .apply { if (cookie.isNotBlank()) header("Cookie", cookie) }
            .followRedirects(true)
            .ignoreContentType(true)
            .timeout(20000)
            .execute()

        if (response.cookies().isNotEmpty()) {
            val finalUrl = response.url().toString()
            response.cookies().forEach { (name, value) ->
                cookieManager.setCookie(finalUrl, "$name=$value; Path=/; Secure")
            }
            cookieManager.flush()
        }
        return response
    }

    private fun looksLoggedOut(url: String, html: String): Boolean {
        val u = url.lowercase()
        val h = html.lowercase()
        return u.contains("loginform") || u.contains("/login") ||
            (h.contains("id(학번 또는 사번)") && h.contains("password")) ||
            (h.contains("비밀번호 최초 등록") && h.contains("로그인"))
    }
}
