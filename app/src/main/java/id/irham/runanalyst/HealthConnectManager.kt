package id.irham.runanalyst

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Satu sesi lari yang sudah dirapikan. */
data class RunActivity(
    val date: LocalDate,
    val name: String,
    val distanceKm: Double,
    val movingMinutes: Double,
    val avgHr: Int,
) {
    val paceMinPerKm: Double get() = if (distanceKm > 0) movingMinutes / distanceKm else 0.0
    // Intensitas 0..1 dari pace (sama dengan versi web)
    val intensity: Double
        get() = when {
            paceMinPerKm <= 0 -> 0.5
            paceMinPerKm <= 5.0 -> 0.9
            paceMinPerKm <= 5.5 -> 0.75
            else -> 0.45
        }
}

class HealthConnectManager(private val context: Context) {

    val permissions = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
    )

    fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    private fun client() = HealthConnectClient.getOrCreate(context)

    suspend fun hasPermissions(): Boolean = client().permissionController.grantedPermissions.containsAll(permissions)

    suspend fun requestPermissions(): Boolean {
        val c = client()
        val granted = c.permissionController.requestGrantedPermissions(permissions)
        return granted.containsAll(permissions)
    }

    /** Baca semua sesi lari (EXERCISE_TYPE_RUNNING) dalam N hari terakhir. */
    suspend fun readRuns(daysBack: Long = 180): List<RunActivity> {
        val c = client()
        val zone = ZoneId.systemDefault()
        val end = Instant.now()
        val start = end.minus(Duration.ofDays(daysBack))
        val filter = TimeRangeFilter.between(start, end)

        val sessions = c.readRecords(
            ReadRecordsRequest(ExerciseSessionRecord::class, filter)
        ).records.filter { it.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_RUNNING }

        return sessions.map { s ->
            val sFilter = TimeRangeFilter.between(s.startTime, s.endTime)
            // Jarak
            val distMeters = c.readRecords(ReadRecordsRequest(DistanceRecord::class, sFilter))
                .records.sumOf { it.distance.inMeters }
            // HR rata-rata
            val hrs = c.readRecords(ReadRecordsRequest(HeartRateRecord::class, sFilter))
                .records.flatMap { it.samples }.map { it.beatsPerMinute }
            val km = distMeters / 1000.0
            val minutes = Duration.between(s.startTime, s.endTime).toSeconds() / 60.0
            RunActivity(
                date = s.startTime.atZone(zone).toLocalDate(),
                name = s.metadata.dataOrigin.packageName.substringAfterLast('.'),
                distanceKm = Math.round(km * 10) / 10.0,
                movingMinutes = Math.round(minutes),
                avgHr = if (hrs.isNotEmpty()) hrs.map { it.toInt() }.average().toInt() else 0,
            )
        }.filter { it.distanceKm >= 0.5 }.sortedBy { it.date }
    }
}
