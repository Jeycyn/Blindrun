package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.AccessibilityMode
import com.example.data.model.AdminAuditLog
import com.example.data.model.AppAlert
import com.example.data.model.CoachAssignment
import com.example.data.model.GpsTrackPoint
import com.example.data.model.HealthMetricRecord
import com.example.data.model.ObstacleDirection
import com.example.data.model.ObstacleEvent
import com.example.data.model.ObstacleSeverity
import com.example.data.model.RaceSession
import com.example.data.model.RaceStatus
import com.example.data.model.RouteWaypoint
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.model.WearableDevice
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val routeJson = Json { ignoreUnknownKeys = true }

@Entity(tableName = "user_profiles")
data class UserProfileEntity(
  @PrimaryKey val id: String,
  val email: String,
  val fullName: String,
  val role: String,
  val accessibilityMode: String,
  val phone: String,
  val emergencyContactName: String,
  val emergencyContactPhone: String,
  val hapticIntensity: Int,
  val audioCuesEnabled: Boolean,
  val consentedCoachSharing: Boolean,
  val onboardingCompleted: Boolean,
  val synced: Boolean,
)

@Entity(tableName = "wearable_devices")
data class WearableDeviceEntity(
  @PrimaryKey val id: String,
  val serialCode: String,
  val ownerAthleteId: String,
  val name: String,
  val macAddress: String,
  val firmwareVersion: String,
  val batteryPercent: Int,
  val isConnected: Boolean,
  val frontMotorOk: Boolean,
  val rearMotorOk: Boolean,
  val leftMotorOk: Boolean,
  val rightMotorOk: Boolean,
  val lidarStatus: String,
  val lastSeenEpochMs: Long,
  val synced: Boolean,
)

@Entity(tableName = "races")
data class RaceEntity(
  @PrimaryKey val id: String,
  val athleteId: String,
  val createdBy: String,
  val name: String,
  val raceType: String,
  val totalDistanceMeters: Double,
  val coveredDistanceMeters: Double,
  val startLocationName: String,
  val finishLocationName: String,
  val waypointsJson: String,
  val status: String,
  val avgPaceSecPerKm: Double,
  val avgSpeedKmh: Double,
  val elapsedSeconds: Long,
  val avgHeartRate: Int,
  val maxHeartRate: Int,
  val activeCalories: Int,
  val startedAtEpochMs: Long?,
  val finishedAtEpochMs: Long?,
  val createdAtEpochMs: Long,
  val synced: Boolean,
)

@Entity(tableName = "gps_trackpoints")
data class GpsTrackPointEntity(
  @PrimaryKey val id: String,
  val raceId: String,
  val athleteId: String,
  val latitude: Double,
  val longitude: Double,
  val accuracyMeters: Float,
  val speedMps: Float,
  val distanceFromStartMeters: Double,
  val deviationMeters: Double,
  val recordedAtEpochMs: Long,
  val synced: Boolean,
)

@Entity(tableName = "obstacle_events")
data class ObstacleEventEntity(
  @PrimaryKey val id: String,
  val raceId: String?,
  val athleteId: String,
  val deviceSerial: String,
  val direction: String,
  val severity: String,
  val distanceCm: Int,
  val recordedAtEpochMs: Long,
  val synced: Boolean,
)

@Entity(tableName = "health_records")
data class HealthRecordEntity(
  @PrimaryKey val id: String,
  val athleteId: String,
  val raceId: String?,
  val heartRateBpm: Int,
  val avgHeartRateBpm: Int,
  val maxHeartRateBpm: Int,
  val steps: Long,
  val activeCalories: Int,
  val exerciseDurationSec: Long,
  val distanceMeters: Double,
  val spo2Percent: Int?,
  val sleepMinutes: Int?,
  val source: String,
  val recordedAtEpochMs: Long,
  val synced: Boolean,
)

@Entity(tableName = "alerts")
data class AlertEntity(
  @PrimaryKey val id: String,
  val athleteId: String,
  val alertType: String,
  val title: String,
  val message: String,
  val severity: String,
  val acknowledged: Boolean,
  val createdAtEpochMs: Long,
  val synced: Boolean,
)

@Entity(tableName = "coach_assignments")
data class CoachAssignmentEntity(
  @PrimaryKey val id: String,
  val coachId: String,
  val athleteId: String,
  val coachName: String,
  val athleteName: String,
  val createdAtEpochMs: Long,
)

@Entity(tableName = "admin_audit_logs")
data class AdminAuditLogEntity(
  @PrimaryKey val id: String,
  val adminId: String,
  val action: String,
  val targetEntity: String,
  val details: String,
  val createdAtEpochMs: Long,
)

