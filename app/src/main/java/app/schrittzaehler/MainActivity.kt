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
    private lateinit var goalTv: TextView
    private lateinit var infoTv: TextView
    private lateinit var bar: ProgressBar
    private lateinit var chart: WeekChart

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        ensureRunning()
    }

    override fun onResume() {
        super.onResume()
        Store.prefs(this).registerOnSharedPreferenceChangeListener(this)
        if (hasPerm()) startSvc()
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

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(32), dp(24), dp(32))
        }

        dateTv = tv(15f, mute)
        root.addView(dateTv)

        countTv = tv(88f, ink, true).apply { includeFontPadding = false }
        root.addView(countTv)

        root.addView(tv(16f, mute).apply { text = "Schritte heute" })

        kmTv = tv(20f, ink, true).apply { setPadding(0, dp(8), 0, 0) }
        root.addView(kmTv)

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
        goalRow.addView(goalTv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        goalRow.addView(Button(this).apply {
            text = "Ändern"
            setOnClickListener { askGoal() }
        })
        root.addView(goalRow, LinearLayout.LayoutParams(mp, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })

        root.addView(
            tv(16f, ink, true).apply { text = "Letzte 7 Tage" },
            LinearLayout.LayoutParams(mp, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(32) }
        )
        chart = WeekChart(this)
        root.addView(chart, LinearLayout.LayoutParams(mp, dp(200)).apply { topMargin = dp(8) })

        infoTv = tv(14f, mute)
        root.addView(infoTv, LinearLayout.LayoutParams(mp, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(24) })

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun refresh() {
        val nf = NumberFormat.getIntegerInstance(de)
        val today = LocalDate.now()
        val steps = Store.stepsOn(this, today)
        val goal = Store.goal(this)

        dateTv.text = today.dayOfWeek.getDisplayName(TextStyle.FULL, de) + ", " +
            today.dayOfMonth + ". " + today.month.getDisplayName(TextStyle.FULL, de)
        countTv.text = nf.format(steps)
        kmTv.text = String.format(de, "≈ %.1f km", steps * Store.STRIDE_M / 1000.0)
        bar.progress = minOf(100, steps * 100 / goal)
        goalTv.text = "Ziel: " + nf.format(goal) + " Schritte"

        val days = (6 downTo 0).map {
            val d = today.minusDays(it.toLong())
            d.dayOfWeek.getDisplayName(TextStyle.SHORT, de).removeSuffix(".") to Store.stepsOn(this, d)
        }
        chart.set(days, goal)
    }

    private fun askGoal() {
        val et = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(Store.goal(this@MainActivity).toString())
            setSelectAllOnFocus(true)
        }
        AlertDialog.Builder(this)
            .setTitle("Tagesziel in Schritten")
            .setView(et)
            .setPositiveButton("Speichern") { _, _ ->
                val v = et.text.toString().toIntOrNull()
                if (v != null && v in 100..100000) Store.setGoal(this, v)
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
