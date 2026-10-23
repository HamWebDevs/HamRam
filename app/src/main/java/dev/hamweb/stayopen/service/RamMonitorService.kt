package dev.hamweb.stayopen.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dev.hamweb.stayopen.MainActivity
import dev.hamweb.stayopen.R
import dev.hamweb.stayopen.data.RamController
import kotlinx.coroutines.*

/** Optional, user-started monitoring; this service itself has no process-killing permission. */
class RamMonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val controller by lazy { RamController.get(this) }
    private var observer: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Automatic RAM clearing", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Visible controls for the optional Shizuku-assisted RAM monitor"
            setShowBadge(false)
        })
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, ID, notification("Watching RAM pressure · tap Stop to end"), type)
        controller.beginAutomaticSession()
        if (observer == null) observer = scope.launch {
            controller.state.collect { s ->
                val text = when {
                    !s.automatic -> "Stopping RAM monitor"
                    s.screenPaused -> "Paused while screen is off"
                    s.busy -> "Clearing eligible cached apps…"
                    else -> "RAM pressure watch · ${s.runs.lastOrNull()?.exited ?: 0} processes exited in last run"
                }
                manager.notify(ID, notification(text))
            }
        }
        return START_NOT_STICKY
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 2, Intent(this, RamMonitorService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_stayopen_mono)
            .setContentTitle("StayOpen · Auto clear RAM").setContentText(text).setContentIntent(open)
            .setOngoing(true).setSilent(true).setOnlyAlertOnce(true).setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, "Stop", stop).build()
    }

    override fun onDestroy() {
        controller.endAutomaticSession(); scope.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL = "ram-auto-clear"
        private const val ID = 120
        private const val ACTION_STOP = "dev.hamweb.stayopen.STOP_RAM_MONITOR"
    }
}
