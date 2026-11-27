package com.example.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.room.withTransaction
import com.example.data.local.BlindRunDatabase
import com.example.data.local.SessionPreferences
import com.example.data.local.AlertEntity
import com.example.data.local.RaceEntity
import com.example.data.local.StoredSession
import com.example.data.local.UserProfileEntity
import com.example.data.local.WearableDeviceEntity
import com.example.data.local.toDomain
import com.example.data.local.toEntity
import com.example.data.model.AccessibilityMode
import com.example.data.model.AdminAuditLog
import com.example.data.model.AppAlert
import com.example.data.model.CoachAssignment
import com.example.data.model.GpsTrackPoint
import com.example.data.model.HealthMetricRecord
import com.example.data.model.ObstacleEvent
import com.example.data.model.RaceSession
import com.example.data.model.SyncState
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.model.WearableDevice
import com.example.data.remote.AdminAuditLogDto
import com.example.data.remote.AlertDto
import com.example.data.remote.CoachAssignmentDto
import com.example.data.remote.DeviceDto
import com.example.data.remote.GpsTrackPointDto
import com.example.data.remote.HealthRecordDto
import com.example.data.remote.ObstacleEventDto
import com.example.data.remote.ProfileDto
import com.example.data.remote.RaceDto
import com.example.data.remote.SupabaseClient
import com.example.data.remote.SupabaseConfig
import com.example.data.remote.SupabaseLoginRequest
import com.example.data.remote.SupabaseRecoverRequest
import com.example.data.remote.SupabaseRefreshRequest
import com.example.data.remote.SupabaseSignUpRequest
import com.example.hardware.QrBeltIdentity
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.Response

sealed interface RegistrationOutcome {
  data class SignedIn(val profile: UserProfile) : RegistrationOutcome

  data class PendingEmailVerification(val email: String, val message: String) : RegistrationOutcome
}

class BlindRunRepository(context: Context) {
  private val appContext = context.applicationContext
  private val db = BlindRunDatabase.getInstance(appContext)
  val prefs = SessionPreferences(appContext)
  private val supabase = SupabaseClient()

  private companion object {
    const val SYNC_BATCH_SIZE = 500
  }

  private val _syncState =
    MutableStateFlow(
      if (SupabaseConfig.isConfigured) SyncState.SYNCED else SyncState.BACKEND_UNCONFIGURED
    )
  val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

  private val _lastSyncMessage = MutableStateFlow("")
  val lastSyncMessage: StateFlow<String> = _lastSyncMessage.asStateFlow()

  val sessionFlow: Flow<StoredSession> = prefs.sessionFlow

  fun observeProfile(userId: String): Flow<UserProfile?> =
    db.userProfileDao().observeProfile(userId).map { it?.toDomain() }

  fun observeAllProfiles(): Flow<List<UserProfile>> =
    db.userProfileDao().observeAllProfiles().map { list -> list.map { it.toDomain() } }

  fun observeDevicesForAthlete(athleteId: String): Flow<List<WearableDevice>> =
    db.wearableDeviceDao().observeDevicesForAthlete(athleteId).map { list -> list.map { it.toDomain() } }

  fun observeAllDevices(): Flow<List<WearableDevice>> =
    db.wearableDeviceDao().observeAllDevices().map { list -> list.map { it.toDomain() } }

  fun observeRacesForAthlete(athleteId: String): Flow<List<RaceSession>> =
    db.raceDao().observeRacesForAthlete(athleteId).map { list -> list.map { it.toDomain() } }

  fun observeAllRaces(): Flow<List<RaceSession>> =
    db.raceDao().observeAllRaces().map { list -> list.map { it.toDomain() } }

  fun observeRace(raceId: String): Flow<RaceSession?> =
    db.raceDao().observeRace(raceId).map { it?.toDomain() }

  fun observeTrackPoints(raceId: String): Flow<List<GpsTrackPoint>> =
    db.gpsTrackPointDao().observeTrackPoints(raceId).map { list -> list.map { it.toDomain() } }

  fun observeObstaclesForAthlete(athleteId: String): Flow<List<ObstacleEvent>> =
    db.obstacleEventDao().observeObstaclesForAthlete(athleteId).map { list -> list.map { it.toDomain() } }

  fun observeHealthRecords(athleteId: String): Flow<List<HealthMetricRecord>> =
    db.healthRecordDao().observeHealthRecords(athleteId).map { list -> list.map { it.toDomain() } }

  fun observeAlertsForAthlete(athleteId: String): Flow<List<AppAlert>> =
    db.alertDao().observeAlertsForAthlete(athleteId).map { list -> list.map { it.toDomain() } }

  fun observeAllAlerts(): Flow<List<AppAlert>> =
    db.alertDao().observeAllAlerts().map { list -> list.map { it.toDomain() } }

  fun observeAssignmentsForCoach(coachId: String): Flow<List<CoachAssignment>> =
    db.coachAssignmentDao().observeAssignmentsForCoach(coachId).map { list -> list.map { it.toDomain() } }

