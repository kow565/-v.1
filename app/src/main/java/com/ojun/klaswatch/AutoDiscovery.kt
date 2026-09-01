package com.ojun.klaswatch

import org.jsoup.Jsoup
import java.net.URI
import java.security.MessageDigest

object AutoDiscovery {
    private data class Link(val text: String, val url: String)

    private val relevantWords = listOf(
        "공지", "알림", "과제", "시험", "퀴즈", "휴강", "보강", "준비물", "강의자료",
        "notice", "board", "announce", "assignment", "exam", "quiz", "task"
    )
    private val courseWords = listOf(
        "과목", "강의", "수업", "교과목", "강좌", "학습", "course", "lecture", "subject", "class"
    )

    fun discover(seedUrl: String, cookie: String): List<MonitorTarget> {
        if (seedUrl.isBlank()) return emptyList()
        val seed = fetch(seedUrl, cookie) ?: return emptyList()
        val firstLinks = links(seed.body(), seed.url().toString())

        val direct = firstLinks.filter { isRelevant(it) }
        val coursePages = firstLinks.filter { isCourseish(it) && !isRelevant(it) }.take(24)

        val nested = mutableListOf<Link>()
        coursePages.forEach { link ->
            val response = fetch(link.url, cookie) ?: return@forEach
            nested += links(response.body(), response.url().toString()).filter { isRelevant(it) }
        }

        val all = (direct + nested)
            .filter { sameKlasHost(it.url) }
            .distinctBy { normalize(it.url) }
            .take(70)

        val targets = all.map { link ->
            MonitorTarget(
                id = "auto_" + stableId(normalize(link.url)),
                name = "[자동] " + link.text.ifBlank { inferName(link.url) }.take(80),
                url = link.url,
                itemSelector = "tr, li, .list, .board, .notice, .row, .item",
                titleSelector = "a"
            )
        }.toMutableList()

        // KLAS 메인 화면에 최근 공지가 모여 있는 경우를 놓치지 않도록 seed도 fallback으로 감시.
        if (targets.none { normalize(it.url) == normalize(seed.url().toString()) }) {
            targets.add(
                0,
                MonitorTarget(
                    id = "auto_" + stableId(normalize(seed.url().toString())),
                    name = "[자동] KLAS 통합 화면",
                    url = seed.url().toString(),
                    itemSelector = "tr, li, .list, .board, .notice, .row, .item",
                    titleSelector = "a"
                )
            )
        }
        return targets.distinctBy { normalize(it.url) }.take(70)
    }

    private fun fetch(url: String, cookie: String): org.jsoup.Connection.Response? = runCatching {
        Jsoup.connect(url)
            .userAgent("Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/140 Mobile Safari/537.36")
            .header("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.7")
            .apply { if (cookie.isNotBlank()) header("Cookie", cookie) }
            .followRedirects(true)
            .ignoreContentType(true)
            .timeout(15000)
            .execute()
    }.getOrNull()

    private fun links(html: String, baseUrl: String): List<Link> {
        val doc = Jsoup.parse(html, baseUrl)
        return doc.select("a[href]").mapNotNull { a ->
            val text = a.text().replace(Regex("\\s+"), " ").trim()
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            if (href.isBlank() || href.startsWith("javascript:", true) || href == "#") return@mapNotNull null
            val absolute = runCatching { URI(baseUrl).resolve(href).toString() }.getOrNull() ?: return@mapNotNull null
            if (!absolute.startsWith("https://") || !sameKlasHost(absolute)) return@mapNotNull null
            Link(text, absolute)
        }.distinctBy { normalize(it.url) }
    }

    private fun isRelevant(link: Link): Boolean {
        val hay = (link.text + " " + link.url).lowercase()
        return relevantWords.any { hay.contains(it.lowercase()) }
    }

    private fun isCourseish(link: Link): Boolean {
        val hay = (link.text + " " + link.url).lowercase()
        return courseWords.any { hay.contains(it.lowercase()) }
    }

    private fun sameKlasHost(url: String): Boolean = runCatching {
        val host = URI(url).host?.lowercase().orEmpty()
        host == "klas.kw.ac.kr" || host.endsWith(".klas.kw.ac.kr")
    }.getOrDefault(false)

    private fun normalize(url: String): String = url.substringBefore('#').trimEnd('/')

    private fun inferName(url: String): String = when {
        url.contains("notice", true) || url.contains("board", true) -> "공지"
        url.contains("assign", true) || url.contains("task", true) -> "과제"
        url.contains("exam", true) || url.contains("quiz", true) -> "시험"
        else -> "KLAS 페이지"
    }

    private fun stableId(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.take(10).joinToString("") { "%02x".format(it) }
    }
}
