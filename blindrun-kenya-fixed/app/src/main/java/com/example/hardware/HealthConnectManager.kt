package com.example.hardware

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.example.data.model.HealthMetricRecord
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

data class HealthConnectStatus(
  val isAvailable: Boolean = false,
  val isInstalled: Boolean = true,
  val hasPermissions: Boolean = false,
  val unsupportedNotes: List<String> = emptyList(),
  val statusMessage: String = "Checking Health Connect availability...",
)

class HealthConnectManager(context: Context) {
  private val appContext = context.applicationContext

  val requiredPermissions: Set<String> =
    setOf(
      HealthPermission.getReadPermission(HeartRateRecord::class),
      HealthPermission.getReadPermission(StepsRecord::class),
      HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
      HealthPermission.getReadPermission(ExerciseSessionRecord::class),
      HealthPermission.getReadPermission(DistanceRecord::class),
      HealthPermission.getReadPermission(OxygenSaturationRecord::class),
      HealthPermission.getReadPermission(SleepSessionRecord::class),
    )

  private val client: HealthConnectClient? by lazy {
    if (HealthConnectClient.getSdkStatus(appContext) == HealthConnectClient.SDK_AVAILABLE) {
      HealthConnectClient.getOrCreate(appContext)
    } else {
      null
    }
  }

  suspend fun checkAvailabilityAndPermissions(): HealthConnectStatus {
    val sdkStatus = HealthConnectClient.getSdkStatus(appContext)
    if (sdkStatus != HealthConnectClient.SDK_AVAILABLE) {
      val needsInstall = sdkStatus == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED
      return HealthConnectStatus(
        isAvailable = false,
        isInstalled = !needsInstall,
        hasPermissions = false,
        unsupportedNotes = listOf("Health Connect provider is not active on this device."),
        statusMessage =
          if (needsInstall) {
            "Health Connect update or installation required to sync smartwatch data."
          } else {
            "Health Connect is unavailable on this Android version."
          },
      )
    }

    val hc = client ?: return HealthConnectStatus(isAvailable = false, statusMessage = "Client unavailable")
    return try {
      val granted = hc.permissionController.getGrantedPermissions()
      val coreGranted =
        granted.contains(HealthPermission.getReadPermission(HeartRateRecord::class)) ||
          granted.contains(HealthPermission.getReadPermission(StepsRecord::class))
      HealthConnectStatus(
        isAvailable = true,
        isInstalled = true,
        hasPermissions = coreGranted,
        statusMessage =
          if (coreGranted) "Health Connect permissions granted" else "Grant Health Connect permission to sync smartwatch readings",
      )
    } catch (e: Exception) {
      Log.w(TAG, "Failed checking Health Connect permissions: ${e.message}")
      HealthConnectStatus(
        isAvailable = true,
        hasPermissions = false,
        statusMessage = "Unable to verify Health Connect permissions: ${e.localizedMessage}",
      )
    }
  }