  fun observeAllAssignments(): Flow<List<CoachAssignment>> =
    db.coachAssignmentDao().observeAllAssignments().map { list -> list.map { it.toDomain() } }

  fun observeAuditLogs(): Flow<List<AdminAuditLog>> =
    db.adminAuditLogDao().observeLogs().map { list -> list.map { it.toDomain() } }

  fun observePendingSyncCount(athleteId: String): Flow<Int> =
    combine(
      listOf(
        db.raceDao().observeUnsyncedCount(athleteId),
        db.gpsTrackPointDao().observeUnsyncedCount(athleteId),
        db.obstacleEventDao().observeUnsyncedCount(athleteId),
        db.healthRecordDao().observeUnsyncedCount(athleteId),
        db.alertDao().observeUnsyncedCount(athleteId),
        db.wearableDeviceDao().observeUnsyncedCount(athleteId),
      )
    ) { counts -> counts.sum() }

  suspend fun getRace(raceId: String): RaceSession? = db.raceDao().getRace(raceId)?.toDomain()

  /** A race left "running" by a killed process has no GPS/timer behind it any more. */
  suspend fun pauseStaleRunningRaces() = db.raceDao().pauseRunningRaces()

  private suspend fun hasUnsyncedRecords(): Boolean =
    db.userProfileDao().hasUnsynced() ||
      db.wearableDeviceDao().hasUnsynced() ||
      db.raceDao().hasUnsynced() ||
      db.gpsTrackPointDao().hasUnsynced() ||
      db.obstacleEventDao().hasUnsynced() ||
      db.healthRecordDao().hasUnsynced() ||
      db.alertDao().hasUnsynced()

  fun isSupabaseConfigured(): Boolean = SupabaseConfig.isConfigured

  fun isNetworkAvailable(): Boolean {
    val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    val network = cm?.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
  }

  suspend fun restoreSession(): StoredSession {
    val session = prefs.getSession()
    if (!session.isAuthenticated) return session
    if (session.isOfflineSession || !SupabaseConfig.isConfigured || !isNetworkAvailable()) {
      return session
    }
    val nowSec = System.currentTimeMillis() / 1000L
    if (session.tokenExpiresAtEpochSec > 0 && nowSec >= session.tokenExpiresAtEpochSec - 60) {
      val auth = supabase.authService
      if (auth != null && session.refreshToken.isNotBlank()) {
        try {
          val res = auth.refreshToken(SupabaseRefreshRequest(session.refreshToken))
          val body = res.body()
          if (res.isSuccessful && body?.accessToken != null && body.user != null) {
            val expiresAt = body.expiresAt ?: (nowSec + (body.expiresIn ?: 3600L))
            prefs.saveAuthenticatedSession(
              userId = body.user.id,
              email = body.user.email ?: session.email,
              fullName = session.fullName,
              role = session.role,
              accessToken = body.accessToken,
              refreshToken = body.refreshToken ?: session.refreshToken,
              expiresAtEpochSec = expiresAt,
              isOffline = false,
            )
            return prefs.getSession()
          }
        } catch (_: Exception) {
          // Keep existing local session if network temporarily unavailable
        }
      }
    }
    return session
  }

  private fun extractSupabaseError(response: Response<*>, bodyMsg: String?, fallback: String): String {
    if (!bodyMsg.isNullOrBlank()) return bodyMsg
    val raw = try {
      response.errorBody()?.string()
    } catch (_: Exception) {
      null
    }
    if (!raw.isNullOrBlank()) {
      try {
        val json = JSONObject(raw)
        val parsed =
          listOf("error_description", "msg", "message", "error")
            .firstNotNullOfOrNull { key -> json.optString(key).takeIf { it.isNotBlank() } }
        if (!parsed.isNullOrBlank()) return parsed
      } catch (_: Exception) {
        return raw.take(180)
      }
    }
    return "$fallback (${response.code()})"
  }

