package com.ojun.klaswatch

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {
    private const val CHANNEL = "klas_notices"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "KLAS 새 공지", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "KLAS에서 새 공지나 로그인 만료를 발견하면 알려줍니다."
            }
        )
    }

    fun notice(context: Context, target: MonitorTarget, item: NoticeItem) {
        ensureChannel(context)
        if (android.os.Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(item.url))
        val pi = PendingIntent.getActivity(context, item.id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("[${item.category}] ${target.name}")
            .setContentText(item.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(item.title))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        NotificationManagerCompat.from(context).notify(item.id.hashCode(), n)
    }

    fun loginExpired(context: Context, target: MonitorTarget) {
        ensureChannel(context)
        if (android.os.Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val intent = Intent(context, MainActivity::class.java)
        val pi = PendingIntent.getActivity(context, 101, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("KLAS 로그인이 필요합니다")
            .setContentText("${target.name} 감시 세션이 만료된 것 같습니다. 앱에서 다시 로그인하세요.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        NotificationManagerCompat.from(context).notify(900001, n)
    }
}
