package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SupabaseSignUpRequest(
  val email: String,
  val password: String,
  val data: Map<String, String> = emptyMap(),
)

@JsonClass(generateAdapter = true)
data class SupabaseLoginRequest(
  val email: String,
  val password: String,
)

@JsonClass(generateAdapter = true)
data class SupabaseRefreshRequest(
  @Json(name = "refresh_token") val refreshToken: String,
)

@JsonClass(generateAdapter = true)
data class SupabaseRecoverRequest(
  val email: String,
)

@JsonClass(generateAdapter = true)
data class SupabaseUserDto(
  val id: String = "",
  val email: String? = null,
  @Json(name = "user_metadata") val userMetadata: Map<String, Any?>? = null,
)

@JsonClass(generateAdapter = true)
data class SupabaseSessionDto(
  @Json(name = "access_token") val accessToken: String? = null,
  @Json(name = "refresh_token") val refreshToken: String? = null,
  @Json(name = "expires_in") val expiresIn: Long? = null,
  @Json(name = "expires_at") val expiresAt: Long? = null,
)

@JsonClass(generateAdapter = true)
data class SupabaseAuthResponse(
  @Json(name = "access_token") val accessToken: String? = null,
  @Json(name = "refresh_token") val refreshToken: String? = null,
  @Json(name = "expires_in") val expiresIn: Long? = null,
  @Json(name = "expires_at") val expiresAt: Long? = null,
  val user: SupabaseUserDto? = null,
  val session: SupabaseSessionDto? = null,
  // GoTrue returns user fields at top-level when email confirmation is enabled
  val id: String? = null,
  val email: String? = null,
  @Json(name = "confirmation_sent_at") val confirmationSentAt: String? = null,
  @Json(name = "user_metadata") val userMetadata: Map<String, Any?>? = null,
  val identities: List<Map<String, Any?>>? = null,
  val msg: String? = null,
  val message: String? = null,
  val error: String? = null,
  @Json(name = "error_description") val errorDescription: String? = null,
)

@JsonClass(generateAdapter = true)
data class ProfileDto(
  val id: String,
  val email: String,
  @Json(name = "full_name") val fullName: String,
  val role: String = "athlete",
  @Json(name = "accessibility_mode") val accessibilityMode: String = "visually_impaired",
  val phone: String = "",
  @Json(name = "emergency_contact_name") val emergencyContactName: String = "",
  @Json(name = "emergency_contact_phone") val emergencyContactPhone: String = "",
  @Json(name = "haptic_intensity") val hapticIntensity: Int = 85,
  @Json(name = "audio_cues_enabled") val audioCuesEnabled: Boolean = true,
  @Json(name = "consented_coach_sharing") val consentedCoachSharing: Boolean = true,
)

@JsonClass(generateAdapter = true)
data class CoachAssignmentDto(
  val id: String? = null,
  @Json(name = "coach_id") val coachId: String,
  @Json(name = "athlete_id") val athleteId: String,
  @Json(name = "assigned_by") val assignedBy: String? = null,
)

@JsonClass(generateAdapter = true)
data class DeviceDto(
  val id: String,
  @Json(name = "serial_code") val serialCode: String,
  @Json(name = "owner_athlete_id") val ownerAthleteId: String?,
  val name: String,
  @Json(name = "mac_address") val macAddress: String,
  @Json(name = "firmware_version") val firmwareVersion: String = "1.0.0",
  @Json(name = "battery_percent") val batteryPercent: Int = 100,
  val status: String = "registered",
)

@JsonClass(generateAdapter = true)
data class RaceDto(
  val id: String,
  @Json(name = "athlete_id") val athleteId: String,
  @Json(name = "created_by") val createdBy: String,
  val name: String,
  @Json(name = "race_type") val raceType: String,
  @Json(name = "total_distance_meters") val totalDistanceMeters: Double,
  @Json(name = "covered_distance_meters") val coveredDistanceMeters: Double,
  @Json(name = "remaining_distance_meters") val remainingDistanceMeters: Double,
  @Json(name = "start_location_name") val startLocationName: String,
  @Json(name = "finish_location_name") val finishLocationName: String,
  @Json(name = "route_waypoints_json") val routeWaypointsJson: List<Map<String, Double>> = emptyList(),
  val status: String,
  @Json(name = "avg_pace_sec_per_km") val avgPaceSecPerKm: Double,
  @Json(name = "avg_speed_kmh") val avgSpeedKmh: Double,
  @Json(name = "elapsed_seconds") val elapsedSeconds: Long,
  @Json(name = "avg_heart_rate") val avgHeartRate: Int,
  @Json(name = "max_heart_rate") val maxHeartRate: Int,
  @Json(name = "active_calories") val activeCalories: Int,
  @Json(name = "started_at") val startedAt: String? = null,
  @Json(name = "finished_at") val finishedAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class GpsTrackPointDto(
  val id: String,
  @Json(name = "race_id") val raceId: String,
  @Json(name = "athlete_id") val athleteId: String,
  val latitude: Double,
  val longitude: Double,
  @Json(name = "accuracy_meters") val accuracyMeters: Float,
  @Json(name = "speed_mps") val speedMps: Float,
  @Json(name = "distance_from_start_meters") val distanceFromStartMeters: Double,
  @Json(name = "deviation_meters") val deviationMeters: Double,
  @Json(name = "recorded_at") val recordedAt: String,
)

@JsonClass(generateAdapter = true)
data class ObstacleEventDto(
  val id: String,
  @Json(name = "race_id") val raceId: String?,
  @Json(name = "athlete_id") val athleteId: String,
  @Json(name = "device_serial") val deviceSerial: String,
  val direction: String,
  val severity: String,
  @Json(name = "distance_cm") val distanceCm: Int,
  @Json(name = "recorded_at") val recordedAt: String,
)

@JsonClass(generateAdapter = true)
data class HealthRecordDto(
  val id: String,
  @Json(name = "athlete_id") val athleteId: String,
  @Json(name = "race_id") val raceId: String?,
  @Json(name = "heart_rate_bpm") val heartRateBpm: Int,
  @Json(name = "avg_heart_rate_bpm") val avgHeartRateBpm: Int,
  @Json(name = "max_heart_rate_bpm") val maxHeartRateBpm: Int,
  val steps: Long,
  @Json(name = "active_calories") val activeCalories: Int,
  @Json(name = "exercise_duration_sec") val exerciseDurationSec: Long,
  @Json(name = "distance_meters") val distanceMeters: Double,
  @Json(name = "spo2_percent") val spo2Percent: Int? = null,
  @Json(name = "sleep_minutes") val sleepMinutes: Int? = null,
  val source: String,
  @Json(name = "recorded_at") val recordedAt: String,
)

@JsonClass(generateAdapter = true)
data class AlertDto(
  val id: String,
  @Json(name = "athlete_id") val athleteId: String,
  @Json(name = "alert_type") val alertType: String,
  val title: String,
  val message: String,
  val severity: String,
  val acknowledged: Boolean,
  @Json(name = "created_at") val createdAt: String,
)

@JsonClass(generateAdapter = true)
data class AdminAuditLogDto(
  val id: String? = null,
  @Json(name = "admin_id") val adminId: String,
  val action: String,
  @Json(name = "target_entity") val targetEntity: String,
  val details: String,
)
