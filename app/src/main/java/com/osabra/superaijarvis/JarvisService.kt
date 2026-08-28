package com.osabra.superaijarvis

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class JarvisService : Service() {
    override fun onCreate() {
        super.onCreate()
        val channelId = "jarvis_channel_v8"
        val channel = NotificationChannel(channelId, "JARVIS 24/7", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, channelId)
           .setContentTitle("JARVIS V8 ACTIVO")
           .setContentText("Escuchando Hey Jarvis - Vitoria")
           .setSmallIcon(R.mipmap.ic_launcher)
           .setContentIntent(pendingIntent)
           .setOngoing(true)
           .build()
        startForeground(1, notification)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int { return START_STICKY }
    override fun onBind(intent: Intent?): IBinder? = null
}
