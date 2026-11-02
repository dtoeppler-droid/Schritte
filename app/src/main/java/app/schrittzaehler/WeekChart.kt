package app.schrittzaehler

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.view.View
import java.text.NumberFormat
import java.util.Locale

class WeekChart(context: Context) : View(context) {
    private var days: List<Pair<String, Int>> = emptyList()
    private var goal = 10000
    private val d = resources.displayMetrics.density
    private val sp = resources.displayMetrics.scaledDensity
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val ln = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = d
        pathEffect = DashPathEffect(floatArrayOf(8f * d, 6f * d), 0f)
    }

    fun set(days: List<Pair<String, Int>>, goal: Int) {
        this.days = days
        this.goal = goal
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        if (days.isEmpty()) return
        val ink = context.getColor(R.color.ink)
        val mute = context.getColor(R.color.mute)
        val w = width.toFloat()
        val h = height.toFloat()
        val top = 20f * d
        val bottom = h - 24f * d
        val maxV = maxOf(goal, days.maxOf { it.second }).toFloat()
        val slot = w / days.size
        val bw = slot * 0.54f

        val gy = bottom - goal / maxV * (bottom - top)
        ln.color = mute
        c.drawLine(0f, gy, w, gy, ln)

        val nf = NumberFormat.getIntegerInstance(Locale.GERMAN)
        days.forEachIndexed { i, (label, v) ->
            val x = i * slot + (slot - bw) / 2
            val bh = v / maxV * (bottom - top)
            bar.color = context.getColor(if (i == days.lastIndex) R.color.acc else R.color.bar_old)
            if (bh >= 1f) c.drawRoundRect(x, bottom - bh, x + bw, bottom, 6f * d, 6f * d, bar)
            txt.color = ink
            txt.textSize = 11f * sp
            c.drawText(nf.format(v), x + bw / 2, bottom - bh - 4f * d, txt)
            txt.color = mute
            txt.textSize = 12f * sp
            c.drawText(label, x + bw / 2, h - 6f * d, txt)
        }
    }
}
