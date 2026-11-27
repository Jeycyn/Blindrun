package com.example.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

interface SupabaseAuthService {
  @POST("auth/v1/signup")
  suspend fun signUp(@Body body: SupabaseSignUpRequest): Response<SupabaseAuthResponse>

  @POST("auth/v1/token?grant_type=password")
  suspend fun signInWithPassword(@Body body: SupabaseLoginRequest): Response<SupabaseAuthResponse>

  @POST("auth/v1/token?grant_type=refresh_token")
  suspend fun refreshToken(@Body body: SupabaseRefreshRequest): Response<SupabaseAuthResponse>

  @POST("auth/v1/recover")
  suspend fun recoverPassword(@Body body: SupabaseRecoverRequest): Response<Unit>

  @POST("auth/v1/logout")
  suspend fun logout(@Header("Authorization") bearer: String): Response<Unit>

  @GET("auth/v1/user")
  suspend fun getUser(@Header("Authorization") bearer: String): Response<SupabaseUserDto>
}

interface SupabaseRestService {
  @GET("rest/v1/profiles")
  suspend fun getProfiles(
    @Header("Authorization") bearer: String,
    @Query("select") select: String = "*",
    @Query("id") idFilter: String? = null,
  ): Response<List<ProfileDto>>

  @Headers("Prefer: resolution=merge-duplicates,return=representation")
  @POST("rest/v1/profiles")
  suspend fun upsertProfile(
    @Header("Authorization") bearer: String,
    @Body profile: ProfileDto,
  ): Response<List<ProfileDto>>

  @GET("rest/v1/devices")
  suspend fun getDevices(
    @Header("Authorization") bearer: String,
    @Query("select") select: String = "*",
    @Query("serial_code") serialFilter: String? = null,
  ): Response<List<DeviceDto>>

  @Headers("Prefer: resolution=merge-duplicates,return=representation")
  @POST("rest/v1/devices")
  suspend fun upsertDevices(
    @Header("Authorization") bearer: String,
    @Body devices: List<DeviceDto>,
  ): Response<List<DeviceDto>>

  @GET("rest/v1/races")
  suspend fun getRaces(
    @Header("Authorization") bearer: String,
    @Query("select") select: String = "*",
    @Query("order") order: String = "created_at.desc",
  ): Response<List<RaceDto>>

  @Headers("Prefer: resolution=merge-duplicates,return=representation")
  @POST("rest/v1/races")
  suspend fun upsertRaces(
    @Header("Authorization") bearer: String,
    @Body races: List<RaceDto>,
  ): Response<List<RaceDto>>

  @Headers("Prefer: resolution=ignore-duplicates,return=minimal")
  @POST("rest/v1/gps_trackpoints")
  suspend fun upsertTrackPoints(
    @Header("Authorization") bearer: String,
    @Body points: List<GpsTrackPointDto>,
  ): Response<Unit>

  @Headers("Prefer: resolution=ignore-duplicates,return=minimal")
  @POST("rest/v1/obstacle_events")
  suspend fun upsertObstacleEvents(
    @Header("Authorization") bearer: String,
    @Body events: List<ObstacleEventDto>,
  ): Response<Unit>

  @Headers("Prefer: resolution=ignore-duplicates,return=minimal")
  @POST("rest/v1/health_records")
  suspend fun upsertHealthRecords(
    @Header("Authorization") bearer: String,
    @Body records: List<HealthRecordDto>,
  ): Response<Unit>

  @GET("rest/v1/health_records")
  suspend fun getHealthRecords(
    @Header("Authorization") bearer: String,
    @Query("select") select: String = "*",
    @Query("order") order: String = "recorded_at.desc",
    @Query("limit") limit: Int = 50,
  ): Response<List<HealthRecordDto>>

  @Headers("Prefer: resolution=merge-duplicates,return=minimal")
  @POST("rest/v1/alerts")
  suspend fun upsertAlerts(
    @Header("Authorization") bearer: String,
    @Body alerts: List<AlertDto>,
  ): Response<Unit>

  @GET("rest/v1/coach_athlete_assignments")
  suspend fun getAssignments(
    @Header("Authorization") bearer: String,
    @Query("select") select: String = "*",
  ): Response<List<CoachAssignmentDto>>

  @Headers("Prefer: resolution=merge-duplicates,return=representation")
  @POST("rest/v1/coach_athlete_assignments")
  suspend fun upsertAssignment(
    @Header("Authorization") bearer: String,
    @Body assignment: CoachAssignmentDto,
  ): Response<List<CoachAssignmentDto>>

  @DELETE("rest/v1/coach_athlete_assignments")
  suspend fun deleteAssignment(
    @Header("Authorization") bearer: String,
    @Query("id") idFilter: String,
  ): Response<Unit>

  @Headers("Prefer: return=minimal")
  @POST("rest/v1/admin_audit_logs")
  suspend fun insertAuditLog(
    @Header("Authorization") bearer: String,
    @Body log: AdminAuditLogDto,
  ): Response<Unit>
}