  suspend fun readLatestSmartwatchSnapshot(athleteId: String): Pair<HealthConnectStatus, HealthMetricRecord?> {
    val status = checkAvailabilityAndPermissions()
    if (!status.isAvailable || !status.hasPermissions) {
      return status to null
    }
    val hc = client ?: return status to null

    val now = Instant.now()
    val start24h = now.minus(24, ChronoUnit.HOURS)
    val filter = TimeRangeFilter.between(start24h, now)
    val unsupported = mutableListOf<String>()

    var latestHr = 0
    var avgHr = 0
    var maxHr = 0
    var sourceLabel = "Health Connect"
    var latestTimeMs = now.toEpochMilli()

    try {
      val hrResponse = hc.readRecords(ReadRecordsRequest(HeartRateRecord::class, filter))
      val allSamples = hrResponse.records.flatMap { it.samples }
      if (allSamples.isNotEmpty()) {
        latestHr = allSamples.last().beatsPerMinute.toInt()
        avgHr = allSamples.map { it.beatsPerMinute }.average().toInt()
        maxHr = allSamples.maxOf { it.beatsPerMinute }.toInt()
        latestTimeMs = allSamples.last().time.toEpochMilli()
        sourceLabel = hrResponse.records.lastOrNull()?.metadata?.dataOrigin?.packageName
          ?.substringAfterLast('.')
          ?.replaceFirstChar { it.uppercase() } ?: "Smartwatch"
      } else {
        unsupported.add("Heart rate: No recent readings")
      }
    } catch (e: Exception) {
      unsupported.add("Heart rate unavailable (${e.javaClass.simpleName})")
    }

    var steps = 0L
    var calories = 0
    var distanceMeters = 0.0
    try {
      val agg =
        hc.aggregate(
          AggregateRequest(
            metrics =
              setOf(
                StepsRecord.COUNT_TOTAL,
                ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                DistanceRecord.DISTANCE_TOTAL,
              ),
            timeRangeFilter = filter,
          )
        )
      steps = agg[StepsRecord.COUNT_TOTAL] ?: 0L
      calories = agg[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories?.toInt() ?: 0
      distanceMeters = agg[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0.0
    } catch (_: Exception) {
      unsupported.add("Aggregated steps/calories/distance unavailable")
    }

    var exerciseSec = 0L
    try {
      val exercises = hc.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, filter))
      exerciseSec =
        exercises.records.sumOf { Duration.between(it.startTime, it.endTime).seconds.coerceAtLeast(0L) }
    } catch (_: Exception) {
      unsupported.add("Exercise duration unavailable")
    }

    var spo2: Int? = null
    try {
      val spo2Res = hc.readRecords(ReadRecordsRequest(OxygenSaturationRecord::class, filter))
      spo2 = spo2Res.records.lastOrNull()?.percentage?.value?.toInt()
      if (spo2 == null) unsupported.add("Blood oxygen (SpO2): Not reported by watch")
    } catch (_: Exception) {
      unsupported.add("Blood oxygen (SpO2) permission not granted or unsupported")
    }

    var sleepMinutes: Int? = null
    try {
      val sleepRes = hc.readRecords(ReadRecordsRequest(SleepSessionRecord::class, filter))
      val totalMin =
        sleepRes.records.sumOf { Duration.between(it.startTime, it.endTime).toMinutes().toInt() }
      sleepMinutes = totalMin.takeIf { it > 0 }
      if (sleepMinutes == null) unsupported.add("Sleep: No session in last 24h")
    } catch (_: Exception) {
      unsupported.add("Sleep data permission not granted or unsupported")
    }

    // Deterministic bucket key prevents duplicate records for the same reading timestamp + source (FR-05)
    val dedupId = "hc_${athleteId}_${latestTimeMs / 60_000L}"
    val hasAnyData = latestHr > 0 || steps > 0L || calories > 0 || distanceMeters > 0.0 || spo2 != null || sleepMinutes != null
    val record =
      if (hasAnyData) {
        HealthMetricRecord(
          id = dedupId,
          athleteId = athleteId,
          raceId = null,
          heartRateBpm = latestHr,
          avgHeartRateBpm = avgHr,
          maxHeartRateBpm = maxHr,
          steps = steps,
          activeCalories = calories,
          exerciseDurationSec = exerciseSec,
          distanceMeters = distanceMeters,
          spo2Percent = spo2,
          sleepMinutes = sleepMinutes,
          source = sourceLabel,
          recordedAtEpochMs = latestTimeMs,
          synced = false,
        )
      } else {
        null
      }

    val updatedStatus =
      status.copy(
        unsupportedNotes = unsupported,
        statusMessage =
          if (record != null) {
            "Synced from $sourceLabel"
          } else {
            "Connected to Health Connect — no smartwatch readings recorded in last 24h"
          },
      )
    return updatedStatus to record
  }

  companion object {
    private const val TAG = "HealthConnectManager"
  }
}