fun UserProfileEntity.toDomain() =
  UserProfile(
    id = id,
    email = email,
    fullName = fullName,
    role = UserRole.fromApi(role),
    accessibilityMode = AccessibilityMode.fromApi(accessibilityMode),
    phone = phone,
    emergencyContactName = emergencyContactName,
    emergencyContactPhone = emergencyContactPhone,
    hapticIntensity = hapticIntensity,
    audioCuesEnabled = audioCuesEnabled,
    consentedCoachSharing = consentedCoachSharing,
    onboardingCompleted = onboardingCompleted,
    synced = synced,
  )

fun UserProfile.toEntity() =
  UserProfileEntity(
    id = id,
    email = email,
    fullName = fullName,
    role = role.apiValue,
    accessibilityMode = accessibilityMode.apiValue,
    phone = phone,
    emergencyContactName = emergencyContactName,
    emergencyContactPhone = emergencyContactPhone,
    hapticIntensity = hapticIntensity,
    audioCuesEnabled = audioCuesEnabled,
    consentedCoachSharing = consentedCoachSharing,
    onboardingCompleted = onboardingCompleted,
    synced = synced,
  )

fun WearableDeviceEntity.toDomain() =
  WearableDevice(
    id = id,
    serialCode = serialCode,
    ownerAthleteId = ownerAthleteId,
    name = name,
    macAddress = macAddress,
    firmwareVersion = firmwareVersion,
    batteryPercent = batteryPercent,
    isConnected = isConnected,
    frontMotorOk = frontMotorOk,
    rearMotorOk = rearMotorOk,
    leftMotorOk = leftMotorOk,
    rightMotorOk = rightMotorOk,
    lidarStatus = lidarStatus,
    lastSeenEpochMs = lastSeenEpochMs,
    synced = synced,
  )

fun WearableDevice.toEntity() =
  WearableDeviceEntity(
    id = id,
    serialCode = serialCode,
    ownerAthleteId = ownerAthleteId,
    name = name,
    macAddress = macAddress,
    firmwareVersion = firmwareVersion,
    batteryPercent = batteryPercent,
    isConnected = isConnected,
    frontMotorOk = frontMotorOk,
    rearMotorOk = rearMotorOk,
    leftMotorOk = leftMotorOk,
    rightMotorOk = rightMotorOk,
    lidarStatus = lidarStatus,
    lastSeenEpochMs = lastSeenEpochMs,
    synced = synced,
  )

fun RaceEntity.toDomain(): RaceSession {
  val parsedWaypoints =
    try {
      routeJson.decodeFromString<List<RouteWaypoint>>(waypointsJson)
    } catch (_: Exception) {
      emptyList()
    }
  return RaceSession(
    id = id,
    athleteId = athleteId,
    createdBy = createdBy,
    name = name,
    raceType = raceType,
    totalDistanceMeters = totalDistanceMeters,
    coveredDistanceMeters = coveredDistanceMeters,
    startLocationName = startLocationName,
    finishLocationName = finishLocationName,
    waypoints = parsedWaypoints,
    status = RaceStatus.fromApi(status),
    avgPaceSecPerKm = avgPaceSecPerKm,
    avgSpeedKmh = avgSpeedKmh,
    elapsedSeconds = elapsedSeconds,
    avgHeartRate = avgHeartRate,
    maxHeartRate = maxHeartRate,
    activeCalories = activeCalories,
    startedAtEpochMs = startedAtEpochMs,
    finishedAtEpochMs = finishedAtEpochMs,
    createdAtEpochMs = createdAtEpochMs,
    synced = synced,
  )
}

fun RaceSession.toEntity() =
  RaceEntity(
    id = id,
    athleteId = athleteId,
    createdBy = createdBy,
    name = name,
    raceType = raceType,
    totalDistanceMeters = totalDistanceMeters,
    coveredDistanceMeters = coveredDistanceMeters,
    startLocationName = startLocationName,
    finishLocationName = finishLocationName,
    waypointsJson = routeJson.encodeToString(waypoints),
    status = status.apiValue,
    avgPaceSecPerKm = avgPaceSecPerKm,
    avgSpeedKmh = avgSpeedKmh,
    elapsedSeconds = elapsedSeconds,
    avgHeartRate = avgHeartRate,
    maxHeartRate = maxHeartRate,
    activeCalories = activeCalories,
    startedAtEpochMs = startedAtEpochMs,
    finishedAtEpochMs = finishedAtEpochMs,
    createdAtEpochMs = createdAtEpochMs,
    synced = synced,
  )

