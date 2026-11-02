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
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.text.NumberFormat
import java.time.LocalDate
import java.time.Month
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
    private lateinit var yearInfoTv: TextView
    private lateinit var infoTv: TextView
    private lateinit var yearLabel: TextView
    private lateinit var bar: ProgressBar
    private lateinit var chart: WeekChart
    private lateinit var weekBtn: Button
    private lateinit var monthBtn: Button
    private lateinit var yearBtn: Button
    private lateinit var prevBtn: Button
    private lateinit var nextBtn: Button
    private lateinit var yearRow: LinearLayout

    private var mode = 0 // 0 = 7 Tage, 1 = Monat, 2 = Jahr
    private var viewYear = LocalDate.now().year

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

        // Haupt-Container der gesamten Ansicht (mit VSA-Dark-Background)
        val mainContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF121212.toInt()) // VSA Dark Mode Background
        }

        // --- VSA HEADER / TOP APP BAR ---
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
            setBackgroundColor(0xFF1A1A1E.toInt()) // VSA Header Dark Tone
        }

        // VSA Geflügelter Schlüssel / Logo-Icon
        val logoIv = ImageView(this).apply {
            setImageResource(R.drawable.ic_vsa_wing_key)
            imageTintList = ColorStateList.valueOf(0xFF00F2FE.toInt()) // VSA Cyan Accent
        }
        topBar.addView(logoIv, LinearLayout.LayoutParams(dp(28), dp(28)))

        // VSA Steps Titel
        val titleTv = tv(20f, 0xFFFFFFFF.toInt(), true).apply {
            text = "VSA Steps"
            setPadding(dp(12), 0, 0, 0)
        }
        topBar.addView(titleTv, LinearLayout.LayoutParams(0, wc, 1f))

        mainContainer.addView(topBar, LinearLayout.LayoutParams(mp, wc))

        // --- SCROLLVIEW FÜR DEN INHALT ---
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(32))
        }

        dateTv = tv(15f, mute)
        root.addView(dateTv)

        countTv = tv(88f, ink, true).apply { includeFontPadding = false }
        root.addView(countTv)
        root.addView(tv(16f, mute).apply { text = "Schritte heute" })

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

        // ---- Verlauf: 7 Tage / Monat / Jahr ----
        root.addView(
            tv(16f, ink, true).apply { text = "Verlauf" },
            LinearLayout.LayoutParams(mp, wc).apply { topMargin = dp(24) }
        )
        val modeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        weekBtn = Button(this).apply { text = "7 Tage"; setOnClickListener { mode = 0; refresh() } }
        monthBtn = Button(this).apply { text = "Monat"; setOnClickListener { mode = 1; refresh() } }
        yearBtn = Button(this).apply { text = "Jahr"; setOnClickListener { mode = 2; refresh() } }
        modeRow.addView(weekBtn, LinearLayout.LayoutParams(0, wc, 1f))
        modeRow.addView(monthBtn, LinearLayout.LayoutParams(0, wc, 1f))
        modeRow.addView(yearBtn, LinearLayout.LayoutParams(0, wc, 1f))
        root.addView(modeRow, LinearLayout.LayoutParams(mp, wc).apply { topMargin = dp(4) })

        yearRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        prevBtn = Button(this).apply { text = "‹"; setOnClickListener { viewYear--; refresh() } }
        nextBtn = Button(this).apply { text = "›"; setOnClickListener { viewYear++; refresh() } }
        yearLabel = tv(18f, ink, true).apply { gravity = Gravity.CENTER }
        yearRow.addView(prevBtn)
        yearRow.addView(yearLabel, LinearLayout.LayoutParams(dp(90), wc))
        yearRow.addView(nextBtn)
        root.addView(yearRow, LinearLayout.LayoutParams(mp, wc))

        chart = WeekChart(this)
        root.addView(chart, LinearLayout.LayoutParams(mp, dp(200)).apply { topMargin = dp(8) })

        yearInfoTv = tv(16f, ink).apply {
            setLineSpacing(dp(4).toFloat(), 1f)
            visibility = View.GONE
        }
        root.addView(yearInfoTv, LinearLayout.LayoutParams(mp, wc).apply { topMargin = dp(8) })

        // ---- Rekorde ----
        root.addView(
            tv(16f, ink, true).apply { text = "Rekorde und Bilanz" },
            LinearLayout.LayoutParams(mp, wc).apply { topMargin = dp(28) }
        )
        recordsTv = tv(16f, ink).apply { setLineSpacing(dp(4).toFloat(), 1f) }
        root.addView(recordsTv, LinearLayout.LayoutParams(mp, wc).apply { topMargin = dp(8) })

        // ---- Datensicherung ----
        root.addView(
            tv(16f, ink, true).apply { text = "Datensicherung" },
            LinearLayout.LayoutParams(mp, wc).apply { topMargin = dp(28) }
        )
        root.addView(
            tv(14f, mute).apply {
                text = "Speichert alle Tageswerte und Einstellungen in einer Datei. " +
                    "Auf einem anderen Handy kannst du sie wieder einlesen."
            },
            LinearLayout.LayoutParams(mp, wc).apply { topMargin = dp(4) }
        )
        val backupRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        backupRow.addView(Button(this).apply {
            text = "Exportieren"
            setOnClickListener { startExport() }
        }, LinearLayout.LayoutParams(0, wc, 1f))
        backupRow.addView(Button(this).apply {
            text = "Importieren"
            setOnClickListener { startImport() }
        }, LinearLayout.LayoutParams(0, wc, 1f))
        root.addView(backupRow, LinearLayout.LayoutParams(mp, wc).apply { topMargin = dp(4) })

        infoTv = tv(14f, mute)
        root.addView(infoTv, LinearLayout.LayoutParams(mp, wc).apply { topMargin = dp(24) })

        // ScrollView unter die TopBar in den Hauptcontainer hängen
        mainContainer.addView(ScrollView(this).apply { addView(root) }, LinearLayout.LayoutParams(mp, mp))

        setContentView(mainContainer)
    }

    private fun refresh() {
        val nf = NumberFormat.getIntegerInstance(de)
        val today = LocalDate.now()
        val all = Store.allDays(this)
        val steps = all[today] ?: 0
        val goal = Store.goal(this)
        val pct = if (goal > 0) steps * 100 / goal else 0

        dateTv.text = today.dayOfWeek.getDisplayName(TextStyle.FULL, de) + ", " +
            today.dayOfMonth + ". " + today.month.getDisplayName(TextStyle.FULL, de)
        countTv.text = nf.format(steps)
        kmTv.text = String.format(de, "%.1f", Store.km(this, steps))
        kcalTv.text = nf.format(Store.kcal(this, steps))
        minTv.text = nf.format(steps / 100)
        bar.progress = minOf(100, pct)
        goalTv.text = "Ziel: " + nf.format(goal) + " · " + minOf(999, pct) + " %"

        weekBtn.alpha = if (mode == 0) 1f else 0.5f
        monthBtn.alpha = if (mode == 1) 1f else 0.5f
        yearBtn.alpha = if (mode == 2) 1f else 0.5f
        yearRow.visibility = if (mode == 2) View.VISIBLE else View.GONE
        yearInfoTv.visibility = if (mode == 2) View.VISIBLE else View.GONE

        when (mode) {
            0 -> {
                val list = (6 downTo 0).map {
                    val d = today.minusDays(it.toLong())
                    d.dayOfWeek.getDisplayName(TextStyle.SHORT, de).removeSuffix(".") to (all[d] ?: 0)
                }
                chart.set(list, goal, 6, true)
            }
            1 -> {
                val n = today.lengthOfMonth()
                val list = (1..n).map {
                    val d = today.withDayOfMonth(it)
                    (if (it == 1 || it % 5 == 0) it.toString() else "") to (all[d] ?: 0)
                }
                chart.set(list, goal, today.dayOfMonth - 1, false)
            }
            else -> {
                val minYear = minOf(today.year, all.keys.minOfOrNull { it.year } ?: today.year)
                if (viewYear > today.year) viewYear = today.year
                if (viewYear < minYear) viewYear = minYear
                yearLabel.text = viewYear.toString()
                prevBtn.isEnabled = viewYear > minYear
                nextBtn.isEnabled = viewYear < today.year

                val perMonth = IntArray(12)
                for ((d, v) in all) {
                    if (d.year == viewYear) perMonth[d.monthValue - 1] += v
                }
                val list = (1..12).map { m ->
                    Month.of(m).getDisplayName(TextStyle.SHORT, de).replace(".", "").take(3) to perMonth[m - 1]
                }
                chart.set(list, goal * 30, if (viewYear == today.year) today.monthValue - 1 else -1, true, true)

                val total = perMonth.sum()
                if (total == 0) {
                    yearInfoTv.text = "Für " + viewYear + " gibt es noch keine Daten."
                } else {
                    val bestM = perMonth.indices.maxByOrNull { perMonth[it] } ?: 0
                    val activeDays = all.count { it.key.year == viewYear && it.value > 0 }
                    yearInfoTv.text = "Gesamt: " + nf.format(total) + " Schritte (" +
                        String.format(de, "%.0f", Store.km(this, total)) + " km)\n" +
                        "Bester Monat: " + Month.of(bestM + 1).getDisplayName(TextStyle.FULL, de) +
                        " mit " + nf.format(perMonth[bestM]) + " Schritten\n" +
                        "Ø pro Tag mit Schritten: " + nf.format(total / maxOf(1, activeDays)) + "\n" +
                        "Die gestrichelte Linie zeigt dein Tagesziel mal 30."
                }
            }
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

    private fun startExport() {
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "vsa-steps-sicherung.json")
        }
        startActivityForResult(i, REQ_EXPORT)
    }

    private fun startImport() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(i, REQ_IMPORT)
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        val uri = data?.data
        if (res != RESULT_OK || uri == null) return
        if (req == REQ_EXPORT) {
            try {
                contentResolver.openOutputStream(uri, "wt")?.use {
                    it.write(Backup.toJson(this).toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(this, "Sicherung gespeichert.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Das Speichern hat nicht geklappt.", Toast.LENGTH_LONG).show()
            }
        } else if (req == REQ_IMPORT) {
            try {
                val text = contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                val n = if (text.length > 5_000_000) -1 else Backup.fromJson(this, text)
                if (n < 0) {
                    Toast.makeText(this, "Das ist keine gültige Sicherungsdatei.", Toast.LENGTH_LONG).show()
                } else {
                    Reminder.schedule(this)
                    StepWidget.refresh(this)
                    refresh()
                    Toast.makeText(this, "Fertig: " + n + " Tage übernommen.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Die Datei konnte nicht gelesen werden.", Toast.LENGTH_LONG).show()
            }
        }
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

    companion object {
        const val REQ_EXPORT = 11
        const val REQ_IMPORT = 12
    }
}
