package com.ojun.klaswatch

import android.content.Context
import android.webkit.CookieManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Connection
import org.jsoup.Jsoup

class KeepAliveWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val seedUrl = AppPrefs.autoSeedUrl(applicationContext)
        if (seedUrl.isBlank()) return@withContext Result.success()

        try {
            val cookieManager = CookieManager.getInstance()
            val cookie = cookieManager.getCookie(seedUrl).orEmpty()
            if (cookie.isBlank()) {
                notifyLoginRequired()
                return@withContext Result.success()
            }

            val response = Jsoup.connect(seedUrl)
                .userAgent("Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/140 Mobile Safari/537.36 KLASWatch/0.5")
                .header("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.7")
                .header("Cookie", cookie)
                .followRedirects(true)
                .ignoreContentType(true)
                .timeout(20_000)
                .execute()

            syncCookies(response)

            if (looksLoggedOut(response.url().toString(), response.body())) {
                notifyLoginRequired()
            } else {
                AppPrefs.markKeepAlive(applicationContext)
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun syncCookies(response: Connection.Response) {
        if (response.cookies().isEmpty()) return
        val cm = CookieManager.getInstance()
        val url = response.url().toString()
        response.cookies().forEach { (name, value) ->
            cm.setCookie(url, "$name=$value; Path=/; Secure")
        }
        cm.flush()
    }

    private fun notifyLoginRequired() {
        val target = MonitorTarget("session", "KLAS", AppPrefs.autoSeedUrl(applicationContext))
        NotificationHelper.loginExpired(applicationContext, target)
        AppPrefs.setLastStatus(applicationContext, "KLAS 로그인 세션이 만료되었습니다. 앱을 열어 다시 로그인하세요.")
    }

    private fun looksLoggedOut(url: String, html: String): Boolean {
        val u = url.lowercase()
        val h = html.lowercase()
        return u.contains("loginform") || u.contains("/login") ||
            (h.contains("id(학번 또는 사번)") && h.contains("password")) ||
            (h.contains("비밀번호 최초 등록") && h.contains("로그인"))
    }
}
