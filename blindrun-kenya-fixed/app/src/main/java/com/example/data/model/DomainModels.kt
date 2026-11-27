package com.example.data.model

import kotlinx.serialization.Serializable

enum class UserRole(val apiValue: String, val label: String) {
  ATHLETE("athlete", "Athlete"),
  COACH("coach", "Coach"),
  ADMIN("admin", "Administrator");

  companion object {
    fun fromApi(raw: String?): UserRole =
      entries.firstOrNull { it.apiValue.equals(raw, ignoreCase = true) } ?: ATHLETE
  }
}

enum class AccessibilityMode(val apiValue: String, val label: String, val description: String) {
  VISUALLY_IMPAIRED(
    "visually_impaired",
    "Visually Impaired",
    "High-contrast large metrics, screen-reader labels, spoken + haptic cues",
  ),
  DEAFBLIND(
    "deafblind",
    "Deafblind",
    "Non-audio directional haptic waveforms, Braille/tactile alerts, vibration confirmations",
  ),
  GUIDE_ASSISTED(
    "guide_assisted",
    "Guide-Assisted",
    "Shared pace, route deviation warnings, and synchronized belt telemetry",
  );

  companion object {
    fun fromApi(raw: String?): AccessibilityMode =
      entries.firstOrNull { it.apiValue.equals(raw, ignoreCase = true) } ?: VISUALLY_IMPAIRED
  }
}

enum class RaceStatus(val apiValue: String, val label: String) {
  CONFIGURED("configured", "Configured"),
  RUNNING("running", "Running"),
  PAUSED("paused", "Paused"),
  COMPLETED("completed", "Completed");

  companion object {
    fun fromApi(raw: String?): RaceStatus =
      entries.firstOrNull { it.apiValue.equals(raw, ignoreCase = true) } ?: CONFIGURED
  }
}

enum class ObstacleDirection(val apiValue: String, val label: String, val motorChannel: Int) {
  FRONT("front", "Front", 1),
  BACK("back", "Back", 2),
  LEFT("left", "Left", 3),
  RIGHT("right", "Right", 4);

  companion object {
    fun fromApi(raw: String?): ObstacleDirection =
      entries.firstOrNull { it.apiValue.equals(raw, ignoreCase = true) } ?: FRONT
  }
}

enum class ObstacleSeverity(val apiValue: String, val label: String) {
  LOW("low", "Low"),
  MEDIUM("medium", "Medium"),
  HIGH("high", "Critical");

  companion object {
    fun fromApi(raw: String?): ObstacleSeverity =
      entries.firstOrNull { it.apiValue.equals(raw, ignoreCase = true) } ?: MEDIUM
  }
}

enum class SyncState {
  SYNCED,
  SYNCING,
  QUEUED_OFFLINE,
  BACKEND_UNCONFIGURED,
  ERROR,
}

@Serializable
data class RouteWaypoint(
  val latitude: Double,
  val longitude: Double,
  val elevationMeters: Double = 0.0,
)

data class UserProfile(
  val id: String,
  val email: String,
  val fullName: String,
  val role: UserRole,
  val accessibilityMode: AccessibilityMode,
  val phone: String = "",
  val emergencyContactName: String = "",
  val emergencyContactPhone: String = "",
  val hapticIntensity: Int = 85,
  val audioCuesEnabled: Boolean = true,
  val consentedCoachSharing: Boolean = true,
  val onboardingCompleted: Boolean = false,
  val synced: Boolean = true,
)

data class WearableDevice(
  val id: String,
  val serialCode: String,
  val ownerAthleteId: String,
  val name: String,
  val macAddress: String,
  val firmwareVersion: String = "1.0.0",
  val batteryPercent: Int = 100,
  val isConnected: Boolean = false,
  val frontMotorOk: Boolean = true,
  val rearMotorOk: Boolean = true,
  val leftMotorOk: Boolean = true,
  val rightMotorOk: Boolean = true,
  val lidarStatus: String = "Ready",
  val lastSeenEpochMs: Long = System.currentTimeMillis(),
  val synced: Boolean = false,
)

data class RaceSession(
  val id: String,
  val athleteId: String,
  val createdBy: String,
  val name: String,
  val raceType: String,
  val totalDistanceMeters: Double,
  val coveredDistanceMeters: Double = 0.0,
  val startLocationName: String = "",
  val finishLocationName: String = "",
  val waypoints: List<RouteWaypoint> = emptyList(),
  val status: RaceStatus = RaceStatus.CONFIGURED,
  val avgPaceSecPerKm: Double = 0.0,
  val avgSpeedKmh: Double = 0.0,
  val elapsedSeconds: Long = 0L,
  val avgHeartRate: Int = 0,
  val maxHeartRate: Int = 0,
  val activeCalories: Int = 0,
  val startedAtEpochMs: Long? = null,
  val finishedAtEpochMs: Long? = null,
  val createdAtEpochMs: Long = System.currentTimeMillis(),
  val synced: Boolean = false,
) {
  // FR-07: Remaining distance Dr = Dt - Dc (clamped at 0.0)
  val remainingDistanceMeters: Double
    get() = (totalDistanceMeters - coveredDistanceMeters).coerceAtLeast(0.0)

  val progressFraction: Float
    get() =
      if (totalDistanceMeters <= 0.0) 0f
      else (coveredDistanceMeters / totalDistanceMeters).toFloat().coerceIn(0f, 1f)
}

data class GpsTrackPoint(
  val id: String,
  val raceId: String,
  val athleteId: String,
  val latitude: Double,
  val longitude: Double,
  val accuracyMeters: Float,
  val speedMps: Float,
  val distanceFromStartMeters: Double,
  val deviationMeters: Double,
  val recordedAtEpochMs: Long,
  val synced: Boolean = false,
)

data class ObstacleEvent(
  val id: String,
  val raceId: String?,
  val athleteId: String,
  val deviceSerial: String,
  val direction: ObstacleDirection,
  val severity: ObstacleSeverity,
  val distanceCm: Int,
  val recordedAtEpochMs: Long,
  val synced: Boolean = false,
)

data class HealthMetricRecord(
  val id: String,
  val athleteId: String,
  val raceId: String?,
  val heartRateBpm: Int,
  val avgHeartRateBpm: Int,
  val maxHeartRateBpm: Int,
  val steps: Long,
  val activeCalories: Int,
  val exerciseDurationSec: Long,
  val distanceMeters: Double,
  val spo2Percent: Int? = null,
  val sleepMinutes: Int? = null,
  val source: String,
  val recordedAtEpochMs: Long,
  val synced: Boolean = false,
)

data class AppAlert(
  val id: String,
  val athleteId: String,
  val alertType: String,
  val title: String,
  val message: String,
  val severity: String,
  val acknowledged: Boolean = false,
  val createdAtEpochMs: Long = System.currentTimeMillis(),
  val synced: Boolean = false,
)

data class CoachAssignment(
  val id: String,
  val coachId: String,
  val athleteId: String,
  val coachName: String = "",
  val athleteName: String = "",
  val createdAtEpochMs: Long = System.currentTimeMillis(),
)

data class AdminAuditLog(
  val id: String,
  val adminId: String,
  val action: String,
  val targetEntity: String,
  val details: String,
  val createdAtEpochMs: Long = System.currentTimeMillis(),
)
