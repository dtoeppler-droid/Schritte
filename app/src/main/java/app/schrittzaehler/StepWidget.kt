package app.schrittzaehler

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

class StepWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        refresh(context)
    }

    companion object {
        fun refresh(c: Context) {
            val mgr = AppWidgetManager.getInstance(c)
            val ids = mgr.getAppWidgetIds(ComponentName(c, StepWidget::class.java))
            if (ids.isEmpty()) return
            val nf = NumberFormat.getIntegerInstance(Locale.GERMAN)
            val steps = Store.stepsOn(c, LocalDate.now())
            val goal = Store.goal(c)
            val v = RemoteViews(c.packageName, R.layout.widget)
            v.setTextViewText(R.id.w_steps, nf.format(steps))
            v.setTextViewText(R.id.w_sub, "Schritte · Ziel " + nf.format(goal))
            v.setProgressBar(R.id.w_bar, 100, minOf(100, steps * 100 / goal), false)
            val pi = PendingIntent.getActivity(
                c, 3, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
            )
            v.setOnClickPendingIntent(R.id.w_root, pi)
            mgr.updateAppWidget(ids, v)
        }
    }
}
