package com.ojun.klaswatch

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object AppPrefs {
    private const val PREFS = "klas_watch_prefs"
    private const val TARGETS = "targets"
    private const val WEBHOOK = "webhook"
    private const val TOKEN = "token"
    private const val INTERVAL = "interval_minutes"
    private const val LAST_STATUS = "last_status"
    private const val LAST_RELAY_STATUS = "last_relay_status"
    private const val AUTO_SEED_URL = "auto_seed_url"
    private const val LAST_DISCOVERY = "last_discovery"
    private const val DEFAULT_RELAY_TOKEN = "a5a7pKzeZnUnxC5P-UlMYJOLbNZfbjXR4ppviwgU59s"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun webhook(context: Context): String = prefs(context).getString(WEBHOOK, "") ?: ""
    fun token(context: Context): String {
        val stored = prefs(context).getString(TOKEN, null)
        return if (stored.isNullOrBlank()) DEFAULT_RELAY_TOKEN else stored
    }
    fun intervalMinutes(context: Context): Long = prefs(context).getLong(INTERVAL, 15L).coerceAtLeast(15L)
    fun lastStatus(context: Context): String = prefs(context).getString(LAST_STATUS, "아직 검사 기록이 없습니다.") ?: ""
    fun lastRelayStatus(context: Context): String = prefs(context).getString(LAST_RELAY_STATUS, "이메일 중계 미설정") ?: ""
    fun autoSeedUrl(context: Context): String = prefs(context).getString(AUTO_SEED_URL, "") ?: ""
    fun lastDiscovery(context: Context): Long = prefs(context).getLong(LAST_DISCOVERY, 0L)

    fun saveSettings(context: Context, webhook: String, token: String, interval: Long) {
        prefs(context).edit()
            .putString(WEBHOOK, webhook.trim())
            .putString(TOKEN, token.trim())
            .putLong(INTERVAL, interval.coerceAtLeast(15L))
            .apply()
    }

    fun saveWebhook(context: Context, webhook: String) {
        prefs(context).edit().putString(WEBHOOK, webhook.trim()).apply()
    }

    fun setLastRelayStatus(context: Context, text: String) {
        prefs(context).edit().putString(LAST_RELAY_STATUS, text).apply()
    }

    fun setAutoSeedUrl(context: Context, url: String) {
        if (url.isBlank()) return
        prefs(context).edit().putString(AUTO_SEED_URL, url).apply()
    }

    fun markDiscovery(context: Context) {
        prefs(context).edit().putLong(LAST_DISCOVERY, System.currentTimeMillis()).apply()
    }

    fun requestRediscovery(context: Context) {
        prefs(context).edit().putLong(LAST_DISCOVERY, 0L).apply()
    }

    fun setLastStatus(context: Context, text: String) {
        prefs(context).edit().putString(LAST_STATUS, text).apply()
    }

    fun targets(context: Context): MutableList<MonitorTarget> {
        val raw = prefs(context).getString(TARGETS, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val list = mutableListOf<MonitorTarget>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list += MonitorTarget(
                id = o.getString("id"),
                name = o.getString("name"),
                url = o.getString("url"),
                itemSelector = o.optString("itemSelector", "tr, li"),
                titleSelector = o.optString("titleSelector", "a")
            )
        }
        return list
    }

    fun addTarget(context: Context, name: String, url: String): MonitorTarget {
        val target = MonitorTarget(UUID.randomUUID().toString(), name.ifBlank { "KLAS 공지" }, url)
        val list = targets(context)
        if (list.none { it.url == target.url }) {
            list += target
            saveTargets(context, list)
        }
        return target
    }

    fun replaceAutoTargets(context: Context, discovered: List<MonitorTarget>) {
        val manual = targets(context).filterNot { it.name.startsWith("[자동]") }
        val merged = (manual + discovered)
            .distinctBy { it.url }
            .take(80)
        saveTargets(context, merged)
    }

    fun removeTarget(context: Context, id: String) {
        saveTargets(context, targets(context).filterNot { it.id == id })
        prefs(context).edit().remove("seen_$id").remove("baseline_$id").apply()
    }

    private fun saveTargets(context: Context, targets: List<MonitorTarget>) {
        val arr = JSONArray()
        targets.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id)
                put("name", it.name)
                put("url", it.url)
                put("itemSelector", it.itemSelector)
                put("titleSelector", it.titleSelector)
            })
        }
        prefs(context).edit().putString(TARGETS, arr.toString()).apply()
    }

    fun seen(context: Context, targetId: String): MutableSet<String> {
        val stored = prefs(context).getStringSet("seen_$targetId", emptySet()) ?: emptySet()
        return stored.toMutableSet()
    }

    fun saveSeen(context: Context, targetId: String, ids: Collection<String>) {
        prefs(context).edit().putStringSet("seen_$targetId", ids.toList().takeLast(500).toSet()).apply()
    }

    fun hasBaseline(context: Context, targetId: String): Boolean = prefs(context).getBoolean("baseline_$targetId", false)
    fun setBaseline(context: Context, targetId: String) = prefs(context).edit().putBoolean("baseline_$targetId", true).apply()
}
