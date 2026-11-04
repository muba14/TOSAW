package com.muba.floattimer

import java.util.Locale

object TimeFormat {

    /** 05:07 veya 1:05:07 */
    fun clock(ms: Long): String {
        val t = ms.coerceAtLeast(0L) / 1000
        val h = t / 3600
        val m = (t % 3600) / 60
        val s = t % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%02d:%02d", m, s)
    }

    /** .42 (salise) */
    fun centis(ms: Long): String =
        String.format(Locale.US, ".%02d", (ms.coerceAtLeast(0L) % 1000) / 10)

    /** Geri sayımda 4,2 sn kaldığında "00:05" görünsün diye yukarı yuvarlar. */
    fun ceilSecond(ms: Long): Long = ((ms + 999) / 1000) * 1000

    fun display(state: TimerEngine.State, now: Long): String = when (state.mode) {
        TimerEngine.Mode.STOPWATCH -> clock(state.elapsed(now))
        TimerEngine.Mode.TIMER -> clock(ceilSecond(state.remainingMs(now)))
    }
}
