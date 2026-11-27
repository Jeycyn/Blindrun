package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.ui.navigation.AppRoute
import com.example.ui.screens.AdminScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CoachScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DevicesScreen
import com.example.ui.screens.HealthScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.LiveRaceTrackingScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.QrScannerScreen
import com.example.ui.screens.RaceHubScreen
import com.example.ui.theme.BlindRunTheme
import com.example.ui.theme.ObsidianNight
import com.example.ui.theme.VoltLime
import com.example.ui.viewmodel.AuthGateState
import com.example.ui.viewmodel.BlindRunViewModel

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      BlindRunTheme(darkTheme = true) {
        BlindRunApp()
      }
    }
  }
}

private data class BottomTab(
  val route: AppRoute,
  val label: String,
  val icon: ImageVector,
  val tag: String,
)

@Composable
fun BlindRunApp(viewModel: BlindRunViewModel = viewModel()) {
  val authGate by viewModel.authGate.collectAsStateWithLifecycle()
  val authBusy by viewModel.authBusy.collectAsStateWithLifecycle()
  val authFeedback by viewModel.authFeedback.collectAsStateWithLifecycle()
  val uiMessage by viewModel.uiMessage.collectAsStateWithLifecycle()

  val snackbarHostState = remember { SnackbarHostState() }
  LaunchedEffect(uiMessage) {
    val msg = uiMessage
    if (!msg.isNullOrBlank()) {
      snackbarHostState.showSnackbar(msg)
      viewModel.clearUiMessage()
    }
  }

  when (val gate = authGate) {
    AuthGateState.Loading -> {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = VoltLime)
      }
    }
    AuthGateState.Unauthenticated -> {
      AuthScreen(
        isBusy = authBusy,
        feedbackMessage = authFeedback,
        isSupabaseConfigured = viewModel.isSupabaseConfigured,
        onSignIn = viewModel::signIn,
        onRegister = viewModel::register,
        onRecoverPassword = viewModel::recoverPassword,
        onContinueOffline = viewModel::continueOfflineMode,
        onDismissFeedback = viewModel::clearAuthFeedback,
      )
    }
    is AuthGateState.Authenticated -> {
      AuthenticatedShell(
        isOfflineSession = gate.session.isOfflineSession,
        viewModel = viewModel,
        snackbarHostState = snackbarHostState,
      )
    }
  }
}

