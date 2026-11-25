package hu.bixie.arlista

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/**
 * Előtérben futó szolgáltatás. A legtöbb tévé kikapcsoláskor csak készenlétbe megy,
 * ilyenkor nincs új rendszerindulás, ezért a képernyő bekapcsolását (SCREEN_ON) figyeli,
 * és ilyenkor is előtérbe hozza az árlistát.
 */
class WatchService : Service() {

    private val screenOn = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // a tévé saját kezdőképernyője is ekkor indul: kicsit várunk, hogy mi kerüljünk felülre
            Handler(Looper.getMainLooper()).postDelayed({ bringToFront(context) }, 4_000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val builder: Notification.Builder
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Bixie árlista", NotificationManager.IMPORTANCE_MIN)
            )
            builder = Notification.Builder(this, CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            builder = Notification.Builder(this)
        }
        val n = builder
            .setContentTitle("Bixie árlista")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
        registerReceiver(screenOn, IntentFilter(Intent.ACTION_SCREEN_ON))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        try {
            unregisterReceiver(screenOn)
        } catch (e: Exception) {
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "arlista"

        fun start(context: Context) {
            val i = Intent(context, WatchService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
                else context.startService(i)
            } catch (e: Exception) {
            }
        }

        fun bringToFront(context: Context) {
            try {
                context.startActivity(
                    Intent(context, MainActivity::class.java).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    )
                )
            } catch (e: Exception) {
            }
        }
    }
}
