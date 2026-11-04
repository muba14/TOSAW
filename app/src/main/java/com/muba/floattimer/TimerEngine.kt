package com.muba.floattimer

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Kronometre ve zamanlayıcının tek doğruluk kaynağı.
 * Arayüz, yüzen pencere, bildirim ve kilit ekranı aynı durumu izler.
 * Süre, SystemClock.elapsedRealtime() ile ölçülür; böylece uyku/kilit
 * sırasında da doğru kalır.
 */
object TimerEngine {

    enum class Mode { STOPWATCH, TIMER }

    data class State(
        val mode: Mode = Mode.STOPWATCH,
        val running: Boolean = false,
        val accumulatedMs: Long = 0L,
        val startedAt: Long = 0L,
        val durationMs: Long = 5 * 60_000L,
        val finished: Boolean = false,
        /** Tur toplam süreleri, en yenisi başta. */
        val laps: List<Long> = emptyList(),
    ) {
        fun elapsed(now: Long = SystemClock.elapsedRealtime()): Long =
            accumulatedMs + if (running) now - startedAt else 0L

        fun remainingMs(now: Long = SystemClock.elapsedRealtime()): Long =
            (durationMs - elapsed(now)).coerceAtLeast(0L)

        fun displayMs(now: Long = SystemClock.elapsedRealtime()): Long = when (mode) {
            Mode.STOPWATCH -> elapsed(now)
            Mode.TIMER -> remainingMs(now)
        }

        /** Halka göstergesi için 0..1 arası değer. */
        fun progress(now: Long = SystemClock.elapsedRealtime()): Float = when (mode) {
            Mode.STOPWATCH -> (elapsed(now) % 60_000L) / 60_000f
            Mode.TIMER -> if (durationMs <= 0L) 0f else remainingMs(now).toFloat() / durationMs
        }

        /** Sayaç başlatılmış (çalışıyor, duraklatılmış ya da bitmiş) mı? */
        val isActive: Boolean get() = running || accumulatedMs > 0L || finished
    }

    private var prefs: SharedPreferences? = null
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    val current: State get() = _state.value

    fun init(context: Context) {
        val p = context.getSharedPreferences("timer_state", Context.MODE_PRIVATE)
        prefs = p
        val mode = runCatching { Mode.valueOf(p.getString("mode", Mode.STOPWATCH.name)!!) }
            .getOrDefault(Mode.STOPWATCH)
        var s = State(
            mode = mode,
            running = p.getBoolean("running", false),
            accumulatedMs = p.getLong("accumulated", 0L),
            startedAt = p.getLong("startedAt", 0L),
            durationMs = p.getLong("duration", 5 * 60_000L),
        )
        // Cihaz yeniden başladıysa elapsedRealtime sıfırlanmıştır: güvenli şekilde sıfırla.
        if (s.running && s.startedAt > SystemClock.elapsedRealtime()) {
            s = s.copy(running = false, accumulatedMs = 0L, startedAt = 0L)
        }
        _state.value = s
    }

    private inline fun update(block: (State) -> State) {
        val old = _state.value
        val new = block(old)
        if (new == old) return
        _state.value = new
        prefs?.edit()
            ?.putString("mode", new.mode.name)
            ?.putBoolean("running", new.running)
            ?.putLong("accumulated", new.accumulatedMs)
            ?.putLong("startedAt", new.startedAt)
            ?.putLong("duration", new.durationMs)
            ?.apply()
    }

    private fun now() = SystemClock.elapsedRealtime()

    fun start() = update {
        when {
            it.running -> it
            it.mode == Mode.TIMER && it.remainingMs() <= 0L -> it
            else -> it.copy(running = true, startedAt = now(), finished = false)
        }
    }

    fun pause() = update {
        if (!it.running) it else it.copy(running = false, accumulatedMs = it.elapsed(), startedAt = 0L)
    }

    fun toggle() {
        when {
            current.finished -> reset()
            current.running -> pause()
            else -> start()
        }
    }

    fun reset() = update {
        it.copy(running = false, accumulatedMs = 0L, startedAt = 0L, finished = false, laps = emptyList())
    }

    fun setMode(mode: Mode) = update {
        if (it.mode == mode) it else State(mode = mode, durationMs = it.durationMs)
    }

    fun setDuration(ms: Long) = update {
        if (it.isActive) it else it.copy(durationMs = ms.coerceIn(0L, 99 * 3_600_000L))
    }

    /** Zamanlayıcıya süre ekler (+1 dk gibi). */
    fun addTime(ms: Long) = update {
        if (it.mode != Mode.TIMER) it
        else it.copy(durationMs = it.durationMs + ms, finished = false)
    }

    fun lap() = update {
        if (it.mode != Mode.STOPWATCH || !it.running) it
        else it.copy(laps = listOf(it.elapsed()) + it.laps)
    }

    fun markFinished() = update {
        it.copy(running = false, accumulatedMs = it.durationMs, startedAt = 0L, finished = true)
    }
}