@Composable
private fun AuthenticatedShell(
  isOfflineSession: Boolean,
  viewModel: BlindRunViewModel,
  snackbarHostState: SnackbarHostState,
) {
  val profile by viewModel.currentProfile.collectAsStateWithLifecycle()
  val allProfiles by viewModel.allProfiles.collectAsStateWithLifecycle()
  val athleteDevices by viewModel.athleteDevices.collectAsStateWithLifecycle()
  val allDevices by viewModel.allDevices.collectAsStateWithLifecycle()
  val athleteRaces by viewModel.athleteRaces.collectAsStateWithLifecycle()
  val allRaces by viewModel.allRaces.collectAsStateWithLifecycle()
  val healthRecords by viewModel.athleteHealthRecords.collectAsStateWithLifecycle()
  val athleteAlerts by viewModel.athleteAlerts.collectAsStateWithLifecycle()
  val allAlerts by viewModel.allAlerts.collectAsStateWithLifecycle()
  val assignments by viewModel.coachAssignments.collectAsStateWithLifecycle()
  val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()
  val pendingSyncCount by viewModel.pendingSyncCount.collectAsStateWithLifecycle()
  val syncState by viewModel.syncState.collectAsStateWithLifecycle()
  val lastSyncMessage by viewModel.lastSyncMessage.collectAsStateWithLifecycle()
  val bleState by viewModel.bleState.collectAsStateWithLifecycle()
  val bleScannedDevices by viewModel.bleScannedDevices.collectAsStateWithLifecycle()
  val healthStatus by viewModel.healthStatus.collectAsStateWithLifecycle()
  val activeRace by viewModel.activeRace.collectAsStateWithLifecycle()
  val activeTrackPoints by viewModel.activeRaceTrackPoints.collectAsStateWithLifecycle()
  val gpsReading by viewModel.gpsReading.collectAsStateWithLifecycle()
  val gpsWarning by viewModel.gpsWarning.collectAsStateWithLifecycle()
  val deviationMeters by viewModel.routeDeviationMeters.collectAsStateWithLifecycle()
  val latestObstacle by viewModel.latestObstacle.collectAsStateWithLifecycle()
  val lightRaceHud by viewModel.lightRaceHud.collectAsStateWithLifecycle()

  val currentProfile = profile
  if (currentProfile != null && !currentProfile.onboardingCompleted) {
    OnboardingScreen(
      profile = currentProfile,
      onTestHaptic = viewModel::testDirectionalHaptic,
      onCompleteOnboarding = { updated -> viewModel.updateProfile(updated, markOnboardingDone = true) },
    )
    return
  }

  val navController = rememberNavController()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentDest = navBackStackEntry?.destination

  val tabs =
    listOf(
      BottomTab(AppRoute.Dashboard, "Home", Icons.Rounded.Home, "nav_home"),
      BottomTab(AppRoute.Devices, "Belt", Icons.Rounded.Bluetooth, "nav_devices"),
      BottomTab(AppRoute.RaceHub, "Race", Icons.AutoMirrored.Rounded.DirectionsRun, "nav_race"),
      BottomTab(AppRoute.Health, "Health", Icons.Rounded.Favorite, "nav_health"),
      BottomTab(AppRoute.History, "History", Icons.Rounded.History, "nav_history"),
      BottomTab(AppRoute.Profile, "Profile", Icons.Rounded.Person, "nav_profile"),
    )

  val showPrimaryChrome =
    currentDest?.let { dest ->
      tabs.any { dest.hasRoute(it.route::class) }
    } ?: true

  val isExpandedWidth = LocalConfiguration.current.screenWidthDp >= 720

  fun navigateTab(route: AppRoute) {
    navController.navigate(route) {
      popUpTo(navController.graph.findStartDestination().id) { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }

  Row(modifier = Modifier.fillMaxSize()) {
    if (showPrimaryChrome && isExpandedWidth) {
      NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
        tabs.forEach { tab ->
          val selected = currentDest?.hasRoute(tab.route::class) == true
          NavigationRailItem(
            selected = selected,
            onClick = { navigateTab(tab.route) },
            icon = { Icon(tab.icon, contentDescription = tab.label) },
            label = { Text(tab.label) },
            modifier = Modifier.testTag(tab.tag),
          )
        }
      }
    }

    Scaffold(
      modifier = Modifier.weight(1f),
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
      bottomBar = {
        if (showPrimaryChrome && !isExpandedWidth) {
          NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            tabs.forEach { tab ->
              val selected = currentDest?.hasRoute(tab.route::class) == true
              NavigationBarItem(
                selected = selected,
                onClick = { navigateTab(tab.route) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
                modifier = Modifier.testTag(tab.tag),
                colors =
                  NavigationBarItemDefaults.colors(
                    selectedIconColor = ObsidianNight,
                    selectedTextColor = VoltLime,
                    indicatorColor = VoltLime,
                  ),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      NavHost(
        navController = navController,
        startDestination = AppRoute.Dashboard,
        modifier = Modifier.fillMaxSize().padding(innerPadding),
      ) {
        composable<AppRoute.Dashboard> {
          DashboardScreen(
            profile = profile,
            bleState = bleState,
            pairedDevices = athleteDevices,
            races = athleteRaces,
            healthRecords = healthRecords,
            alerts = athleteAlerts,
            syncState = syncState,
            pendingSyncCount = pendingSyncCount,
            onSyncNow = viewModel::syncNow,
            onOpenDevices = { navigateTab(AppRoute.Devices) },
            onOpenQrScanner = { navController.navigate(AppRoute.QrScanner) },
            onOpenRaceHub = { navigateTab(AppRoute.RaceHub) },
            onStartOrResumeRace = { raceId ->
              viewModel.selectActiveRace(raceId)
              navController.navigate(AppRoute.LiveRace(raceId))
            },
            onOpenHealth = { navigateTab(AppRoute.Health) },
            onOpenCoachPortal = { navController.navigate(AppRoute.CoachDashboard) },
            onOpenAdminPortal = { navController.navigate(AppRoute.AdminDashboard) },
            onAcknowledgeAlert = viewModel::acknowledgeAlert,
          )
        }

        composable<AppRoute.Devices> {
          DevicesScreen(
            bleState = bleState,
            scannedDevices = bleScannedDevices,
            pairedDevices = athleteDevices,
            hasBlePermissions = viewModel.bleManager.hasRequiredPermissions(),
            latestObstacleDirection = latestObstacle?.direction,
            onStartBleScan = { viewModel.bleManager.startScan() },
            onStopBleScan = { viewModel.bleManager.stopScan() },
            onConnectDevice = viewModel::connectBleDevice,
            onDisconnect = { viewModel.bleManager.disconnect() },
            onOpenQrScanner = { navController.navigate(AppRoute.QrScanner) },
            onTestHapticMotor = viewModel::testDirectionalHaptic,
          )
        }

        composable<AppRoute.QrScanner> {
          QrScannerScreen(
            onQrDecoded = viewModel::handleScannedBeltQr,
            onBack = { navController.popBackStack() },
          )
        }

        composable<AppRoute.RaceHub> {
          RaceHubScreen(
            races = athleteRaces,
            onSaveRace = { existingId, name, type, distKm, startLoc, finishLoc, waypoints ->
              viewModel.createOrUpdateRace(
                existingId = existingId,
                name = name,
                raceType = type,
                totalDistanceKm = distKm,
                startLocation = startLoc,
                finishLocation = finishLoc,
                waypoints = waypoints,
              )
            },
            onParseRouteUri = viewModel::parseRouteFile,
            onOpenLiveRace = { raceId ->
              viewModel.selectActiveRace(raceId)
              navController.navigate(AppRoute.LiveRace(raceId))
            },
          )
        }

        composable<AppRoute.LiveRace> { entry ->
          val args = entry.toRoute<AppRoute.LiveRace>()
          LaunchedEffect(args.raceId) { viewModel.selectActiveRace(args.raceId) }
          val latestHr = healthRecords.firstOrNull()?.heartRateBpm ?: 0
          LiveRaceTrackingScreen(
            race = activeRace,
            trackPoints = activeTrackPoints,
            gpsReading = gpsReading,
            gpsWarning = gpsWarning,
            deviationMeters = deviationMeters,
            latestObstacle = latestObstacle,
            latestHeartRateBpm = latestHr,
            useLightContrastHud = lightRaceHud,
            onToggleContrast = viewModel::toggleRaceHudContrast,
            onStartRace = viewModel::startRace,
            onPauseRace = viewModel::pauseRace,
            onResumeRace = viewModel::resumeRace,
            onFinishRace = viewModel::finishRace,
            onTestHaptic = viewModel::testDirectionalHaptic,
            onBack = { navController.popBackStack() },
          )
        }

        composable<AppRoute.Health> {
          HealthScreen(
            healthStatus = healthStatus,
            healthRecords = healthRecords,
            requiredPermissions = viewModel.healthManager.requiredPermissions,
            onSyncWatchData = viewModel::syncSmartwatchHealthData,
            onRefreshPermissions = viewModel::refreshHealthConnectStatus,
          )
        }

        composable<AppRoute.History> {
          HistoryScreen(
            races = athleteRaces,
            syncState = syncState,
            pendingSyncCount = pendingSyncCount,
            lastSyncMessage = lastSyncMessage,
            onSyncNow = viewModel::syncNow,
            onOpenRace = { raceId ->
              viewModel.selectActiveRace(raceId)
              navController.navigate(AppRoute.LiveRace(raceId))
            },
          )
        }

        composable<AppRoute.Profile> {
          ProfileScreen(
            profile = profile,
            isOfflineSession = isOfflineSession,
            onSaveProfile = { updated -> viewModel.updateProfile(updated) },
            onOpenCoachPortal = { navController.navigate(AppRoute.CoachDashboard) },
            onOpenAdminPortal = { navController.navigate(AppRoute.AdminDashboard) },
            onSignOut = viewModel::signOut,
          )
        }

        composable<AppRoute.CoachDashboard> {
          CoachScreen(
            coachProfile = profile,
            allProfiles = allProfiles,
            assignments = assignments,
            allRaces = allRaces,
            healthRecords = healthRecords,
            alerts = allAlerts,
            onCreateTrainingRace = { athleteId, name, distKm ->
              viewModel.createOrUpdateRace(
                targetAthleteId = athleteId,
                name = name,
                raceType = "Coach Training",
                totalDistanceKm = distKm,
                startLocation = "Assigned Start",
                finishLocation = "Assigned Finish",
                waypoints = emptyList(),
              )
            },
            onBack = { navController.popBackStack() },
          )
        }

        composable<AppRoute.AdminDashboard> {
          AdminScreen(
            profiles = allProfiles,
            devices = allDevices,
            races = allRaces,
            assignments = assignments,
            auditLogs = auditLogs,
            onUpdateUserRole = viewModel::updateUserRoleByAdmin,
            onAssignCoach = viewModel::assignCoachToAthlete,
            onRemoveAssignment = viewModel::unassignCoach,
            onRegisterDevice = viewModel::registerDeviceByAdmin,
            onBack = { navController.popBackStack() },
          )
        }
      }
    }
  }
}
