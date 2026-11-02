package app.schrittzaehler

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

object Reminder {
    const val CH = "reminder"

    private fun pending(c: Context): PendingIntent =
        PendingIntent.getBroadcast(
            c, 1, Intent(c, ReminderReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    // Plant die nächste Erinnerung (ungefähr zur gewählten Stunde, ohne Sonderberechtigung).
    fun schedule(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java)
        val pi = pending(c)
        am.cancel(pi)
        if (!Store.reminderOn(c)) return
        var t = LocalDate.now().atTime(Store.reminderHour(c), 0)
        if (!t.isAfter(LocalDateTime.now())) t = t.plusDays(1)
        val ms = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
    }

    fun notifyIfNeeded(c: Context) {
        val steps = Store.stepsOn(c, LocalDate.now())
        val goal = Store.goal(c)
        if (steps >= goal) return
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH, "Erinnerung", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = PendingIntent.getActivity(
            c, 2, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val rest = NumberFormat.getIntegerInstance(Locale.GERMAN).format(goal - steps)
        val n = Notification.Builder(c, CH)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Tagesziel noch offen")
            .setContentText("Dir fehlen noch $rest Schritte bis zum Ziel.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(2, n)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (Store.reminderOn(c)) Reminder.notifyIfNeeded(c)
        Reminder.schedule(c)
    }
}
