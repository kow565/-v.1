package com.ojun.klaswatch

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI
import java.security.MessageDigest

object NoticeParser {
    private val dateRegex = Regex("(20\\d{2}[./-]\\s?\\d{1,2}[./-]\\s?\\d{1,2}|\\d{1,2}[./-]\\d{1,2})")
    private val skipTexts = setOf("로그인", "로그아웃", "홈", "HOME", "메뉴", "전체메뉴", "이전", "다음", "목록", "더보기")

    fun parseList(html: String, baseUrl: String, target: MonitorTarget): List<NoticeItem> {
        val doc = Jsoup.parse(html, baseUrl)
        doc.select("script, style, nav, header, footer, noscript").remove()
        val candidates = linkedMapOf<String, NoticeItem>()

        fun acceptAnchor(anchor: Element, container: Element) {
            val title = anchor.text().replace(Regex("\\s+"), " ").trim()
            if (title.length !in 3..180 || skipTexts.any { title.equals(it, true) }) return
            val href = anchor.absUrl("href").ifBlank { anchor.attr("href") }
            if (href.isBlank() || href.startsWith("javascript:", true) || href == "#") return
            val absolute = try { URI(baseUrl).resolve(href).toString() } catch (_: Exception) { return }
            if (!absolute.startsWith("https://")) return

            val rowText = container.text().replace(Regex("\\s+"), " ").trim()
            val date = dateRegex.find(rowText)?.value ?: ""
            val classHint = (container.className() + " " + container.id()).lowercase()
            val looksLikeRow = container.tagName() == "tr" || container.tagName() == "li" ||
                classHint.contains("notice") || classHint.contains("board") || classHint.contains("list") ||
                classHint.contains("row") || classHint.contains("lecture") || classHint.contains("learning") ||
                classHint.contains("content") || classHint.contains("week") || classHint.contains("item")
            if (!looksLikeRow && date.isBlank()) return

            val id = stableId(absolute.ifBlank { "$title|$date" })
            candidates.putIfAbsent(id, NoticeItem(id, title, absolute, date, categorize(title + " " + rowText)))
        }

        val containers = doc.select(target.itemSelector)
        containers.forEach { c -> c.select(target.titleSelector).firstOrNull()?.let { acceptAnchor(it, c) } }

        if (candidates.isEmpty()) {
            doc.select("a[href]").forEach { a -> acceptAnchor(a, a.parent() ?: a) }
        }

        return candidates.values.take(150)
    }

    fun extractDetail(html: String, baseUrl: String): String {
        val doc = Jsoup.parse(html, baseUrl)
        doc.select("script, style, nav, header, footer, noscript, form").remove()
        val selectors = listOf(
            "article", "main", ".board_view", ".board-view", ".view_content", ".view-content",
            ".notice_view", ".notice-view", ".content", ".contents", ".lecture", ".learning",
            ".week", ".week-item", "#content", "#contents"
        )
        val blocks = selectors.flatMap { doc.select(it) }.distinct()
        val best = blocks.maxByOrNull { it.text().length }
        val text = (best?.text() ?: doc.body().text()).replace(Regex("\\s+"), " ").trim()
        return text.take(5000)
    }

    private fun categorize(text: String): String {
        val t = text.lowercase()
        return when {
            listOf("휴강", "보강", "강의 취소").any { t.contains(it) } -> "휴강/보강"
            listOf("시험", "중간고사", "기말고사", "퀴즈", "고사").any { t.contains(it) } -> "시험"
            listOf("과제", "제출", "레포트", "보고서", "assignment").any { t.contains(it) } -> "과제"
            listOf("강의자료", "수업자료", "학습자료", "온라인강의", "강의영상", "강의동영상",
                "녹화강의", "녹화영상", "학습콘텐츠", "강의콘텐츠", "수업콘텐츠", "동영상",
                "영상강의", "차시", "주차학습", "학습하기", "lecture", "video", "content", "material"
            ).any { t.contains(it) } -> "강의"
            listOf("준비물", "교재", "지참").any { t.contains(it) } -> "준비물"
            listOf("마감", "기한", "deadline").any { t.contains(it) } -> "마감"
            else -> "공지"
        }
    }

    private fun stableId(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }
}