  suspend fun signInWithSupabase(email: String, password: String): Result<UserProfile> {
    if (!SupabaseConfig.isConfigured) {
      return Result.failure(
        IllegalStateException(
          "Supabase is not configured yet. Add SUPABASE_URL and SUPABASE_ANON_KEY in AI Studio Secrets, or continue in Offline Mode."
        )
      )
    }
    val auth = supabase.authService ?: return Result.failure(IllegalStateException("Auth client unavailable"))
    return try {
      val response = auth.signInWithPassword(SupabaseLoginRequest(email.trim(), password))
      val body = response.body()
      val accessToken = body?.accessToken ?: body?.session?.accessToken
      val refreshToken = body?.refreshToken ?: body?.session?.refreshToken.orEmpty()
      val userId = body?.user?.id ?: body?.id
      if (!response.isSuccessful || accessToken.isNullOrBlank() || userId.isNullOrBlank()) {
        val msg =
          extractSupabaseError(
            response = response,
            bodyMsg = body?.errorDescription ?: body?.msg ?: body?.message ?: body?.error,
            fallback = "Sign in failed",
          )
        return Result.failure(IllegalStateException(msg))
      }

      val bearer = "Bearer $accessToken"
      val nowSec = System.currentTimeMillis() / 1000L
      val expiresAt =
        body?.expiresAt
          ?: body?.session?.expiresAt
          ?: (nowSec + (body?.expiresIn ?: body?.session?.expiresIn ?: 3600L))

      // Fetch profile from Supabase PostgREST (best-effort in case schema.sql isn't applied yet)
      val remoteProfile =
        try {
          supabase.restService
            ?.getProfiles(bearer = bearer, idFilter = "eq.$userId")
            ?.takeIf { it.isSuccessful }
            ?.body()
            ?.firstOrNull()
        } catch (_: Exception) {
          null
        }

      val localExisting = db.userProfileDao().getProfile(userId)?.toDomain()
      val userEmail = body?.user?.email ?: body?.email ?: email.trim()
      val metaMap = body?.user?.userMetadata ?: body?.userMetadata
      val metaName = (metaMap?.get("full_name") as? String)?.takeIf { it.isNotBlank() } ?: userEmail.substringBefore("@")
      val metaRole = (metaMap?.get("role") as? String) ?: "athlete"

      val profile =
        if (remoteProfile != null && localExisting?.synced != false) {
          UserProfile(
            id = remoteProfile.id,
            email = remoteProfile.email,
            fullName = remoteProfile.fullName,
            role = UserRole.fromApi(remoteProfile.role),
            accessibilityMode = AccessibilityMode.fromApi(remoteProfile.accessibilityMode),
            phone = remoteProfile.phone,
            emergencyContactName = remoteProfile.emergencyContactName,
            emergencyContactPhone = remoteProfile.emergencyContactPhone,
            hapticIntensity = remoteProfile.hapticIntensity,
            audioCuesEnabled = remoteProfile.audioCuesEnabled,
            consentedCoachSharing = remoteProfile.consentedCoachSharing,
            onboardingCompleted = localExisting?.onboardingCompleted ?: true,
            synced = true,
          )
        } else {
          val initial =
            localExisting
              ?: UserProfile(
                id = userId,
                email = userEmail,
                fullName = metaName,
                role = UserRole.fromApi(metaRole).let { if (it == UserRole.ADMIN) UserRole.ATHLETE else it },
                accessibilityMode = AccessibilityMode.VISUALLY_IMPAIRED,
                onboardingCompleted = false,
                synced = false,
              )
          val syncedRemote =
            try {
              supabase.restService
                ?.upsertProfile(
                  bearer,
                  ProfileDto(
                    id = initial.id,
                    email = initial.email,
                    fullName = initial.fullName,
                    role = initial.role.apiValue,
                    accessibilityMode = initial.accessibilityMode.apiValue,
                    phone = initial.phone,
                    emergencyContactName = initial.emergencyContactName,
                    emergencyContactPhone = initial.emergencyContactPhone,
                    hapticIntensity = initial.hapticIntensity,
                    audioCuesEnabled = initial.audioCuesEnabled,
                    consentedCoachSharing = initial.consentedCoachSharing,
                  ),
                )
                ?.isSuccessful == true
            } catch (_: Exception) {
              false
            }
          initial.copy(synced = syncedRemote)
        }

      db.userProfileDao().upsertProfile(profile.toEntity())
      prefs.saveAuthenticatedSession(
        userId = profile.id,
        email = profile.email,
        fullName = profile.fullName,
        role = profile.role.apiValue,
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresAtEpochSec = expiresAt,
        isOffline = false,
      )
      pullCloudData(bearer, profile)
      Result.success(profile)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun registerWithSupabase(
    email: String,
    password: String,
    fullName: String,
    requestedRole: UserRole,
  ): Result<RegistrationOutcome> {
    // Admin accounts are never self-service (the database also enforces this).
    val role = if (requestedRole == UserRole.ADMIN) UserRole.ATHLETE else requestedRole
    if (!SupabaseConfig.isConfigured) {
      return Result.failure(
        IllegalStateException(
          "Supabase is not configured yet. Add SUPABASE_URL and SUPABASE_ANON_KEY in AI Studio Secrets, or continue in Offline Mode."
        )
      )
    }
    val auth = supabase.authService ?: return Result.failure(IllegalStateException("Auth client unavailable"))
    val trimmedEmail = email.trim()
    val trimmedName = fullName.trim()
    return try {
      val response =
        auth.signUp(
          SupabaseSignUpRequest(
            email = trimmedEmail,
            password = password,
            data = mapOf("full_name" to trimmedName, "role" to role.apiValue),
          )
        )
      val body = response.body()
      val userId = body?.user?.id ?: body?.id
      if (!response.isSuccessful || userId.isNullOrBlank()) {
        val msg =
          extractSupabaseError(
            response = response,
            bodyMsg = body?.errorDescription ?: body?.msg ?: body?.message ?: body?.error,
            fallback = "Registration failed",
          )
        return Result.failure(IllegalStateException(msg))
      }

      val token = body?.accessToken ?: body?.session?.accessToken
      if (token.isNullOrBlank()) {
        // Supabase GoTrue returns HTTP 200 with empty identities and no confirmation_sent_at when email already exists
        if (body?.identities != null && body.identities.isEmpty() && body.confirmationSentAt.isNullOrBlank()) {
          return Result.failure(
            IllegalStateException(
              "An account for $trimmedEmail already exists. Please Sign In or use Recover to reset your password."
            )
          )
        }
        return Result.success(
          RegistrationOutcome.PendingEmailVerification(
            email = trimmedEmail,
            message =
              "Account created for $trimmedEmail! Open the verification link sent to your email, then Sign In.",
          )
        )
      }

      val bearer = "Bearer $token"
      val profile =
        UserProfile(
          id = userId,
          email = body?.user?.email ?: body?.email ?: trimmedEmail,
          fullName = trimmedName,
          role = role,
          accessibilityMode = AccessibilityMode.VISUALLY_IMPAIRED,
          onboardingCompleted = false,
          synced = false,
        )

      val syncedRemote =
        try {
          supabase.restService
            ?.upsertProfile(
              bearer,
              ProfileDto(
                id = profile.id,
                email = profile.email,
                fullName = profile.fullName,
                role = profile.role.apiValue,
              ),
            )
            ?.isSuccessful == true
        } catch (_: Exception) {
          false
        }
      val savedProfile = profile.copy(synced = syncedRemote)
      db.userProfileDao().upsertProfile(savedProfile.toEntity())

      val nowSec = System.currentTimeMillis() / 1000L
      val expiresAt =
        body?.expiresAt
          ?: body?.session?.expiresAt
          ?: (nowSec + (body?.expiresIn ?: body?.session?.expiresIn ?: 3600L))
      val refresh = body?.refreshToken ?: body?.session?.refreshToken.orEmpty()
      prefs.saveAuthenticatedSession(
        userId = savedProfile.id,
        email = savedProfile.email,
        fullName = savedProfile.fullName,
        role = savedProfile.role.apiValue,
        accessToken = token,
        refreshToken = refresh,
        expiresAtEpochSec = expiresAt,
        isOffline = false,
      )
      Result.success(RegistrationOutcome.SignedIn(savedProfile))
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun recoverPassword(email: String): Result<Unit> {
    if (!SupabaseConfig.isConfigured) {
      return Result.failure(
        IllegalStateException("Configure SUPABASE_URL & SUPABASE_ANON_KEY in Secrets to send recovery emails.")
      )
    }
    val auth = supabase.authService ?: return Result.failure(IllegalStateException("Auth client unavailable"))
    return try {
      val res = auth.recoverPassword(SupabaseRecoverRequest(email.trim()))
      if (res.isSuccessful) Result.success(Unit)
      else Result.failure(IllegalStateException(extractSupabaseError(res, null, "Password recovery failed")))
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun startOfflineSession(fullName: String, email: String, requestedRole: UserRole): UserProfile {
    val role = if (requestedRole == UserRole.ADMIN) UserRole.ATHLETE else requestedRole
    val cleanEmail = email.trim().ifBlank { "athlete@blindrunkenya.local" }
    val cleanName = fullName.trim().ifBlank { "BlindRun ${role.label}" }
    val localUid = "local-${UUID.nameUUIDFromBytes(cleanEmail.lowercase().toByteArray())}"
    val existing = db.userProfileDao().getProfile(localUid)?.toDomain()
    val profile =
      existing
        ?: UserProfile(
          id = localUid,
          email = cleanEmail,
          fullName = cleanName,
          role = role,
          accessibilityMode = AccessibilityMode.VISUALLY_IMPAIRED,
          onboardingCompleted = false,
          synced = false,
        )
    db.userProfileDao().upsertProfile(profile.toEntity())
    prefs.saveAuthenticatedSession(
      userId = profile.id,
      email = profile.email,
      fullName = profile.fullName,
      role = profile.role.apiValue,
      accessToken = "",
      refreshToken = "",
      expiresAtEpochSec = 0L,
      isOffline = true,
    )
    return profile
  }

  suspend fun signOut() {
    val session = prefs.getSession()
    if (!session.isOfflineSession && session.accessToken.isNotBlank() && SupabaseConfig.isConfigured) {
      // Best effort: push anything still queued before the local copy is wiped.
      try { syncQueuedRecords() } catch (_: Exception) {}
      try {
        supabase.authService?.logout("Bearer ${prefs.getSession().accessToken}")
      } catch (_: Exception) {}
    }
    prefs.clearSession()
    // Shared phones: never leave one person's health data / cached profiles for the next user.
    // If records are still queued (no signal), keep them so no run is lost.
    if (!hasUnsyncedRecords()) {
      withContext(Dispatchers.IO) { db.clearAllTables() }
    }
  }

  suspend fun saveProfile(profile: UserProfile): UserProfile {
    val local = profile.copy(synced = false)
    db.userProfileDao().upsertProfile(local.toEntity())
    val own = prefs.getSession()
    if (profile.id == own.userId) {
      prefs.updateAccessibilitySettings(
        hapticIntensity = profile.hapticIntensity,
        audioCues = profile.audioCuesEnabled,
        lightHud = own.highContrastLightRaceHud,
      )
    }
    val session = restoreSession()
    if (!session.isOfflineSession && session.accessToken.isNotBlank() && SupabaseConfig.isConfigured && isNetworkAvailable()) {
      try {
        val res =
          supabase.restService?.upsertProfile(
            "Bearer ${session.accessToken}",
            ProfileDto(
              id = profile.id,
              email = profile.email,
              fullName = profile.fullName,
              role = profile.role.apiValue,
              accessibilityMode = profile.accessibilityMode.apiValue,
              phone = profile.phone,
              emergencyContactName = profile.emergencyContactName,
              emergencyContactPhone = profile.emergencyContactPhone,
              hapticIntensity = profile.hapticIntensity,
              audioCuesEnabled = profile.audioCuesEnabled,
              consentedCoachSharing = profile.consentedCoachSharing,
            ),
          )
        if (res?.isSuccessful == true) {
          val syncedProfile = profile.copy(synced = true)
          db.userProfileDao().upsertProfile(syncedProfile.toEntity())
          return syncedProfile
        }
      } catch (_: Exception) {}
    }
    return local
  }

  suspend fun registerDeviceFromQr(identity: QrBeltIdentity, athleteId: String): Result<WearableDevice> {
    val session = restoreSession()
    var validatedMac = identity.macAddress ?: ""
    var validatedName = identity.deviceName
    var validatedFw = identity.firmwareVersion
    var deviceId = db.wearableDeviceDao().getBySerial(identity.serialCode)?.id ?: UUID.randomUUID().toString()

    // Validate against Supabase device registration service when online (FR-03 Step 4)
    if (!session.isOfflineSession && session.accessToken.isNotBlank() && SupabaseConfig.isConfigured && isNetworkAvailable()) {
      try {
        val remote =
          supabase.restService
            ?.getDevices("Bearer ${session.accessToken}", serialFilter = "eq.${identity.serialCode}")
            ?.body()
            ?.firstOrNull()
        if (remote != null) {
          deviceId = remote.id
          if (remote.macAddress.isNotBlank()) validatedMac = remote.macAddress
          validatedName = remote.name
          validatedFw = remote.firmwareVersion
        }
      } catch (_: Exception) {}
    }

    val device =
      WearableDevice(
        id = deviceId,
        serialCode = identity.serialCode,
        ownerAthleteId = athleteId,
        name = validatedName,
        macAddress = validatedMac.ifBlank { "Unassigned-BLE" },
        firmwareVersion = validatedFw,
        batteryPercent = 100,
        isConnected = false,
        lastSeenEpochMs = System.currentTimeMillis(),
        synced = false,
      )
    db.wearableDeviceDao().upsertDevice(device.toEntity())
    prefs.savePairedBelt(device.macAddress, device.serialCode)
    return Result.success(device)
  }

  suspend fun saveOrUpdateDevice(device: WearableDevice) {
    db.wearableDeviceDao().upsertDevice(device.toEntity())
    if (device.macAddress.isNotBlank()) {
      prefs.savePairedBelt(device.macAddress, device.serialCode)
    }
  }

  suspend fun saveRace(race: RaceSession) {
    db.raceDao().upsertRace(race.copy(synced = false).toEntity())
  }

  suspend fun deleteRace(raceId: String) {
    db.raceDao().deleteRace(raceId)
  }

  suspend fun recordTrackPoint(point: GpsTrackPoint) {
    db.gpsTrackPointDao().insertTrackPoint(point.copy(synced = false).toEntity())
  }

  suspend fun recordObstacleEvent(event: ObstacleEvent) {
    db.obstacleEventDao().insertObstacle(event.copy(synced = false).toEntity())
  }

  suspend fun recordHealthMetric(record: HealthMetricRecord) {
    db.healthRecordDao().insertHealthRecordIgnoreDuplicate(record.copy(synced = false).toEntity())
  }

  suspend fun recordAlert(alert: AppAlert) {
    db.alertDao().insertAlert(alert.copy(synced = false).toEntity())
  }

  suspend fun acknowledgeAlert(alertId: String) {
    db.alertDao().acknowledgeAlert(alertId)
  }

  suspend fun saveCoachAssignment(assignment: CoachAssignment) {
    db.coachAssignmentDao().upsertAssignment(assignment.toEntity())
    val session = restoreSession()
    if (!session.isOfflineSession && session.accessToken.isNotBlank() && SupabaseConfig.isConfigured && isNetworkAvailable()) {
      try {
        supabase.restService?.upsertAssignment(
          "Bearer ${session.accessToken}",
          CoachAssignmentDto(
            id = assignment.id,
            coachId = assignment.coachId,
            athleteId = assignment.athleteId,
            assignedBy = session.userId,
          ),
        )
      } catch (_: Exception) {}
    }
  }

  suspend fun removeCoachAssignment(id: String) {
    db.coachAssignmentDao().deleteAssignment(id)
    val session = restoreSession()
    if (!session.isOfflineSession && session.accessToken.isNotBlank() && SupabaseConfig.isConfigured && isNetworkAvailable()) {
      try {
        supabase.restService?.deleteAssignment("Bearer ${session.accessToken}", "eq.$id")
      } catch (_: Exception) {}
    }
  }

  suspend fun recordAdminAudit(adminId: String, action: String, targetEntity: String, details: String) {
    val log =
      AdminAuditLog(
        id = UUID.randomUUID().toString(),
        adminId = adminId,
        action = action,
        targetEntity = targetEntity,
        details = details,
      )
    db.adminAuditLogDao().insertLog(log.toEntity())
    val session = restoreSession()
    if (!session.isOfflineSession && session.accessToken.isNotBlank() && SupabaseConfig.isConfigured && isNetworkAvailable()) {
      try {
        supabase.restService?.insertAuditLog(
          "Bearer ${session.accessToken}",
          AdminAuditLogDto(
            adminId = adminId,
            action = action,
            targetEntity = targetEntity,
            details = details,
          ),
        )
      } catch (_: Exception) {}
    }
  }

  // Mark a row synced ONLY if it has not changed since it was uploaded. Blindly writing back the
  // uploaded snapshot (the old behaviour) overwrote newer edits - e.g. race distance updated
  // every second while a sync was in flight - and flagged them as already synced.
  private suspend fun markProfileSynced(snapshot: UserProfileEntity) {
    db.withTransaction {
      if (db.userProfileDao().getProfile(snapshot.id) == snapshot) {
        db.userProfileDao().upsertProfile(snapshot.copy(synced = true))
      }
    }
  }

  private suspend fun markDeviceSynced(snapshot: WearableDeviceEntity) {
    db.withTransaction {
      if (db.wearableDeviceDao().getBySerial(snapshot.serialCode) == snapshot) {
        db.wearableDeviceDao().upsertDevice(snapshot.copy(synced = true))
      }
    }
  }

  private suspend fun markRaceSynced(snapshot: RaceEntity) {
    db.withTransaction {
      if (db.raceDao().getRace(snapshot.id) == snapshot) {
        db.raceDao().upsertRace(snapshot.copy(synced = true))
      }
    }
  }

  private suspend fun markAlertSynced(snapshot: AlertEntity) {
    db.withTransaction {
      if (db.alertDao().getAlert(snapshot.id) == snapshot) {
        db.alertDao().insertAlert(snapshot.copy(synced = true))
      }
    }
  }

  suspend fun syncQueuedRecords(): Result<Int> {
    if (!SupabaseConfig.isConfigured) {
      _syncState.value = SyncState.BACKEND_UNCONFIGURED
      _lastSyncMessage.value = "Add SUPABASE_URL & SUPABASE_ANON_KEY in AI Studio Secrets to enable cloud sync."
      return Result.failure(IllegalStateException(_lastSyncMessage.value))
    }
    // restoreSession() refreshes the access token if it is about to expire. Before, it only ran at
    // app start, so after ~1 hour every upload failed with 401 and the UI still said "up to date".
    val session = restoreSession()
    if (session.isOfflineSession || session.accessToken.isBlank()) {
      _syncState.value = SyncState.QUEUED_OFFLINE
      _lastSyncMessage.value = "Signed in locally — sign in with Supabase account to upload queued records."
      return Result.failure(IllegalStateException(_lastSyncMessage.value))
    }
    if (!isNetworkAvailable()) {
      _syncState.value = SyncState.QUEUED_OFFLINE
      _lastSyncMessage.value = "Offline — records safely queued in local Room database."
      return Result.failure(IllegalStateException(_lastSyncMessage.value))
    }

    val rest = supabase.restService ?: return Result.failure(IllegalStateException("REST client unavailable"))
    val bearer = "Bearer ${session.accessToken}"
    _syncState.value = SyncState.SYNCING
    var syncedCount = 0
    val failures = mutableListOf<String>()
    var unauthorized = false

    fun ok(label: String, res: Response<*>): Boolean {
      if (res.isSuccessful) return true
      if (res.code() == 401) unauthorized = true
      failures += "$label (HTTP ${res.code()})"
      return false
    }

    return try {
      // 1. Profiles
      for (p in db.userProfileDao().getUnsyncedProfiles()) {
        val res =
          rest.upsertProfile(
            bearer,
            ProfileDto(
              id = p.id,
              email = p.email,
              fullName = p.fullName,
              role = p.role,
              accessibilityMode = p.accessibilityMode,
              phone = p.phone,
              emergencyContactName = p.emergencyContactName,
              emergencyContactPhone = p.emergencyContactPhone,
              hapticIntensity = p.hapticIntensity,
              audioCuesEnabled = p.audioCuesEnabled,
              consentedCoachSharing = p.consentedCoachSharing,
            ),
          )
        if (ok("profile", res)) {
          markProfileSynced(p)
          syncedCount++
        }
      }

      // 2. Devices (an unowned, admin-registered belt has owner "" locally -> must be NULL in SQL,
      //    an empty string is not a valid uuid and used to make the whole batch fail)
      for (chunk in db.wearableDeviceDao().getUnsyncedDevices().chunked(SYNC_BATCH_SIZE)) {
        val dtos =
          chunk.map { d ->
            DeviceDto(
              id = d.id,
              serialCode = d.serialCode,
              ownerAthleteId = d.ownerAthleteId.takeIf { owner -> owner.isNotBlank() },
              name = d.name,
              macAddress = d.macAddress,
              firmwareVersion = d.firmwareVersion,
              batteryPercent = d.batteryPercent,
              status = if (d.isConnected) "connected" else "registered",
            )
          }
        if (ok("devices", rest.upsertDevices(bearer, dtos))) {
          chunk.forEach { markDeviceSynced(it) }
          syncedCount += chunk.size
        }
      }

      // 3. Races
      for (chunk in db.raceDao().getUnsyncedRaces().chunked(SYNC_BATCH_SIZE)) {
        val raceDtos =
          chunk.map { r ->
            val domain = r.toDomain()
            RaceDto(
              id = domain.id,
              athleteId = domain.athleteId,
              createdBy = domain.createdBy,
              name = domain.name,
              raceType = domain.raceType,
              totalDistanceMeters = domain.totalDistanceMeters,
              coveredDistanceMeters = domain.coveredDistanceMeters,
              remainingDistanceMeters = domain.remainingDistanceMeters,
              startLocationName = domain.startLocationName,
              finishLocationName = domain.finishLocationName,
              routeWaypointsJson =
                domain.waypoints.map { mapOf("lat" to it.latitude, "lon" to it.longitude, "ele" to it.elevationMeters) },
              status = domain.status.apiValue,
              avgPaceSecPerKm = domain.avgPaceSecPerKm,
              avgSpeedKmh = domain.avgSpeedKmh,
              elapsedSeconds = domain.elapsedSeconds,
              avgHeartRate = domain.avgHeartRate,
              maxHeartRate = domain.maxHeartRate,
              activeCalories = domain.activeCalories,
              startedAt = domain.startedAtEpochMs?.let { Instant.ofEpochMilli(it).toString() },
              finishedAt = domain.finishedAtEpochMs?.let { Instant.ofEpochMilli(it).toString() },
            )
          }
        if (ok("races", rest.upsertRaces(bearer, raceDtos))) {
          chunk.forEach { markRaceSynced(it) }
          syncedCount += chunk.size
        }
      }

      // 4. GPS trackpoints (append-only; large backlogs are sent in batches of SYNC_BATCH_SIZE)
      for (chunk in db.gpsTrackPointDao().getUnsyncedTrackPoints().chunked(SYNC_BATCH_SIZE)) {
        val dtos =
          chunk.map { pt ->
            GpsTrackPointDto(
              id = pt.id,
              raceId = pt.raceId,
              athleteId = pt.athleteId,
              latitude = pt.latitude,
              longitude = pt.longitude,
              accuracyMeters = pt.accuracyMeters,
              speedMps = pt.speedMps,
              distanceFromStartMeters = pt.distanceFromStartMeters,
              deviationMeters = pt.deviationMeters,
              recordedAt = Instant.ofEpochMilli(pt.recordedAtEpochMs).toString(),
            )
          }
        if (ok("gps", rest.upsertTrackPoints(bearer, dtos))) {
          db.gpsTrackPointDao().markSynced(chunk.map { it.id })
          syncedCount += chunk.size
        }
      }

      // 5. Obstacle events
      for (chunk in db.obstacleEventDao().getUnsyncedObstacles().chunked(SYNC_BATCH_SIZE)) {
        val dtos =
          chunk.map { o ->
            ObstacleEventDto(
              id = o.id,
              raceId = o.raceId,
              athleteId = o.athleteId,
              deviceSerial = o.deviceSerial,
              direction = o.direction,
              severity = o.severity,
              distanceCm = o.distanceCm,
              recordedAt = Instant.ofEpochMilli(o.recordedAtEpochMs).toString(),
            )
          }
        if (ok("obstacles", rest.upsertObstacleEvents(bearer, dtos))) {
          db.obstacleEventDao().markSynced(chunk.map { it.id })
          syncedCount += chunk.size
        }
      }

      // 6. Health records
      for (chunk in db.healthRecordDao().getUnsyncedHealthRecords().chunked(SYNC_BATCH_SIZE)) {
        val dtos =
          chunk.map { h ->
            HealthRecordDto(
              id = h.id,
              athleteId = h.athleteId,
              raceId = h.raceId,
              heartRateBpm = h.heartRateBpm,
              avgHeartRateBpm = h.avgHeartRateBpm,
              maxHeartRateBpm = h.maxHeartRateBpm,
              steps = h.steps,
              activeCalories = h.activeCalories,
              exerciseDurationSec = h.exerciseDurationSec,
              distanceMeters = h.distanceMeters,
              spo2Percent = h.spo2Percent,
              sleepMinutes = h.sleepMinutes,
              source = h.source,
              recordedAt = Instant.ofEpochMilli(h.recordedAtEpochMs).toString(),
            )
          }
        if (ok("health", rest.upsertHealthRecords(bearer, dtos))) {
          db.healthRecordDao().markSynced(chunk.map { it.id })
          syncedCount += chunk.size
        }
      }

      // 7. Alerts
      for (chunk in db.alertDao().getUnsyncedAlerts().chunked(SYNC_BATCH_SIZE)) {
        val dtos =
          chunk.map { a ->
            AlertDto(
              id = a.id,
              athleteId = a.athleteId,
              alertType = a.alertType,
              title = a.title,
              message = a.message,
              severity = a.severity,
              acknowledged = a.acknowledged,
              createdAt = Instant.ofEpochMilli(a.createdAtEpochMs).toString(),
            )
          }
        if (ok("alerts", rest.upsertAlerts(bearer, dtos))) {
          chunk.forEach { markAlertSynced(it) }
          syncedCount += chunk.size
        }
      }

      if (syncedCount > 0) prefs.updateLastSync(System.currentTimeMillis())
      if (failures.isEmpty()) {
        _syncState.value = SyncState.SYNCED
        _lastSyncMessage.value =
          if (syncedCount > 0) "Synced $syncedCount record(s) to Supabase" else "All records up to date"
        Result.success(syncedCount)
      } else {
        // Previously a rejected batch was ignored and the UI still reported "all up to date".
        val msg =
          if (unauthorized) {
            "Session expired — sign in again to upload queued records."
          } else {
            "Sync incomplete: ${failures.distinct().joinToString()}. Records stay queued on this device."
          }
        _syncState.value = SyncState.ERROR
        _lastSyncMessage.value = msg
        Result.failure(IllegalStateException(msg))
      }
    } catch (e: Exception) {
      _syncState.value = SyncState.ERROR
      _lastSyncMessage.value = "Sync failed: ${e.localizedMessage ?: "Network error"}"
      Result.failure(e)
    }
  }

  private suspend fun pullCloudData(bearer: String, currentProfile: UserProfile) {
    val rest = supabase.restService ?: return
    try {
      // Pull profiles visible to this role (self, or assigned athletes for coach, or all for admin)
      val remoteProfiles = rest.getProfiles(bearer).body().orEmpty()
      val dirtyProfileIds = db.userProfileDao().getUnsyncedProfiles().map { it.id }.toSet()
      if (remoteProfiles.isNotEmpty()) {
        db.userProfileDao().upsertProfiles(
          remoteProfiles.filter { it.id !in dirtyProfileIds }.map { dto ->
            UserProfile(
              id = dto.id,
              email = dto.email,
              fullName = dto.fullName,
              role = UserRole.fromApi(dto.role),
              accessibilityMode = AccessibilityMode.fromApi(dto.accessibilityMode),
              phone = dto.phone,
              emergencyContactName = dto.emergencyContactName,
              emergencyContactPhone = dto.emergencyContactPhone,
              hapticIntensity = dto.hapticIntensity,
              audioCuesEnabled = dto.audioCuesEnabled,
              consentedCoachSharing = dto.consentedCoachSharing,
              onboardingCompleted = if (dto.id == currentProfile.id) currentProfile.onboardingCompleted else true,
              synced = true,
            ).toEntity()
          }
        )
      }

      val remoteDevices = rest.getDevices(bearer).body().orEmpty()
      val dirtyDeviceIds = db.wearableDeviceDao().getUnsyncedDevices().map { it.id }.toSet()
      if (remoteDevices.isNotEmpty()) {
        db.wearableDeviceDao().upsertDevices(
          remoteDevices.filter { it.id !in dirtyDeviceIds }.map { d ->
            WearableDevice(
              id = d.id,
              serialCode = d.serialCode,
              ownerAthleteId = d.ownerAthleteId.orEmpty(),
              name = d.name,
              macAddress = d.macAddress,
              firmwareVersion = d.firmwareVersion,
              batteryPercent = d.batteryPercent,
              isConnected = false,
              synced = true,
            ).toEntity()
          }
        )
      }

      val remoteAssignments = rest.getAssignments(bearer).body().orEmpty()
      if (remoteAssignments.isNotEmpty()) {
        val profileMap = remoteProfiles.associateBy { it.id }
        db.coachAssignmentDao().upsertAssignments(
          remoteAssignments.mapNotNull { a ->
            val id = a.id ?: return@mapNotNull null
            CoachAssignment(
              id = id,
              coachId = a.coachId,
              athleteId = a.athleteId,
              coachName = profileMap[a.coachId]?.fullName ?: a.coachId.take(8),
              athleteName = profileMap[a.athleteId]?.fullName ?: a.athleteId.take(8),
            ).toEntity()
          }
        )
      }
    } catch (_: Exception) {
      // Cloud pull is best-effort; local Room remains authoritative
    }
  }
}
