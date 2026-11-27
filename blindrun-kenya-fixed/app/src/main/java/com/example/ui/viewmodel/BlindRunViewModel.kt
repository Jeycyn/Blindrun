package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.StoredSession
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
import com.example.data.model.SyncState
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.model.WearableDevice
import com.example.data.repository.BlindRunRepository
import com.example.data.repository.RegistrationOutcome
import com.example.hardware.BleConnectionState
import com.example.hardware.BleObstaclePacket
import com.example.hardware.BleScannedDevice
import com.example.hardware.BleWaistBeltManager
import com.example.hardware.GpsReading
import com.example.hardware.GpsTrackingManager
import com.example.hardware.HapticFeedbackManager
import com.example.hardware.HealthConnectManager
import com.example.hardware.HealthConnectStatus
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface AuthGateState {
  data object Loading : AuthGateState

  data object Unauthenticated : AuthGateState

  data class Authenticated(val session: StoredSession) : AuthGateState
}

data class AuthFeedback(
  val message: String,
  val isError: Boolean = true,
  val switchToSignIn: Boolean = false,
)

class BlindRunViewModel(application: Application) : AndroidViewModel(application) {
  private val repository = BlindRunRepository(application)
  val bleManager = BleWaistBeltManager(application)
  val gpsManager = GpsTrackingManager(application)
  val healthManager = HealthConnectManager(application)
  val hapticManager = HapticFeedbackManager(application)

  private val _authGate = MutableStateFlow<AuthGateState>(AuthGateState.Loading)
  val authGate: StateFlow<AuthGateState> = _authGate.asStateFlow()

  private val _authBusy = MutableStateFlow(false)
  val authBusy: StateFlow<Boolean> = _authBusy.asStateFlow()

  private val _authFeedback = MutableStateFlow<AuthFeedback?>(null)
  val authFeedback: StateFlow<AuthFeedback?> = _authFeedback.asStateFlow()

  private val _activeUserId = MutableStateFlow("")
  val activeUserId: StateFlow<String> = _activeUserId.asStateFlow()

