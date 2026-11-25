package id.irham.runanalyst

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class WeekStat(val km: Double, val load: Double, val runs: Int, val hardKm: Double, val pace: Double)

/** 12 minggu terakhir yang berakhir pada minggu aktivitas terakhir. */
fun weeklyStats(acts: List<RunActivity>, today: LocalDate = LocalDate.now()): List<WeekStat> {
    var end = today
    if (acts.isNotEmpty()) {
        val lastAct = acts.last().date
        val firstAct = acts.first().date
        end = if (lastAct.isBefore(today.minusDays(7)))
            maxOf(lastAct, firstAct.plusWeeks(11)) else lastAct
    }
    val weeks = mutableListOf<WeekStat>()
    for (w in 11 downTo 0) {
        val endDate = end.minusWeeks(w.toLong())
        val startDate = endDate.minusWeeks(1)
        val inWeek = acts.filter { it.date > startDate && it.date <= endDate }
        val km = inWeek.sumOf { it.distanceKm }
        val load = inWeek.sumOf { it.movingMinutes * it.intensity }
        val hardKm = inWeek.filter { it.intensity > 0.7 }.sumOf { it.distanceKm }
        val pace = if (km > 0) inWeek.sumOf { it.paceMinPerKm * it.distanceKm } / km else 0.0
        weeks.add(WeekStat(km, load, inWeek.size, hardKm, pace))
    }
    return weeks
}

data class Recommendation(val level: String, val icon: String, val text: String)

fun buildRecommendations(weeks: List<WeekStat>, acts: List<RunActivity>): List<Recommendation> {
    val recs = mutableListOf<Recommendation>()
    if (acts.isNotEmpty() && weeks.all { it.km == 0.0 }) {
        val fmt = DateTimeFormatter.ISO_DATE
        recs += Recommendation("warn", "📅",
            "Ada ${acts.size} aktivitas, tapi di luar 12 minggu terakhir (${acts.first().date.format(fmt)} – ${acts.last().date.format(fmt)}).")
    }
    val last = weeks.last()
    val prev = weeks[10]
    val last4 = weeks.subList(8, 12)

    if (prev.km > 0) {
        val delta = (last.km - prev.km) / prev.km * 100
        when {
            delta > 15 -> recs += Recommendation("bad", "⚠️",
                "Jarak mingguan naik ${delta.toInt()}% dibanding minggu lalu (${fmt1(prev.km)} → ${fmt1(last.km)} km). " +
                    "Batas aman umumnya +10–15% per minggu untuk menghindari cedera. Tahan volume minggu depan.")
            delta < -40 -> recs += Recommendation("warn", "📉",
                "Jarak mingguan turun ${(-delta).toInt()}% — bagus untuk recovery jika sengaja (cutback week), tapi jangan berlarut kalau sedang mengejar target.")
        }
    }

    val hardPct = last4.sumOf { it.hardKm } / maxOf(1.0, last4.sumOf { it.km }) * 100
    when {
        hardPct > 30 -> recs += Recommendation("warn", "🔥",
            "${hardPct.toInt()}% jarakmu 4 minggu terakhir di zona keras. Pola ideal ±80% easy / 20% keras. Tambah easy run santai.")
        hardPct < 8 -> recs += Recommendation("warn", "🎯",
            "Hanya ${hardPct.toInt()}% latihan di zona keras. Kalau targetnya mempercepat pace, masukkan 1 sesi tempo/interval per minggu.")
        else -> recs += Recommendation("good", "✅",
            "Distribusi intensitas sehat: ${(100 - hardPct).toInt()}% easy / ${hardPct.toInt()}% keras — sesuai pola 80/20.")
    }

    val avgRuns = last4.sumOf { it.runs } / 4.0
    if (avgRuns < 3) recs += Recommendation("warn", "📅",
        "Rata-rata ${fmt1(avgRuns)} sesi lari per minggu. Konsistensi 3–5 sesi/minggu lebih efektif menaikkan kapasitas aerobik.")
    else recs += Recommendation("good", "✅", "Konsistensi bagus: rata-rata ${avgRuns.toInt()} sesi lari per minggu.")

    val paceOld = weeks.subList(0, 4).filter { it.pace > 0 }
    val paceNew = weeks.subList(8, 12).filter { it.pace > 0 }
    if (paceOld.isNotEmpty() && paceNew.isNotEmpty()) {
        val p0 = paceOld.sumOf { it.pace } / paceOld.size
        val p1 = paceNew.sumOf { it.pace } / paceNew.size
        val sec = (p1 - p0) * 60
        when {
            sec < -8 -> recs += Recommendation("good", "⚡",
                "Pace membaik ${(-sec).toInt()} detik/km dibanding awal periode. Pertahankan!")
            sec > 8 -> recs += Recommendation("warn", "🐢",
                "Pace melambat ${sec.toInt()} detik/km dibanding awal periode — bisa tanda kelelahan akumulatif.")
        }
    }

    val loadAvg = weeks.subList(0, 11).sumOf { it.load } / 11.0
    if (loadAvg > 0 && last.load > loadAvg * 1.4) recs += Recommendation("bad", "🚨",
        "Training load minggu ini ${((last.load / loadAvg - 1) * 100).toInt()}% di atas rata-rata 11 minggu. Sisipkan 1–2 hari istirahat.")

    if (last.runs >= 5) recs += Recommendation("warn", "😴",
        "Kamu lari ${last.runs} hari minggu ini tanpa cukup recovery. Minimal 1–2 hari istirahat penuh per minggu.")

    return recs
}

fun fmt1(n: Double) = String.format(Locale.US, "%.1f", n)
fun fmtPace(minPerKm: Double): String {
    if (minPerKm <= 0) return "—"
    val m = minPerKm.toInt()
    val s = ((minPerKm - m) * 60).toInt()
    return "$m:${s.toString().padStart(2, '0')}"
}
