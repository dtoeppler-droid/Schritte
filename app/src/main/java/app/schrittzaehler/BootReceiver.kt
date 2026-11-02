package app.schrittzaehler

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action != Intent.ACTION_BOOT_COMPLETED) return
        if (c.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)
            != PackageManager.PERMISSION_GRANTED
        ) return
        try {
            c.startForegroundService(Intent(c, StepService::class.java))
        } catch (e: Exception) {
            // Wenn Android den Start nach dem Neustart blockiert, startet die App beim nächsten Öffnen.
        }
    }
}
