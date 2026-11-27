package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.model.GpsTrackPoint
import com.example.data.model.ObstacleDirection
import com.example.data.model.RaceSession
import com.example.data.model.RaceStatus
import com.example.data.model.RouteWaypoint
import com.example.hardware.BleObstaclePacket
import com.example.hardware.GpsReading
import com.example.ui.components.DirectionalMotorBadge
import com.example.ui.components.RoutePathCanvas
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BlindRunTheme
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.ObsidianNight
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.VoltLime
import com.example.ui.viewmodel.BlindRunViewModel

@Composable
fun RaceHubScreen(
  races: List<RaceSession>,
  onSaveRace: (
    existingId: String?,
    name: String,
    raceType: String,
    totalDistanceKm: Double,
    startLocation: String,
    finishLocation: String,
    waypoints: List<RouteWaypoint>,
  ) -> Unit,
  onParseRouteUri: (Uri, (List<RouteWaypoint>, Double) -> Unit) -> Unit,
  onOpenLiveRace: (String) -> Unit,
) {
  var showForm by rememberSaveable { mutableStateOf(races.isEmpty()) }
  var editingRaceId by rememberSaveable { mutableStateOf<String?>(null) }
  var name by rememberSaveable { mutableStateOf("") }
  var raceType by rememberSaveable { mutableStateOf("Long distance") }
  var distanceKmText by rememberSaveable { mutableStateOf("10.0") }
  var startLoc by rememberSaveable { mutableStateOf("") }
  var finishLoc by rememberSaveable { mutableStateOf("") }
  var waypoints by remember { mutableStateOf<List<RouteWaypoint>>(emptyList()) }

  val routePickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        onParseRouteUri(uri) { parsedPoints, distKm ->
          waypoints = parsedPoints
          if (distKm > 0.1) {
            distanceKmText = String.format("%.2f", distKm)
          }
        }
      }
    }

  val raceTypes = listOf("Sprint", "Middle distance", "Long distance", "Trail")

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text("Race Management", style = MaterialTheme.typography.headlineMedium)
          Text(
            text = "Configure race distance, GPX/TCX routes, and GPS tracking",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Button(
          onClick = {
            editingRaceId = null
            name = ""
            distanceKmText = "10.0"
            startLoc = ""
            finishLoc = ""
            waypoints = emptyList()
            showForm = !showForm
          },
          colors = ButtonDefaults.buttonColors(containerColor = VoltLime, contentColor = ObsidianNight),
          modifier = Modifier.height(48.dp).testTag("toggle_race_form_btn"),
        ) {
          Icon(Icons.Rounded.Add, contentDescription = null)
          Spacer(Modifier.size(4.dp))
          Text(if (showForm) "Close" else "New Race", fontWeight = FontWeight.Bold)
        }
      }
    }

    if (showForm) {
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
          border = BorderStroke(1.dp, VoltLime.copy(alpha = 0.5f)),
          shape = RoundedCornerShape(22.dp),
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
              text = if (editingRaceId == null) "Configure New Race" else "Edit Race Configuration",
              style = MaterialTheme.typography.titleLarge,
            )
            OutlinedTextField(
              value = name,
              onValueChange = { name = it },
              label = { Text("Race Name (e.g. Nairobi Athletics Trial)") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("race_name_input"),
            )
            Text("Race Category", style = MaterialTheme.typography.labelLarge)
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              raceTypes.forEach { cat ->
                FilterChip(
                  selected = raceType == cat,
                  onClick = { raceType = cat },
                  label = { Text(cat) },
                )
              }
            }
            OutlinedTextField(
              value = distanceKmText,
              onValueChange = { distanceKmText = it },
              label = { Text("Total Race Distance (km)") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("race_distance_input"),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              OutlinedTextField(
                value = startLoc,
                onValueChange = { startLoc = it },
                label = { Text("Starting Location") },
                singleLine = true,
                modifier = Modifier.weight(1f),
              )
              OutlinedTextField(
                value = finishLoc,
                onValueChange = { finishLoc = it },
                label = { Text("Finishing Location") },
                singleLine = true,
                modifier = Modifier.weight(1f),
              )
            }
            OutlinedButton(
              onClick = { routePickerLauncher.launch(arrayOf("*/*")) },
              modifier = Modifier.fillMaxWidth().height(50.dp).testTag("upload_gpx_btn"),
            ) {
              Icon(Icons.Rounded.UploadFile, contentDescription = null, tint = SignalCyan)
              Spacer(Modifier.size(8.dp))
              Text(
                if (waypoints.isEmpty()) "Upload GPX / TCX Route File"
                else "Route Loaded (${waypoints.size} waypoints)",
              )
            }
            Button(
              onClick = {
                val distKm = distanceKmText.toDoubleOrNull() ?: 5.0
                if (name.isNotBlank() && distKm > 0.0) {
                  onSaveRace(editingRaceId, name, raceType, distKm, startLoc, finishLoc, waypoints)
                  showForm = false
                }
              },
              colors = ButtonDefaults.buttonColors(containerColor = VoltLime, contentColor = ObsidianNight),
              modifier = Modifier.fillMaxWidth().height(52.dp).testTag("save_race_btn"),
            ) {
              Text("Save Race & Sync to Belt", fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }

    val activeRaces = races.filter { it.status != RaceStatus.COMPLETED }
    if (activeRaces.isEmpty() && !showForm) {
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(Icons.Rounded.Route, contentDescription = null, tint = VoltLime, modifier = Modifier.size(36.dp))
            Text("No Races Configured", style = MaterialTheme.typography.titleMedium)
            Text(
              text = "Tap 'New Race' above to set distance, start/finish locations, or upload a GPX/TCX route.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }

    items(activeRaces, key = { it.id }) { race ->
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(20.dp),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(race.name, style = MaterialTheme.typography.titleLarge)
              Text(
                text =
                  "${race.raceType} • ${String.format("%.1f", race.totalDistanceMeters / 1000.0)} km" +
                    if (race.waypoints.isNotEmpty()) " • ${race.waypoints.size} GPX pts" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            if (race.status == RaceStatus.CONFIGURED) {
              IconButton(
                onClick = {
                  editingRaceId = race.id
                  name = race.name
                  raceType = race.raceType
                  distanceKmText = String.format("%.2f", race.totalDistanceMeters / 1000.0)
                  startLoc = race.startLocationName
                  finishLoc = race.finishLocationName
                  waypoints = race.waypoints
                  showForm = true
                }
              ) {
                Icon(Icons.Rounded.Edit, contentDescription = "Edit race ${race.name}")
              }
            }
          }
          Button(
            onClick = { onOpenLiveRace(race.id) },
            colors = ButtonDefaults.buttonColors(containerColor = VoltLime, contentColor = ObsidianNight),
            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("open_race_${race.id}"),
          ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
            Spacer(Modifier.size(6.dp))
            Text(
              text =
                when (race.status) {
                  RaceStatus.CONFIGURED -> "Open Race & Start"
                  RaceStatus.RUNNING -> "Return to Active Run"
                  RaceStatus.PAUSED -> "Resume Paused Run"
                  RaceStatus.COMPLETED -> "View Summary"
                },
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }
    }
  }
}

@Composable
fun LiveRaceTrackingScreen(
  race: RaceSession?,
  trackPoints: List<GpsTrackPoint>,
  gpsReading: GpsReading?,
  gpsWarning: String?,
  deviationMeters: Double,
  latestObstacle: BleObstaclePacket?,
  latestHeartRateBpm: Int,
  useLightContrastHud: Boolean,
  onToggleContrast: () -> Unit,
  onStartRace: (String) -> Unit,
  onPauseRace: () -> Unit,
  onResumeRace: () -> Unit,
  onFinishRace: () -> Unit,
  onTestHaptic: (ObstacleDirection) -> Unit,
  onBack: () -> Unit,
) {
  BackHandler { onBack() }

  val context = LocalContext.current
  var hasLocationPerm by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    )
  }
  val locationLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
      hasLocationPerm = res.values.any { it }
      if (hasLocationPerm && race != null && race.status == RaceStatus.CONFIGURED) {
        onStartRace(race.id)
      }
    }

  // Screen 4 supports optional high-contrast daylight mode matching the white/orange race tracking card in reference!
  BlindRunTheme(darkTheme = !useLightContrastHud) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      if (race == null) {
        Column(
          modifier = Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text("Race not found.", style = MaterialTheme.typography.titleLarge)
          Spacer(Modifier.height(12.dp))
          Button(onClick = onBack) { Text("Return") }
        }
        return@Surface
      }

      val coveredKm = race.coveredDistanceMeters / 1000.0
      val remainingKm = race.remainingDistanceMeters / 1000.0
      val totalKm = race.totalDistanceMeters / 1000.0

      LazyColumn(
        modifier = Modifier.fillMaxSize().systemBarsPadding(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              IconButton(onClick = onBack, modifier = Modifier.testTag("live_race_back_btn")) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
              }
              Column {
                Text(race.name, style = MaterialTheme.typography.titleLarge)
                Text(
                  text = "${race.status.label} • Target ${String.format("%.2f", totalKm)} km",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            IconButton(
              onClick = onToggleContrast,
              modifier = Modifier.testTag("toggle_hud_contrast_btn"),
            ) {
              Icon(
                Icons.Rounded.Contrast,
                contentDescription = "Toggle daylight/night high contrast HUD",
              )
            }
          }
        }

        // GPS Route Canvas
        item {
          RoutePathCanvas(
            plannedWaypoints = race.waypoints,
            recordedTrack = trackPoints,
            deviationMeters = deviationMeters,
          )
        }

        // GPS Signal / Route Deviation / Obstacle Banner
        if (!gpsWarning.isNullOrBlank() || (race.waypoints.isNotEmpty() && deviationMeters > 35.0)) {
          item {
            Card(
              colors = CardDefaults.cardColors(containerColor = AmberWarning.copy(alpha = 0.18f)),
              border = BorderStroke(1.dp, AmberWarning),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = AmberWarning)
                Text(
                  text =
                    if (race.waypoints.isNotEmpty() && deviationMeters > 35.0) {
                      "Route deviation: ${deviationMeters.toInt()}m off configured route"
                    } else {
                      gpsWarning ?: ""
                    },
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurface,
                )
              }
            }
          }
        }

        if (latestObstacle != null) {
          item {
            Card(
              colors = CardDefaults.cardColors(containerColor = CoralAlert.copy(alpha = 0.2f)),
              border = BorderStroke(1.5.dp, CoralAlert),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = "OBSTACLE ${latestObstacle.direction.label.uppercase()} (${latestObstacle.distanceCm} cm)",
                  style = MaterialTheme.typography.titleMedium,
                  color = CoralAlert,
                  fontWeight = FontWeight.Bold,
                )
                Text(
                  text = "CH ${latestObstacle.direction.motorChannel}",
                  style = MaterialTheme.typography.labelLarge,
                  color = CoralAlert,
                )
              }
            }
          }
        }

        // Primary Oversized Distance Metrics (Dc and Dr = Dt - Dc)
        item {
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(24.dp),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(20.dp),
              verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
              ) {
                Column(
                  modifier =
                    Modifier.semantics {
                      contentDescription = "Distance covered ${String.format("%.2f", coveredKm)} kilometers"
                    }
                ) {
                  Text("COVERED (Dc)", style = MaterialTheme.typography.labelMedium)
                  Text(
                    text = String.format("%.2f km", coveredKm),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary,
                  )
                }
                Column(
                  horizontalAlignment = Alignment.End,
                  modifier =
                    Modifier.semantics {
                      contentDescription =
                        "Remaining distance ${String.format("%.2f", remainingKm)} kilometers"
                    },
                ) {
                  Text("REMAINING (Dr)", style = MaterialTheme.typography.labelMedium)
                  Text(
                    text = String.format("%.2f km", remainingKm),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                }
              }

              LinearProgressIndicator(
                progress = { race.progressFraction },
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                MetricCol("ELAPSED", BlindRunViewModel.formatDuration(race.elapsedSeconds))
                MetricCol("PACE", "${BlindRunViewModel.formatPace(race.avgPaceSecPerKm)} /km")
                MetricCol("SPEED", String.format("%.1f km/h", race.avgSpeedKmh))
                MetricCol("HR", if (latestHeartRateBpm > 0) "$latestHeartRateBpm bpm" else "--")
              }

              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Icon(
                  Icons.Rounded.GpsFixed,
                  contentDescription = null,
                  tint = if (gpsReading?.isPoorSignal == false) EmeraldSuccess else AmberWarning,
                  modifier = Modifier.size(16.dp),
                )
                Text(
                  text =
                    if (gpsReading != null) {
                      "GPS ±${gpsReading.accuracyMeters.toInt()}m"
                    } else {
                      "GPS standby"
                    },
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }

        // Large Accessible Transport Controls (Start / Pause / Resume / Finish)
        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            when (race.status) {
              RaceStatus.CONFIGURED -> {
                Button(
                  onClick = {
                    if (hasLocationPerm) {
                      onStartRace(race.id)
                    } else {
                      locationLauncher.launch(
                        arrayOf(
                          Manifest.permission.ACCESS_FINE_LOCATION,
                          Manifest.permission.ACCESS_COARSE_LOCATION,
                        )
                      )
                    }
                  },
                  colors = ButtonDefaults.buttonColors(containerColor = VoltLime, contentColor = ObsidianNight),
                  modifier = Modifier.weight(1f).height(62.dp).testTag("live_race_start_btn"),
                ) {
                  Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                  Spacer(Modifier.size(8.dp))
                  Text("START RACE", fontWeight = FontWeight.Bold)
                }
              }
              RaceStatus.RUNNING -> {
                Button(
                  onClick = onPauseRace,
                  colors = ButtonDefaults.buttonColors(containerColor = AmberWarning, contentColor = ObsidianNight),
                  modifier = Modifier.weight(1f).height(62.dp).testTag("live_race_pause_btn"),
                ) {
                  Icon(Icons.Rounded.Pause, contentDescription = null)
                  Spacer(Modifier.size(8.dp))
                  Text("PAUSE", fontWeight = FontWeight.Bold)
                }
                Button(
                  onClick = onFinishRace,
                  colors = ButtonDefaults.buttonColors(containerColor = CoralAlert, contentColor = MaterialTheme.colorScheme.onError),
                  modifier = Modifier.weight(1f).height(62.dp).testTag("live_race_finish_btn"),
                ) {
                  Icon(Icons.Rounded.Stop, contentDescription = null)
                  Spacer(Modifier.size(8.dp))
                  Text("FINISH", fontWeight = FontWeight.Bold)
                }
              }
              RaceStatus.PAUSED -> {
                Button(
                  onClick = onResumeRace,
                  colors = ButtonDefaults.buttonColors(containerColor = VoltLime, contentColor = ObsidianNight),
                  modifier = Modifier.weight(1f).height(62.dp).testTag("live_race_resume_btn"),
                ) {
                  Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                  Spacer(Modifier.size(8.dp))
                  Text("RESUME", fontWeight = FontWeight.Bold)
                }
                Button(
                  onClick = onFinishRace,
                  colors = ButtonDefaults.buttonColors(containerColor = CoralAlert, contentColor = MaterialTheme.colorScheme.onError),
                  modifier = Modifier.weight(1f).height(62.dp).testTag("live_race_finish_btn"),
                ) {
                  Icon(Icons.Rounded.Flag, contentDescription = null)
                  Spacer(Modifier.size(8.dp))
                  Text("FINISH", fontWeight = FontWeight.Bold)
                }
              }
              RaceStatus.COMPLETED -> {
                OutlinedButton(
                  onClick = onBack,
                  modifier = Modifier.weight(1f).height(56.dp),
                ) {
                  Text("Race Completed — Back to List")
                }
              }
            }
          }
        }

        // Quick 4-Direction Haptic Guidance Trigger
        item {
          Text("Directional Belt Haptics", style = MaterialTheme.typography.titleMedium)
          Spacer(Modifier.height(8.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            ObstacleDirection.entries.forEach { dir ->
              DirectionalMotorBadge(
                direction = dir,
                isAlertActive = latestObstacle?.direction == dir,
                onTestClick = { onTestHaptic(dir) },
                modifier = Modifier.weight(1f),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun MetricCol(label: String, value: String) {
  Column {
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
  }
}
