package app.schrittzaehler

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate

object Store {

    fun prefs(c: Context): SharedPreferences =
        c.getSharedPreferences("steps", Context.MODE_PRIVATE)

    private fun key(d: LocalDate) = "d_$d"

    fun stepsOn(c: Context, d: LocalDate): Int = prefs(c).getInt(key(d), 0)

    // ---- Einstellungen ----
    fun goal(c: Context): Int = prefs(c).getInt("goal", 10000)
    fun strideCm(c: Context): Int = prefs(c).getInt("stride_cm", 75)
    fun weightKg(c: Context): Int = prefs(c).getInt("weight", 70)
    fun reminderOn(c: Context): Boolean = prefs(c).getBoolean("rem_on", false)
    fun reminderHour(c: Context): Int = prefs(c).getInt("rem_hour", 19)

    fun saveSettings(c: Context, goal: Int, stride: Int, weight: Int, remOn: Boolean, remHour: Int) {
        prefs(c).edit()
            .putInt("goal", goal)
            .putInt("stride_cm", stride)
            .putInt("weight", weight)
            .putBoolean("rem_on", remOn)
            .putInt("rem_hour", remHour)
            .apply()
    }

    // ---- Umrechnungen ----
    fun km(c: Context, steps: Int): Double = steps * strideCm(c) / 100000.0

    // Faustformel fürs Gehen: etwa 0,5 kcal pro kg Körpergewicht und Kilometer
    fun kcal(c: Context, steps: Int): Int = (km(c, steps) * weightKg(c) * 0.5).toInt()

    // ---- Auswertung ----
    fun allDays(c: Context): Map<LocalDate, Int> {
        val out = HashMap<LocalDate, Int>()
        for ((k, v) in prefs(c).all) {
            if (k.startsWith("d_") && v is Int) {
                try {
                    out[LocalDate.parse(k.substring(2))] = v
                } catch (e: Exception) {
                }
            }
        }
        return out
    }

    fun streak(c: Context, all: Map<LocalDate, Int>): Int {
        val g = goal(c)
        if (g <= 0) return 0
        var d = LocalDate.now()
        if ((all[d] ?: 0) < g) d = d.minusDays(1)
        var n = 0
        while ((all[d] ?: 0) >= g) {
            n++
            d = d.minusDays(1)
        }
        return n
    }

    // Der Schrittsensor liefert einen Gesamtzähler seit dem letzten Neustart.
    // Wir bilden die Differenz zum letzten Wert und buchen sie auf heute.
    fun onSensor(c: Context, counter: Float) {
        val p = prefs(c)
        val last = p.getFloat("last", -1f)
        val delta = when {
            last < 0f -> 0f
            counter >= last -> counter - last
            else -> counter
        }
        val k = key(LocalDate.now())
        val e = p.edit().putFloat("last", counter)
        if (delta > 0f) e.putInt(k, p.getInt(k, 0) + delta.toInt())
        e.apply()
    }
}
