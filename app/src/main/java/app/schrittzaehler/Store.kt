package app.schrittzaehler

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate

object Store {
    const val STRIDE_M = 0.75

    fun prefs(c: Context): SharedPreferences =
        c.getSharedPreferences("steps", Context.MODE_PRIVATE)

    private fun key(d: LocalDate) = "d_$d"

    fun stepsOn(c: Context, d: LocalDate): Int = prefs(c).getInt(key(d), 0)

    fun goal(c: Context): Int = prefs(c).getInt("goal", 10000)

    fun setGoal(c: Context, g: Int) {
        prefs(c).edit().putInt("goal", g).apply()
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
