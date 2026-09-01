package com.ojun.klaswatch

data class MonitorTarget(
    val id: String,
    val name: String,
    val url: String,
    val itemSelector: String = "tr, li",
    val titleSelector: String = "a"
)

data class NoticeItem(
    val id: String,
    val title: String,
    val url: String,
    val dateText: String,
    val category: String,
    val snippet: String = ""
)