  val currentProfile: StateFlow<UserProfile?> =
    _activeUserId
      .flatMapLatest { uid ->
        if (uid.isBlank()) flowOf(null) else repository.observeProfile(uid)
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

  val allProfiles: StateFlow<List<UserProfile>> =
    repository
      .observeAllProfiles()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val athleteDevices: StateFlow<List<WearableDevice>> =
    _activeUserId
      .flatMapLatest { uid ->
        if (uid.isBlank()) flowOf(emptyList()) else repository.observeDevicesForAthlete(uid)
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val allDevices: StateFlow<List<WearableDevice>> =
    repository
      .observeAllDevices()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val athleteRaces: StateFlow<List<RaceSession>> =
    _activeUserId
      .flatMapLatest { uid ->
        if (uid.isBlank()) flowOf(emptyList()) else repository.observeRacesForAthlete(uid)
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val allRaces: StateFlow<List<RaceSession>> =
    repository
      .observeAllRaces()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val athleteObstacles: StateFlow<List<ObstacleEvent>> =
    _activeUserId
      .flatMapLatest { uid ->
        if (uid.isBlank()) flowOf(emptyList()) else repository.observeObstaclesForAthlete(uid)
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val athleteHealthRecords: StateFlow<List<HealthMetricRecord>> =
    _activeUserId
      .flatMapLatest { uid ->
        if (uid.isBlank()) flowOf(emptyList()) else repository.observeHealthRecords(uid)
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val athleteAlerts: StateFlow<List<AppAlert>> =
    _activeUserId
      .flatMapLatest { uid ->
        if (uid.isBlank()) flowOf(emptyList()) else repository.observeAlertsForAthlete(uid)
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val allAlerts: StateFlow<List<AppAlert>> =
    repository
      .observeAllAlerts()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val coachAssignments: StateFlow<List<CoachAssignment>> =
    repository
      .observeAllAssignments()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val auditLogs: StateFlow<List<AdminAuditLog>> =
    repository
      .observeAuditLogs()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val pendingSyncCount: StateFlow<Int> =
    _activeUserId
      .flatMapLatest { uid ->
        if (uid.isBlank()) flowOf(0) else repository.observePendingSyncCount(uid)
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

  val syncState: StateFlow<SyncState> = repository.syncState
  val lastSyncMessage: StateFlow<String> = repository.lastSyncMessage
  val bleState: StateFlow<BleConnectionState> = bleManager.connectionState
  val bleScannedDevices: StateFlow<List<BleScannedDevice>> = bleManager.scannedDevices
  val gpsReading: StateFlow<GpsReading?> = gpsManager.latestReading
  val gpsWarning: StateFlow<String?> = gpsManager.gpsWarning

  private val _healthStatus = MutableStateFlow(HealthConnectStatus())
  val healthStatus: StateFlow<HealthConnectStatus> = _healthStatus.asStateFlow()

  private val _activeRaceId = MutableStateFlow<String?>(null)
  val activeRaceId: StateFlow<String?> = _activeRaceId.asStateFlow()

  val activeRace: StateFlow<RaceSession?> =
    _activeRaceId
      .flatMapLatest { id -> if (id.isNullOrBlank()) flowOf(null) else repository.observeRace(id) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

  val activeRaceTrackPoints: StateFlow<List<GpsTrackPoint>> =
    _activeRaceId
      .flatMapLatest { id ->
        if (id.isNullOrBlank()) flowOf(emptyList()) else repository.observeTrackPoints(id)
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  private val _latestObstacle = MutableStateFlow<BleObstaclePacket?>(null)
  val latestObstacle: StateFlow<BleObstaclePacket?> = _latestObstacle.asStateFlow()

  private val _routeDeviationMeters = MutableStateFlow(0.0)
  val routeDeviationMeters: StateFlow<Double> = _routeDeviationMeters.asStateFlow()

  private val _uiMessage = MutableStateFlow<String?>(null)
  val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

  private val _lightRaceHud = MutableStateFlow(false)
  val lightRaceHud: StateFlow<Boolean> = _lightRaceHud.asStateFlow()

  private var raceTimerJob: Job? = null
  private var lastAnnouncedKm = 0

  // All race read-modify-write goes through this lock (see mutateActiveRace).
  private val raceMutex = Mutex()
  private var offRoute = false
  private var lastDeviationCueAtMs = 0L
  private val lastObstacleCueAtMs = mutableMapOf<ObstacleDirection, Long>()
  private val lastObstacleRecordAtMs = mutableMapOf<ObstacleDirection, Long>()

  val isSupabaseConfigured: Boolean
    get() = repository.isSupabaseConfigured()

  init {
    restorePersistedSession()
    observeHardwareEvents()
  }

  private fun restorePersistedSession() {
    viewModelScope.launch {
      repository.pauseStaleRunningRaces()
      val session = repository.restoreSession()
      _lightRaceHud.value = session.highContrastLightRaceHud
      if (session.isAuthenticated) {
        _activeUserId.value = session.userId
        _authGate.value = AuthGateState.Authenticated(session)
        attemptAutoReconnectBelt(session)
        refreshHealthConnectStatus()
      } else {
        _authGate.value = AuthGateState.Unauthenticated
      }
    }
  }

  private fun observeHardwareEvents() {
    viewModelScope.launch {
      // collect, NOT collectLatest: collectLatest cancels the previous handler when a new packet
      // arrives, which dropped haptic cues and un-saved obstacle events during bursts.
      bleManager.obstacleEvents.collect { packet ->
        _latestObstacle.value = packet
        val profile = currentProfile.value
        val mode = profile?.accessibilityMode ?: AccessibilityMode.VISUALLY_IMPAIRED
        val intensity = profile?.hapticIntensity ?: 85
        val audioCues = profile?.audioCuesEnabled ?: true

        // The belt can stream several packets a second. Throttle the phone-side cue/TTS and the
        // stored history per direction, but never throttle critical (HIGH) obstacles.
        val nowMs = SystemClock.elapsedRealtime()
        val critical = packet.severity == ObstacleSeverity.HIGH
        val cueDue = critical || nowMs - (lastObstacleCueAtMs[packet.direction] ?: 0L) >= OBSTACLE_CUE_MIN_INTERVAL_MS
        if (cueDue) {
          lastObstacleCueAtMs[packet.direction] = nowMs
          hapticManager.triggerDirectionalObstacle(
            direction = packet.direction,
            intensityPercent = intensity,
            accessibilityMode = mode,
            audioCuesEnabled = audioCues,
            distanceCm = packet.distanceCm,
          )
        }
        val uid = _activeUserId.value
        val recordDue =
          critical || nowMs - (lastObstacleRecordAtMs[packet.direction] ?: 0L) >= OBSTACLE_RECORD_MIN_INTERVAL_MS
        if (uid.isNotBlank() && recordDue) {
          lastObstacleRecordAtMs[packet.direction] = nowMs
          val session = repository.prefs.getSession()
          repository.recordObstacleEvent(
            ObstacleEvent(
              id = UUID.randomUUID().toString(),
              raceId = _activeRaceId.value,
              athleteId = uid,
              // the column is "device_serial": store the serial, not the MAC address
              deviceSerial = session.pairedBeltSerial.ifBlank { bleState.value.connectedMacAddress },
              direction = packet.direction,
              severity = packet.severity,
              distanceCm = packet.distanceCm,
              recordedAtEpochMs = packet.timestampMs,
              synced = false,
            )
          )
        }
      }
    }

    viewModelScope.launch {
      bleManager.disconnectionAlerts.collect { deviceName ->
        val profile = currentProfile.value
        hapticManager.triggerDisconnectWarning(
          accessibilityMode = profile?.accessibilityMode ?: AccessibilityMode.VISUALLY_IMPAIRED,
          audioCuesEnabled = profile?.audioCuesEnabled ?: true,
        )
        val uid = _activeUserId.value
        if (uid.isNotBlank()) {
          repository.recordAlert(
            AppAlert(
              id = UUID.randomUUID().toString(),
              athleteId = uid,
              alertType = "device_disconnection",
              title = "Waist Belt Disconnected",
              message = "$deviceName connection lost. Auto-reconnect is active; local ESP32 obstacle sensing continues.",
              severity = "warning",
            )
          )
        }
      }
    }
  }

  private fun attemptAutoReconnectBelt(session: StoredSession) {
    if (session.pairedBeltMac.isNotBlank() &&
      session.pairedBeltMac != "Unassigned-BLE" &&
      bleManager.hasRequiredPermissions()
    ) {
      bleManager.connectToAddress(session.pairedBeltMac, "BlindRun Belt (${session.pairedBeltSerial})")
    }
  }

  fun clearAuthFeedback() {
    _authFeedback.value = null
  }

  fun clearUiMessage() {
    _uiMessage.value = null
  }

  fun signIn(email: String, password: String) {
    if (email.isBlank() || password.isBlank()) {
      _authFeedback.value = AuthFeedback("Enter both email and password.", isError = true)
      return
    }
    viewModelScope.launch {
      _authBusy.value = true
      _authFeedback.value = null
      val res = repository.signInWithSupabase(email, password)
      _authBusy.value = false
      res.fold(
        onSuccess = {
          val session = repository.restoreSession()
          _activeUserId.value = session.userId
          _authGate.value = AuthGateState.Authenticated(session)
          attemptAutoReconnectBelt(session)
          refreshHealthConnectStatus()
        },
        onFailure = {
          _authFeedback.value = AuthFeedback(it.localizedMessage ?: "Sign in failed", isError = true)
        },
      )
    }
  }

  fun register(email: String, password: String, fullName: String, role: UserRole) {
    if (fullName.isBlank() || email.isBlank() || password.length < 6) {
      _authFeedback.value =
        AuthFeedback("Enter your full name, valid email, and password (6+ characters).", isError = true)
      return
    }
    viewModelScope.launch {
      _authBusy.value = true
      _authFeedback.value = null
      val res = repository.registerWithSupabase(email, password, fullName, role)
      _authBusy.value = false
      res.fold(
        onSuccess = { outcome ->
          when (outcome) {
            is RegistrationOutcome.SignedIn -> {
              val session = repository.restoreSession()
              _activeUserId.value = session.userId
              _authGate.value = AuthGateState.Authenticated(session)
              refreshHealthConnectStatus()
            }
            is RegistrationOutcome.PendingEmailVerification -> {
              _authFeedback.value =
                AuthFeedback(
                  message = outcome.message,
                  isError = false,
                  switchToSignIn = true,
                )
            }
          }
        },
        onFailure = {
          _authFeedback.value = AuthFeedback(it.localizedMessage ?: "Registration failed", isError = true)
        },
      )
    }
  }

  fun recoverPassword(email: String) {
    if (email.isBlank()) {
      _authFeedback.value = AuthFeedback("Enter your account email first.", isError = true)
      return
    }
    viewModelScope.launch {
      _authBusy.value = true
      val res = repository.recoverPassword(email)
      _authBusy.value = false
      _authFeedback.value =
        res.fold(
          onSuccess = {
            AuthFeedback("Password recovery instructions sent to ${email.trim()}.", isError = false)
          },
          onFailure = {
            AuthFeedback(it.localizedMessage ?: "Could not send recovery email.", isError = true)
          },
        )
    }
  }

  fun continueOfflineMode(fullName: String, email: String, role: UserRole) {
    viewModelScope.launch {
      _authBusy.value = true
      repository.startOfflineSession(fullName, email, role)
      val session = repository.restoreSession()
      _activeUserId.value = session.userId
      _authGate.value = AuthGateState.Authenticated(session)
      _authBusy.value = false
      attemptAutoReconnectBelt(session)
      refreshHealthConnectStatus()
    }
  }

  fun signOut() {
    viewModelScope.launch {
      stopRaceHardware()
      bleManager.disconnect()
      repository.signOut()
      _activeUserId.value = ""
      _activeRaceId.value = null
      _authGate.value = AuthGateState.Unauthenticated
    }
  }

  fun updateProfile(profile: UserProfile, markOnboardingDone: Boolean = false) {
    viewModelScope.launch {
      val updated = repository.saveProfile(profile.copy(onboardingCompleted = markOnboardingDone || profile.onboardingCompleted))
      _uiMessage.value =
        if (updated.synced) "Profile saved and synced to Supabase." else "Profile saved offline (queued for sync)."
    }
  }

  fun handleScannedBeltQr(rawQr: String): Result<WearableDevice> {
    val identity =
      BleWaistBeltManager.parseBeltQrCode(rawQr)
        ?: return Result.failure(
          IllegalArgumentException(
            "Invalid or insecure QR code. Scan a valid BlindRun belt tag (e.g. blindrun://belt?serial=BRK-01&mac=AA:BB:CC:DD:EE:FF)."
          )
        )
    val uid = _activeUserId.value
    if (uid.isBlank()) return Result.failure(IllegalStateException("Not signed in"))

    viewModelScope.launch {
      val res = repository.registerDeviceFromQr(identity, uid)
      res.onSuccess { device ->
        _uiMessage.value = "Belt ${device.serialCode} validated. Searching via BLE..."
        if (bleManager.hasRequiredPermissions()) {
          if (device.macAddress != "Unassigned-BLE") {
            bleManager.connectToAddress(device.macAddress, device.name)
          } else {
            bleManager.startScan(device.serialCode)
          }
        }
      }
    }
    return Result.success(
      WearableDevice(
        id = identity.serialCode,
        serialCode = identity.serialCode,
        ownerAthleteId = uid,
        name = identity.deviceName,
        macAddress = identity.macAddress ?: "Scanning...",
        firmwareVersion = identity.firmwareVersion,
      )
    )
  }

  fun connectBleDevice(macAddress: String, name: String) {
    val uid = _activeUserId.value
    viewModelScope.launch {
      val ok = bleManager.connectToAddress(macAddress, name)
      if (ok && uid.isNotBlank()) {
        val device =
          WearableDevice(
            id = UUID.nameUUIDFromBytes(macAddress.toByteArray()).toString(),
            serialCode = "BLE-${macAddress.replace(":", "").uppercase()}",
            ownerAthleteId = uid,
            name = name,
            macAddress = macAddress,
            firmwareVersion = bleState.value.firmwareVersion,
            batteryPercent = bleState.value.batteryPercent ?: 100,
            isConnected = true,
            synced = false,
          )
        repository.saveOrUpdateDevice(device)
      }
    }
  }

  fun testDirectionalHaptic(direction: ObstacleDirection) {
    val profile = currentProfile.value
    val intensity = profile?.hapticIntensity ?: 85
    val mode = profile?.accessibilityMode ?: AccessibilityMode.VISUALLY_IMPAIRED
    val audio = profile?.audioCuesEnabled ?: true
    hapticManager.triggerDirectionalObstacle(direction, intensity, mode, audio, distanceCm = 90)
    bleManager.sendHapticCommand(direction, intensity)
  }

  fun createOrUpdateRace(
    existingId: String? = null,
    targetAthleteId: String? = null,
    name: String,
    raceType: String,
    totalDistanceKm: Double,
    startLocation: String,
    finishLocation: String,
    waypoints: List<RouteWaypoint>,
  ) {
    val actorId = _activeUserId.value
    if (actorId.isBlank()) return
    val athleteId = targetAthleteId ?: actorId
    val totalMeters = (totalDistanceKm * 1000.0).coerceAtLeast(100.0)
    viewModelScope.launch {
      val existing = existingId?.let { id -> repository.getRace(id) }
      val race =
        (existing
          ?: RaceSession(
            id = UUID.randomUUID().toString(),
            athleteId = athleteId,
            createdBy = actorId,
            name = name.trim(),
            raceType = raceType,
            totalDistanceMeters = totalMeters,
            startLocationName = startLocation.trim(),
            finishLocationName = finishLocation.trim(),
            waypoints = waypoints,
          )).copy(
          name = name.trim(),
          raceType = raceType,
          totalDistanceMeters = totalMeters,
          startLocationName = startLocation.trim(),
          finishLocationName = finishLocation.trim(),
          waypoints = waypoints,
          synced = false,
        )
      repository.saveRace(race)
      bleManager.sendRaceConfiguration(race.name, race.totalDistanceMeters, race.status.apiValue)
      _uiMessage.value = "Race '${race.name}' saved locally & queued for cloud sync."
    }
  }

  fun parseRouteFile(uri: Uri, onParsed: (waypoints: List<RouteWaypoint>, distanceKm: Double) -> Unit) {
    viewModelScope.launch {
      try {
        val stream = getApplication<Application>().contentResolver.openInputStream(uri)
        if (stream != null) {
          val points = stream.use { GpsTrackingManager.parseGpxOrTcx(it) }
          val distKm = GpsTrackingManager.computeRouteTotalMeters(points) / 1000.0
          onParsed(points, distKm)
          _uiMessage.value =
            if (points.isNotEmpty()) {
              "Loaded ${points.size} waypoints (${String.format("%.2f", distKm)} km)"
            } else {
              "No GPX/TCX waypoints found in selected file."
            }
        }
      } catch (e: Exception) {
        _uiMessage.value = "Could not read route file: ${e.localizedMessage}"
      }
    }
  }

  fun selectActiveRace(raceId: String) {
    _activeRaceId.value = raceId
  }

  fun startRace(raceId: String) {
    viewModelScope.launch {
      val race = athleteRaces.value.firstOrNull { it.id == raceId } ?: allRaces.value.firstOrNull { it.id == raceId } ?: return@launch
      val now = System.currentTimeMillis()
      val updated =
        race.copy(
          status = RaceStatus.RUNNING,
          startedAtEpochMs = race.startedAtEpochMs ?: now,
          synced = false,
        )
      repository.saveRace(updated)
      _activeRaceId.value = raceId
      lastAnnouncedKm = (updated.coveredDistanceMeters / 1000.0).toInt()
      bleManager.sendRaceConfiguration(updated.name, updated.totalDistanceMeters, "running")

      val profile = currentProfile.value
      hapticManager.triggerStartCue(
        profile?.accessibilityMode ?: AccessibilityMode.VISUALLY_IMPAIRED,
        profile?.audioCuesEnabled ?: true,
      )
      startRaceHardwareAndTimer(updated)
    }
  }

  /**
   * The ONLY way race state is changed while a race is live. The 1 Hz timer, the GPS callback and
   * the pause/finish buttons used to each copy a stale in-memory race and write it back, so one
   * silently erased the other's update (lost distance, elapsed time jumping backwards). Reading
   * the current row inside a lock makes every update build on the latest state.
   */
  private suspend fun mutateActiveRace(transform: (RaceSession) -> RaceSession): RaceSession? =
    raceMutex.withLock {
      val id = _activeRaceId.value ?: return@withLock null
      val race = repository.getRace(id) ?: return@withLock null
      val updated = transform(race)
      if (updated != race) repository.saveRace(updated)
      updated
    }

  fun pauseRace() {
    viewModelScope.launch {
      stopRaceHardware()
      val paused = mutateActiveRace { it.copy(status = RaceStatus.PAUSED) } ?: return@launch
      bleManager.sendRaceConfiguration(paused.name, paused.totalDistanceMeters, "paused")
      val profile = currentProfile.value
      hapticManager.triggerPauseCue(
        profile?.accessibilityMode ?: AccessibilityMode.VISUALLY_IMPAIRED,
        profile?.audioCuesEnabled ?: true,
      )
    }
  }

  fun resumeRace() {
    viewModelScope.launch {
      val updated = mutateActiveRace { it.copy(status = RaceStatus.RUNNING) } ?: return@launch
      bleManager.sendRaceConfiguration(updated.name, updated.totalDistanceMeters, "running")
      val profile = currentProfile.value
      hapticManager.triggerStartCue(
        profile?.accessibilityMode ?: AccessibilityMode.VISUALLY_IMPAIRED,
        profile?.audioCuesEnabled ?: true,
      )
      startRaceHardwareAndTimer(updated)
    }
  }

  fun finishRace() {
    viewModelScope.launch {
      stopRaceHardware()
      val finished =
        mutateActiveRace {
          it.copy(status = RaceStatus.COMPLETED, finishedAtEpochMs = System.currentTimeMillis())
        } ?: return@launch
      bleManager.sendRaceConfiguration(finished.name, finished.totalDistanceMeters, "completed")
      val profile = currentProfile.value
      hapticManager.triggerFinishCue(
        profile?.accessibilityMode ?: AccessibilityMode.VISUALLY_IMPAIRED,
        profile?.audioCuesEnabled ?: true,
      )
      repository.recordAlert(
        AppAlert(
          id = UUID.randomUUID().toString(),
          athleteId = finished.athleteId,
          alertType = "race_completed",
          title = "Race Completed: ${finished.name}",
          message = "Covered ${String.format("%.2f", finished.coveredDistanceMeters / 1000.0)} km in ${formatDuration(finished.elapsedSeconds)}.",
          severity = "info",
        )
      )
      syncNow()
    }
  }

  fun toggleRaceHudContrast() {
    viewModelScope.launch {
      val newVal = !_lightRaceHud.value
      _lightRaceHud.value = newVal
      val profile = currentProfile.value
      repository.prefs.updateAccessibilitySettings(
        hapticIntensity = profile?.hapticIntensity ?: 85,
        audioCues = profile?.audioCuesEnabled ?: true,
        lightHud = newVal,
      )
    }
  }

  private fun startRaceHardwareAndTimer(initialRace: RaceSession) {
    raceTimerJob?.cancel()
    offRoute = false
    lastDeviationCueAtMs = 0L
    raceTimerJob =
      viewModelScope.launch {
        var lastTickMs = SystemClock.elapsedRealtime()
        while (true) {
          delay(1000L)
          // Wall-clock based: a late or skipped tick must not lose running time.
          val nowMs = SystemClock.elapsedRealtime()
          val dtSec = ((nowMs - lastTickMs) / 1000L).coerceAtLeast(1L)
          lastTickMs += dtSec * 1000L
          var running = true
          val updated =
            mutateActiveRace { race ->
              if (race.status != RaceStatus.RUNNING) {
                running = false
                race
              } else {
                val newElapsed = race.elapsedSeconds + dtSec
                val avgSpeedKmh =
                  if (newElapsed > 0) (race.coveredDistanceMeters / 1000.0) / (newElapsed / 3600.0) else 0.0
                val paceSecPerKm =
                  if (race.coveredDistanceMeters >= 10.0) newElapsed / (race.coveredDistanceMeters / 1000.0) else 0.0
                race.copy(elapsedSeconds = newElapsed, avgSpeedKmh = avgSpeedKmh, avgPaceSecPerKm = paceSecPerKm)
              }
            }
          if (updated == null || !running) break
        }
      }

    gpsManager.startTracking { reading, deltaMeters ->
      viewModelScope.launch {
        var deviation = 0.0
        var newCovered = 0.0
        var counted = false
        val updated =
          mutateActiveRace { race ->
            if (race.status != RaceStatus.RUNNING) return@mutateActiveRace race
            counted = true
            newCovered = race.coveredDistanceMeters + deltaMeters
            deviation =
              GpsTrackingManager.calculateMinDeviationMeters(reading.latitude, reading.longitude, race.waypoints)
            val elapsed = race.elapsedSeconds.coerceAtLeast(1L)
            val avgSpeed = (newCovered / 1000.0) / (elapsed / 3600.0)
            val paceSec = if (newCovered >= 10.0) elapsed / (newCovered / 1000.0) else 0.0
            val calories = (newCovered / 1000.0 * 62.0).toInt()
            race.copy(
              coveredDistanceMeters = newCovered,
              avgSpeedKmh = avgSpeed,
              avgPaceSecPerKm = paceSec,
              activeCalories = maxOf(race.activeCalories, calories),
            )
          }
        if (updated == null || !counted) return@launch
        _routeDeviationMeters.value = deviation

        val profile = currentProfile.value
        val mode = profile?.accessibilityMode ?: AccessibilityMode.VISUALLY_IMPAIRED
        val audio = profile?.audioCuesEnabled ?: true

        // Off-route handling: warn immediately, repeat the cue every 15 s while still off route,
        // store ONE alert per excursion (it used to fire - and sync to the coach - on every GPS fix).
        if (updated.waypoints.isNotEmpty()) {
          val nowMs = SystemClock.elapsedRealtime()
          if (deviation > GpsTrackingManager.ROUTE_DEVIATION_THRESHOLD_M) {
            val firstFix = !offRoute
            if (firstFix || nowMs - lastDeviationCueAtMs >= DEVIATION_REPEAT_CUE_MS) {
              lastDeviationCueAtMs = nowMs
              hapticManager.triggerDeviationWarning(deviation.toInt(), mode, audio)
            }
            if (firstFix) {
              offRoute = true
              repository.recordAlert(
                AppAlert(
                  id = UUID.randomUUID().toString(),
                  athleteId = updated.athleteId,
                  alertType = "route_deviation",
                  title = "Route Deviation Warning",
                  message = "${deviation.toInt()}m off configured route on ${updated.name}.",
                  severity = "warning",
                )
              )
            }
          } else if (offRoute && deviation < GpsTrackingManager.ROUTE_DEVIATION_THRESHOLD_M * 0.7) {
            offRoute = false // hysteresis: back on route
          }
        }

        val currentKm = (newCovered / 1000.0).toInt()
        if (currentKm > lastAnnouncedKm) {
          lastAnnouncedKm = currentKm
          val remKm = ((updated.totalDistanceMeters - newCovered).coerceAtLeast(0.0)) / 1000.0
          hapticManager.triggerRaceEvent(
            eventLabel = "$currentKm kilometers covered, ${String.format("%.1f", remKm)} kilometers remaining",
            timings = longArrayOf(0, 220),
            amplitudes = intArrayOf(0, 240),
            accessibilityMode = mode,
            audioCuesEnabled = audio,
          )
        }

        repository.recordTrackPoint(
          GpsTrackPoint(
            id = UUID.randomUUID().toString(),
            raceId = updated.id,
            athleteId = updated.athleteId,
            latitude = reading.latitude,
            longitude = reading.longitude,
            accuracyMeters = reading.accuracyMeters,
            speedMps = reading.speedMps,
            distanceFromStartMeters = newCovered,
            deviationMeters = deviation,
            recordedAtEpochMs = reading.timestampMs,
            synced = false,
          )
        )
      }
    }
  }

  private fun stopRaceHardware() {
    raceTimerJob?.cancel()
    raceTimerJob = null
    offRoute = false
    gpsManager.stopTracking()
  }

  fun refreshHealthConnectStatus() {
    viewModelScope.launch {
      _healthStatus.value = healthManager.checkAvailabilityAndPermissions()
    }
  }

  fun syncSmartwatchHealthData() {
    val uid = _activeUserId.value
    if (uid.isBlank()) return
    viewModelScope.launch {
      val (status, record) = healthManager.readLatestSmartwatchSnapshot(uid)
      _healthStatus.value = status
      if (record != null) {
        repository.recordHealthMetric(record)
        _uiMessage.value = "Retrieved smartwatch readings from ${record.source}."
      } else {
        _uiMessage.value = status.statusMessage
      }
    }
  }

  fun syncNow() {
    viewModelScope.launch {
      val res = repository.syncQueuedRecords()
      _uiMessage.value =
        res.fold(
          onSuccess = { count -> if (count > 0) "Synced $count queued record(s) to Supabase." else "Everything is synced." },
          onFailure = { it.localizedMessage ?: "Offline — queued locally." },
        )
    }
  }

  fun acknowledgeAlert(alertId: String) {
    viewModelScope.launch { repository.acknowledgeAlert(alertId) }
  }

  fun assignCoachToAthlete(coach: UserProfile, athlete: UserProfile) {
    val adminId = _activeUserId.value
    viewModelScope.launch {
      val assignment =
        CoachAssignment(
          id = UUID.randomUUID().toString(),
          coachId = coach.id,
          athleteId = athlete.id,
          coachName = coach.fullName,
          athleteName = athlete.fullName,
        )
      repository.saveCoachAssignment(assignment)
      repository.recordAdminAudit(
        adminId = adminId,
        action = "ASSIGN_COACH",
        targetEntity = athlete.fullName,
        details = "Assigned coach ${coach.fullName} to ${athlete.fullName}",
      )
      _uiMessage.value = "Assigned ${coach.fullName} to ${athlete.fullName}"
    }
  }

  fun unassignCoach(assignment: CoachAssignment) {
    val adminId = _activeUserId.value
    viewModelScope.launch {
      repository.removeCoachAssignment(assignment.id)
      repository.recordAdminAudit(
        adminId = adminId,
        action = "REMOVE_ASSIGNMENT",
        targetEntity = assignment.athleteName,
        details = "Removed coach ${assignment.coachName}",
      )
    }
  }

  fun updateUserRoleByAdmin(target: UserProfile, newRole: UserRole) {
    val adminId = _activeUserId.value
    viewModelScope.launch {
      repository.saveProfile(target.copy(role = newRole))
      repository.recordAdminAudit(
        adminId = adminId,
        action = "UPDATE_ROLE",
        targetEntity = target.fullName,
        details = "Changed role to ${newRole.label}",
      )
      _uiMessage.value = "${target.fullName} is now ${newRole.label}"
    }
  }

  fun registerDeviceByAdmin(serial: String, name: String, mac: String, firmware: String) {
    val adminId = _activeUserId.value
    viewModelScope.launch {
      val device =
        WearableDevice(
          id = UUID.randomUUID().toString(),
          serialCode = serial.trim(),
          ownerAthleteId = "",
          name = name.trim(),
          macAddress = mac.trim(),
          firmwareVersion = firmware.trim().ifBlank { "ESP32-S3 v1.4" },
          batteryPercent = 100,
          isConnected = false,
          synced = false,
        )
      repository.saveOrUpdateDevice(device)
      repository.recordAdminAudit(
        adminId = adminId,
        action = "REGISTER_DEVICE",
        targetEntity = device.serialCode,
        details = "Registered belt ${device.name} (${device.macAddress})",
      )
      _uiMessage.value = "Registered wearable device ${device.serialCode}"
    }
  }

  override fun onCleared() {
    super.onCleared()
    stopRaceHardware()
    bleManager.disconnect()
    hapticManager.shutdown()
  }

  companion object {
    private const val OBSTACLE_CUE_MIN_INTERVAL_MS = 700L
    private const val OBSTACLE_RECORD_MIN_INTERVAL_MS = 1_500L
    private const val DEVIATION_REPEAT_CUE_MS = 15_000L

    fun formatDuration(totalSeconds: Long): String {
      val hrs = totalSeconds / 3600
      val mins = (totalSeconds % 3600) / 60
      val secs = totalSeconds % 60
      return if (hrs > 0) String.format("%02d:%02d:%02d", hrs, mins, secs)
      else String.format("%02d:%02d", mins, secs)
    }

    fun formatPace(secPerKm: Double): String {
      if (secPerKm <= 0.0 || secPerKm > 3600.0) return "--'--\""
      val mins = (secPerKm / 60.0).toInt()
      val secs = (secPerKm % 60.0).toInt()
      return String.format("%d'%02d\"", mins, secs)
    }
  }
}
