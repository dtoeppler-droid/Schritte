package app.schrittzaehler

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate

object Backup {

    fun toJson(c: Context): String {
        val o = JSONObject()
        o.put("app", "stampfer")
        o.put("version", 1)
        val s = JSONObject()
        s.put("goal", Store.goal(c))
        s.put("stride_cm", Store.strideCm(c))
        s.put("weight", Store.weightKg(c))
        s.put("rem_on", Store.reminderOn(c))
        s.put("rem_hour", Store.reminderHour(c))
        o.put("settings", s)
        val d = JSONObject()
        for ((k, v) in Store.allDays(c)) d.put(k.toString(), v)
        o.put("days", d)
        return o.toString(2)
    }

    // Gibt die Anzahl übernommener Tage zurück, bei einer ungültigen Datei -1.
    // Bei Überschneidungen gilt der höhere Wert, es geht also nichts verloren.
    fun fromJson(c: Context, text: String): Int {
        try {
            val o = JSONObject(text)
            if (o.optString("app") != "stampfer") return -1
            val d = o.getJSONObject("days")
            val cur = Store.allDays(c)
            val e = Store.prefs(c).edit()
            var n = 0
            val keys = d.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val date = LocalDate.parse(k)
                val v = d.getInt(k)
                if (v < 0 || v > 200000) continue
                if (v > (cur[date] ?: 0)) {
                    e.putInt("d_$date", v)
                    n++
                }
            }
            val s = o.optJSONObject("settings")
            if (s != null) {
                e.putInt("goal", s.optInt("goal", Store.goal(c)).coerceIn(100, 100000))
                e.putInt("stride_cm", s.optInt("stride_cm", Store.strideCm(c)).coerceIn(30, 150))
                e.putInt("weight", s.optInt("weight", Store.weightKg(c)).coerceIn(30, 250))
                e.putBoolean("rem_on", s.optBoolean("rem_on", Store.reminderOn(c)))
                e.putInt("rem_hour", s.optInt("rem_hour", Store.reminderHour(c)).coerceIn(0, 23))
            }
            e.apply()
            return n
        } catch (ex: Exception) {
            return -1
        }
    }
}