fun GpsTrackPointEntity.toDomain() =
  GpsTrackPoint(
    id = id,
    raceId = raceId,
    athleteId = athleteId,
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = accuracyMeters,
    speedMps = speedMps,
    distanceFromStartMeters = distanceFromStartMeters,
    deviationMeters = deviationMeters,
    recordedAtEpochMs = recordedAtEpochMs,
    synced = synced,
  )

fun GpsTrackPoint.toEntity() =
  GpsTrackPointEntity(
    id = id,
    raceId = raceId,
    athleteId = athleteId,
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = accuracyMeters,
    speedMps = speedMps,
    distanceFromStartMeters = distanceFromStartMeters,
    deviationMeters = deviationMeters,
    recordedAtEpochMs = recordedAtEpochMs,
    synced = synced,
  )

fun ObstacleEventEntity.toDomain() =
  ObstacleEvent(
    id = id,
    raceId = raceId,
    athleteId = athleteId,
    deviceSerial = deviceSerial,
    direction = ObstacleDirection.fromApi(direction),
    severity = ObstacleSeverity.fromApi(severity),
    distanceCm = distanceCm,
    recordedAtEpochMs = recordedAtEpochMs,
    synced = synced,
  )

fun ObstacleEvent.toEntity() =
  ObstacleEventEntity(
    id = id,
    raceId = raceId,
    athleteId = athleteId,
    deviceSerial = deviceSerial,
    direction = direction.apiValue,
    severity = severity.apiValue,
    distanceCm = distanceCm,
    recordedAtEpochMs = recordedAtEpochMs,
    synced = synced,
  )

fun HealthRecordEntity.toDomain() =
  HealthMetricRecord(
    id = id,
    athleteId = athleteId,
    raceId = raceId,
    heartRateBpm = heartRateBpm,
    avgHeartRateBpm = avgHeartRateBpm,
    maxHeartRateBpm = maxHeartRateBpm,
    steps = steps,
    activeCalories = activeCalories,
    exerciseDurationSec = exerciseDurationSec,
    distanceMeters = distanceMeters,
    spo2Percent = spo2Percent,
    sleepMinutes = sleepMinutes,
    source = source,
    recordedAtEpochMs = recordedAtEpochMs,
    synced = synced,
  )

fun HealthMetricRecord.toEntity() =
  HealthRecordEntity(
    id = id,
    athleteId = athleteId,
    raceId = raceId,
    heartRateBpm = heartRateBpm,
    avgHeartRateBpm = avgHeartRateBpm,
    maxHeartRateBpm = maxHeartRateBpm,
    steps = steps,
    activeCalories = activeCalories,
    exerciseDurationSec = exerciseDurationSec,
    distanceMeters = distanceMeters,
    spo2Percent = spo2Percent,
    sleepMinutes = sleepMinutes,
    source = source,
    recordedAtEpochMs = recordedAtEpochMs,
    synced = synced,
  )

fun AlertEntity.toDomain() =
  AppAlert(
    id = id,
    athleteId = athleteId,
    alertType = alertType,
    title = title,
    message = message,
    severity = severity,
    acknowledged = acknowledged,
    createdAtEpochMs = createdAtEpochMs,
    synced = synced,
  )

fun AppAlert.toEntity() =
  AlertEntity(
    id = id,
    athleteId = athleteId,
    alertType = alertType,
    title = title,
    message = message,
    severity = severity,
    acknowledged = acknowledged,
    createdAtEpochMs = createdAtEpochMs,
    synced = synced,
  )

fun CoachAssignmentEntity.toDomain() =
  CoachAssignment(
    id = id,
    coachId = coachId,
    athleteId = athleteId,
    coachName = coachName,
    athleteName = athleteName,
    createdAtEpochMs = createdAtEpochMs,
  )

fun CoachAssignment.toEntity() =
  CoachAssignmentEntity(
    id = id,
    coachId = coachId,
    athleteId = athleteId,
    coachName = coachName,
    athleteName = athleteName,
    createdAtEpochMs = createdAtEpochMs,
  )

fun AdminAuditLogEntity.toDomain() =
  AdminAuditLog(
    id = id,
    adminId = adminId,
    action = action,
    targetEntity = targetEntity,
    details = details,
    createdAtEpochMs = createdAtEpochMs,
  )

fun AdminAuditLog.toEntity() =
  AdminAuditLogEntity(
    id = id,
    adminId = adminId,
    action = action,
    targetEntity = targetEntity,
    details = details,
    createdAtEpochMs = createdAtEpochMs,
  )
