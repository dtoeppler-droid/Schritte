package app.schrittzaehler

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

class MainActivity : Activity(), SharedPreferences.OnSharedPreferenceChangeListener {

    private val de = Locale.GERMAN
    private lateinit var dateTv: TextView
    private lateinit var countTv: TextView
    private lateinit var kmTv: TextView
    private lateinit var kcalTv: TextView
    private lateinit var minTv: TextView
    private lateinit var goalTv: TextView
    private lateinit var recordsTv: TextView
    private lateinit var infoTv: TextView
    private lateinit var bar: ProgressBar
    private lateinit var chart: WeekChart
    private lateinit var weekBtn: Button
    private lateinit var monthBtn: Button
    private var showMonth = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        ensureRunning()
    }

    override fun onResume() {
        super.onResume()
        Store.prefs(this).registerOnSharedPreferenceChangeListener(this)
        if (hasPerm()) startSvc()
        Reminder.schedule(this)
        refresh()
    }

    override fun onPause() {
        Store.prefs(this).unregisterOnSharedPreferenceChangeListener(this)
        super.onPause()
    }

    override fun onSharedPreferenceChanged(p: SharedPreferences?, key: String?) {
        runOnUiThread { refresh() }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun tv(size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun buildUi() {
        val ink = getColor(R.color.ink)
        val mute = getColor(R.color.mute)
        val acc = getColor(R.color.acc)
        val line = getColor(R.color.line)
        val mp = ViewGroup.LayoutParams.MATCH_PARENT
        val wc = ViewGroup.LayoutParams.WRAP_CONTENT

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(32), dp(24), dp(32))
        }

        dateTv = tv(15f, mute)
        root.addView(dateTv)

        countTv = tv(88f, ink, true).apply { includeFontPadding = false }
        root.addView(countTv)
        root.addView(tv(16f, mute).apply { text = "Schritte heute" })

        // Drei Kennzahlen nebeneinander
        val stats = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun stat(label: String): TextView {
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val value = tv(20f, ink, true)
            col.addView(value)
            col.addView(tv(13f, mute).apply { text = label })
            stats.addView(col, LinearLayout.LayoutParams(0, wc, 1f))
            return value
        }
        kmTv = stat("Kilometer")
        kcalTv = stat("≈ Kalorien (kcal)")
        minTv = stat("≈ Aktive Minuten")
        root.addView(stats, LinearLayout.LayoutParams(mp, wc).apply { topMargin = dp(16) })

        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = ColorStateList.valueOf(acc)
            progressBackgroundTintList = ColorStateList.valueOf(line)
        }
        root.addView(bar, LinearLayout.LayoutParams(mp, dp(10)).apply { topMargin = dp(20) })

        val goalRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        goalTv = tv(16f, ink)
        goalRow.addView(goalTv, LinearLayout.LayoutParams(0, wc, 1f))
        goalRow.addView(Button(this).apply {
            text = "Einstellungen"
            setOnClickListener { openSettings() }
        })
        root.addView(goalRow, LinearLayout.LayoutParams(mp, wc).apply { topMargin = dp(8) })

        // Verlauf: 7 Tage oder Monat
        val chartRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        chartRow.addView(tv(16f, ink, true).apply { text = "Verlauf" }, LinearLayout.LayoutParams(0, wc, 1f))
        weekBtn = Button(this).apply {
            text = "7 Tage"
            setOnClickListener { showMonth = false; refresh() }
        }
        monthBtn = Button(this).apply {
            text = "Monat"
            setOnClickListener { showMonth = true; refresh() }
        }
        chartRow.addView(weekBtn)
        chartRow.addView(monthBtn)
        root.addView(chartRow, LinearLayout.LayoutParams(mp, wc).apply { topMargin = dp(24) })

        chart = WeekChart(this)
        root.addView(chart, LinearLayout.LayoutParams(mp, dp(200)).apply { topMargin = dp(8) })

        root.addView(
            tv(16f, ink, true).apply { text = "Rekorde und Bilanz" },
            LinearLayout.LayoutParams(mp, wc).apply { topMargin = dp(28) }
        )
        recordsTv = tv(16f, ink).apply { setLineSpacing(dp(4).toFloat(), 1f) }
        root.addView(recordsTv, LinearLayout.LayoutParams(mp, wc).apply { topMargin = dp(8) })

        infoTv = tv(14f, mute)
        root.addView(infoTv, LinearLayout.LayoutParams(mp, wc).apply { topMargin = dp(24) })

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun refresh() {
        val nf = NumberFormat.getIntegerInstance(de)
        val today = LocalDate.now()
        val all = Store.allDays(this)
        val steps = all[today] ?: 0
        val goal = Store.goal(this)
        val pct = steps * 100 / goal

        dateTv.text = today.dayOfWeek.getDisplayName(TextStyle.FULL, de) + ", " +
            today.dayOfMonth + ". " + today.month.getDisplayName(TextStyle.FULL, de)
        countTv.text = nf.format(steps)
        kmTv.text = String.format(de, "%.1f", Store.km(this, steps))
        kcalTv.text = nf.format(Store.kcal(this, steps))
        minTv.text = nf.format(steps / 100)
        bar.progress = minOf(100, pct)
        goalTv.text = "Ziel: " + nf.format(goal) + " · " + minOf(999, pct) + " %"

        weekBtn.alpha = if (showMonth) 0.5f else 1f
        monthBtn.alpha = if (showMonth) 1f else 0.5f
        if (showMonth) {
            val n = today.lengthOfMonth()
            val list = (1..n).map {
                val d = today.withDayOfMonth(it)
                (if (it == 1 || it % 5 == 0) it.toString() else "") to (all[d] ?: 0)
            }
            chart.set(list, goal, today.dayOfMonth - 1, false)
        } else {
            val list = (6 downTo 0).map {
                val d = today.minusDays(it.toLong())
                d.dayOfWeek.getDisplayName(TextStyle.SHORT, de).removeSuffix(".") to (all[d] ?: 0)
            }
            chart.set(list, goal, 6, true)
        }

        val best = all.maxByOrNull { it.value }
        val bestTxt = if (best == null || best.value == 0) "noch keiner"
        else nf.format(best.value) + " Schritte (" +
            best.key.dayOfMonth + "." + best.key.monthValue + "." + best.key.year + ")"
        val streak = Store.streak(this, all)
        val avg7 = (0..6).map { all[today.minusDays(it.toLong())] ?: 0 }.sum() / 7
        val monthTotal = all.filter { it.key.year == today.year && it.key.month == today.month }
            .values.sum()
        val monthKm = Store.km(this, monthTotal)
        recordsTv.text = "Bester Tag: " + bestTxt + "\n" +
            "Serie: " + streak + (if (streak == 1) " Tag" else " Tage") + " in Folge mit erreichtem Ziel\n" +
            "Ø der letzten 7 Tage: " + nf.format(avg7) + " Schritte\n" +
            "Dieser Monat: " + nf.format(monthTotal) + " Schritte (" + String.format(de, "%.0f", monthKm) + " km)"
    }

    private fun openSettings() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
        }
        fun field(label: String, value: Int): EditText {
            box.addView(TextView(this).apply {
                text = label
                setPadding(0, dp(12), 0, 0)
            })
            val et = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                setText(value.toString())
            }
            box.addView(et)
            return et
        }
        val goal = field("Tagesziel (Schritte)", Store.goal(this))
        val stride = field("Schrittlänge (cm)", Store.strideCm(this))
        val weight = field("Gewicht (kg, für die Kalorien)", Store.weightKg(this))
        val remSwitch = Switch(this).apply {
            text = "Abends erinnern, wenn das Ziel fehlt"
            isChecked = Store.reminderOn(this@MainActivity)
            setPadding(0, dp(20), 0, 0)
        }
        box.addView(remSwitch)
        val hour = field("Uhrzeit der Erinnerung (Stunde, 0 bis 23)", Store.reminderHour(this))

        fun num(e: EditText, lo: Int, hi: Int, cur: Int): Int =
            e.text.toString().toIntOrNull()?.takeIf { it in lo..hi } ?: cur

        AlertDialog.Builder(this)
            .setTitle("Einstellungen")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("Speichern") { _, _ ->
                Store.saveSettings(
                    this,
                    num(goal, 100, 100000, Store.goal(this)),
                    num(stride, 30, 150, Store.strideCm(this)),
                    num(weight, 30, 250, Store.weightKg(this)),
                    remSwitch.isChecked,
                    num(hour, 0, 23, Store.reminderHour(this))
                )
                Reminder.schedule(this)
                StepWidget.refresh(this)
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun hasPerm() =
        checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    private fun startSvc() {
        try {
            startForegroundService(Intent(this, StepService::class.java))
        } catch (e: Exception) {
            infoTv.text = "Der Zähldienst konnte nicht gestartet werden."
        }
    }

    private fun ensureRunning() {
        val sm = getSystemService(SensorManager::class.java)
        if (sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) == null) {
            infoTv.text = "Dein Handy hat keinen Schrittsensor, die App kann deshalb nicht zählen."
            return
        }
        if (hasPerm()) {
            startSvc()
        } else {
            val p = mutableListOf(Manifest.permission.ACTIVITY_RECOGNITION)
            if (Build.VERSION.SDK_INT >= 33) p.add(Manifest.permission.POST_NOTIFICATIONS)
            requestPermissions(p.toTypedArray(), 1)
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (hasPerm()) {
            startSvc()
        } else {
            infoTv.text = "Ohne die Berechtigung „Körperliche Aktivität“ kann die App nicht zählen. " +
                "Du kannst sie in den App-Einstellungen erlauben."
        }
    }
}
