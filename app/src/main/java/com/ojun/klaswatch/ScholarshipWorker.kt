package com.ojun.klaswatch

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScholarshipWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    private data class Source(val id: String, val name: String, val url: String)

    private val sources = listOf(
        Source(
            "kw",
            "광운대학교 장학 공지",
            "https://www.kw.ac.kr/ko/life/notice.jsp?BoardMode=list&srCategoryId=4&tpage=1"
        ),
        Source(
            "kosaf",
            "한국장학재단",
            "https://www.kosaf.go.kr/ko/main.do"
        )
    )

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        var okCount = 0
        var newCount = 0
        val errors = mutableListOf<String>()

        sources.forEach { source ->
            try {
                val response = Jsoup.connect(source.url)
                    .userAgent("Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/140 Mobile Safari/537.36 KLASWatch/0.5")
                    .header("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.7")
                    .followRedirects(true)
                    .timeout(20_000)
                    .execute()

                val doc = response.parse()
                val items = doc.select("a[href]")
                    .mapNotNull { a ->
                        val title = a.text().replace(Regex("\\s+"), " ").trim()
                        if (!isRelevant(title)) return@mapNotNull null
                        val href = a.absUrl("href").ifBlank {
                            runCatching { URI(response.url().toString()).resolve(a.attr("href")).toString() }.getOrDefault("")
                        }
                        if (href.isBlank()) return@mapNotNull null
                        val id = (source.id + "|" + href + "|" + title).hashCode().toString()
                        NoticeItem(
                            id = id,
                            title = title,
                            url = href,
                            dateText = "",
                            category = "장학금",
                            snippet = ""
                        )
                    }
                    .distinctBy { it.url + "|" + it.title }
                    .take(80)

                if (items.isEmpty()) {
                    errors += "${source.name}: 공고 0개"
                    return@forEach
                }

                okCount++
                val targetId = "scholarship_${source.id}"
                val seen = AppPrefs.seen(applicationContext, targetId)

                if (!AppPrefs.hasBaseline(applicationContext, targetId)) {
                    AppPrefs.saveSeen(applicationContext, targetId, items.map { it.id })
                    AppPrefs.setBaseline(applicationContext, targetId)
                    return@forEach
                }

                val fresh = items.filterNot { seen.contains(it.id) }
                val target = MonitorTarget(targetId, source.name, source.url)
                fresh.reversed().forEach { item ->
                    val detail = runCatching {
                        Jsoup.connect(item.url)
                            .userAgent("Mozilla/5.0 (Linux; Android 16) KLASWatch/0.5")
                            .timeout(15_000)
                            .get()
                            .text()
                            .take(12_000)
                    }.getOrDefault("")

                    NotificationHelper.notice(applicationContext, target, item)
                    if (AppPrefs.webhook(applicationContext).isNotBlank()) {
                        RelayClient.send(
                            AppPrefs.webhook(applicationContext),
                            AppPrefs.token(applicationContext),
                            target,
                            item,
                            detail
                        )
                    }
                    seen += item.id
                    newCount++
                }
                AppPrefs.saveSeen(applicationContext, targetId, seen)
            } catch (e: Exception) {
                errors += "${source.name}: ${e.javaClass.simpleName}"
            }
        }

        val now = SimpleDateFormat("MM/dd HH:mm", Locale.KOREA).format(Date())
        val text = buildString {
            append("$now 장학금 검사 · 정상 $okCount/${sources.size} · 새 공고 ${newCount}건")
            if (errors.isNotEmpty()) append(" · " + errors.take(2).joinToString(" / "))
        }
        AppPrefs.setScholarshipStatus(applicationContext, text)

        if (okCount == 0) Result.retry() else Result.success()
    }

    private fun isRelevant(title: String): Boolean {
        if (title.length < 4) return false
        val t = title.lowercase()
        val include = listOf(
            "장학", "장학생", "국가근로", "근로장학", "주거안정",
            "희망사다리", "푸른등대", "학자금", "생활비", "재단"
        )
        val exclude = listOf(
            "지급 예정", "지급(예정)", "지급 안내", "선발결과", "등록금 납부",
            "등록금 고지", "대출 상환", "부정수급"
        )
        return include.any { t.contains(it) } && exclude.none { t.contains(it) }
    }
}
