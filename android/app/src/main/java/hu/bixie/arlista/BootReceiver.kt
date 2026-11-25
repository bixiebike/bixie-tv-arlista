package hu.bixie.arlista

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Bekapcsolás (teljes indulás) után elindítja a figyelő szolgáltatást, és előtérbe hozza az árlistát. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WatchService.start(context)
        WatchService.bringToFront(context)
    }
}
