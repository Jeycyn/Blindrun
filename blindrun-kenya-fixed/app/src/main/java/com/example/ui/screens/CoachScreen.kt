package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddTask
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.AppAlert
import com.example.data.model.CoachAssignment
import com.example.data.model.HealthMetricRecord
import com.example.data.model.RaceSession
import com.example.data.model.RaceStatus
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.ui.components.AccessibleStatCard
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.ObsidianNight
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.VoltLime
import com.example.ui.viewmodel.BlindRunViewModel

@Composable
fun CoachScreen(
  coachProfile: UserProfile?,
  allProfiles: List<UserProfile>,
  assignments: List<CoachAssignment>,
  allRaces: List<RaceSession>,
  healthRecords: List<HealthMetricRecord>,
  alerts: List<AppAlert>,
  onCreateTrainingRace: (athleteId: String, name: String, distanceKm: Double) -> Unit,
  onBack: () -> Unit,
) {
  BackHandler { onBack() }

  val coachId = coachProfile?.id ?: ""
  val assignedIds =
    if (coachProfile?.role == UserRole.ADMIN) {
      allProfiles.filter { it.role == UserRole.ATHLETE }.map { it.id }.toSet()
    } else {
      assignments.filter { it.coachId == coachId }.map { it.athleteId }.toSet()
    }

  val assignedAthletes = allProfiles.filter { it.id in assignedIds }
  val authorizedRaces = allRaces.filter { it.athleteId in assignedIds }
  val activeRaces = authorizedRaces.filter { it.status == RaceStatus.RUNNING || it.status == RaceStatus.PAUSED }
  val authorizedAlerts = alerts.filter { it.athleteId in assignedIds && !it.acknowledged }

  var selectedAthleteId by rememberSaveable { mutableStateOf(assignedAthletes.firstOrNull()?.id ?: "") }
  var sessionName by rememberSaveable { mutableStateOf("") }
  var sessionKm by rememberSaveable { mutableStateOf("5.0") }

  LazyColumn(
    modifier = Modifier.fillMaxSize().systemBarsPadding(),
    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(onClick = onBack, modifier = Modifier.testTag("coach_back_btn")) {
          Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
        Column {
          Text("Coach Monitoring Desk", style = MaterialTheme.typography.headlineMedium)
          Text(
            text = "Authorized athletes, live race telemetry & training setup",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        AccessibleStatCard(
          title = "Assigned",
          value = "${assignedAthletes.size}",
          subtitle = "Authorized athletes",
          icon = Icons.Rounded.Person,
          accentColor = SignalCyan,
          modifier = Modifier.weight(1f),
        )
        AccessibleStatCard(
          title = "Live Runs",
          value = "${activeRaces.size}",
          subtitle = "${authorizedAlerts.size} open alert(s)",
          icon = Icons.Rounded.NotificationsActive,
          accentColor = if (authorizedAlerts.isNotEmpty()) AmberWarning else VoltLime,
          modifier = Modifier.weight(1f),
        )
      }
    }

    // Assigned Athletes & Consented Health Summary (FR-11, AC-12)
    item {
      Text("Assigned Athletes", style = MaterialTheme.typography.titleMedium)
    }

    if (assignedAthletes.isEmpty()) {
      item {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
          Text(
            text = "No athletes assigned to your coach account yet. Administrators can link athletes in the Admin Console.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
          )
        }
      }
    } else {
      items(assignedAthletes, key = { it.id }) { athlete ->
        val athleteRaces = authorizedRaces.filter { it.athleteId == athlete.id }
        val latestRace = athleteRaces.firstOrNull()
        val consentedHealth =
          if (athlete.consentedCoachSharing) healthRecords.firstOrNull { it.athleteId == athlete.id }
          else null

        Card(
          onClick = { selectedAthleteId = athlete.id },
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
          border =
            BorderStroke(
              1.dp,
              if (selectedAthleteId == athlete.id) VoltLime else MaterialTheme.colorScheme.outline,
            ),
          shape = RoundedCornerShape(18.dp),
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(athlete.fullName, style = MaterialTheme.typography.titleMedium)
              Text(
                text = athlete.accessibilityMode.label,
                style = MaterialTheme.typography.labelMedium,
                color = SignalCyan,
              )
            }
            if (latestRace != null) {
              Text(
                text =
                  "Latest: ${latestRace.name} (${latestRace.status.label}) • ${String.format("%.2f", latestRace.coveredDistanceMeters / 1000.0)} km • Pace ${BlindRunViewModel.formatPace(latestRace.avgPaceSecPerKm)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            } else {
              Text("No races recorded yet", style = MaterialTheme.typography.bodyMedium)
            }
            if (!athlete.consentedCoachSharing) {
              Text(
                text = "Health sharing not consented by athlete",
                style = MaterialTheme.typography.labelMedium,
                color = AmberWarning,
              )
            } else if (consentedHealth != null) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.Favorite, contentDescription = null, tint = CoralAlert, modifier = Modifier.size(16.dp))
                Text(
                  text = "HR ${consentedHealth.heartRateBpm} bpm • ${consentedHealth.steps} steps",
                  style = MaterialTheme.typography.labelMedium,
                )
              }
            }
          }
        }
      }
    }

    // Configure Training / Race Session for Selected Athlete
    if (assignedAthletes.isNotEmpty()) {
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
          shape = RoundedCornerShape(20.dp),
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Icon(Icons.Rounded.AddTask, contentDescription = null, tint = VoltLime)
              Text("Assign Training / Race Session", style = MaterialTheme.typography.titleMedium)
            }
            val targetName =
              assignedAthletes.firstOrNull { it.id == selectedAthleteId }?.fullName
                ?: assignedAthletes.first().fullName
            Text(
              text = "Target Athlete: $targetName",
              style = MaterialTheme.typography.labelLarge,
              color = SignalCyan,
            )
            OutlinedTextField(
              value = sessionName,
              onValueChange = { sessionName = it },
              label = { Text("Training / Trial Name") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
              value = sessionKm,
              onValueChange = { sessionKm = it },
              label = { Text("Distance (km)") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
            Button(
              onClick = {
                val target = selectedAthleteId.ifBlank { assignedAthletes.first().id }
                val km = sessionKm.toDoubleOrNull() ?: 5.0
                if (sessionName.isNotBlank() && km > 0.0) {
                  onCreateTrainingRace(target, sessionName, km)
                  sessionName = ""
                }
              },
              colors = ButtonDefaults.buttonColors(containerColor = VoltLime, contentColor = ObsidianNight),
              modifier = Modifier.fillMaxWidth().height(48.dp).testTag("coach_assign_session_btn"),
            ) {
              Text("Assign Session to Athlete", fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }

    if (authorizedAlerts.isNotEmpty()) {
      item {
        Text("Authorized Athlete Alerts", style = MaterialTheme.typography.titleMedium)
      }
      items(authorizedAlerts, key = { it.id }) { alert ->
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
          border = BorderStroke(1.dp, CoralAlert.copy(alpha = 0.5f)),
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Text(alert.title, style = MaterialTheme.typography.titleMedium, color = CoralAlert)
            Text(alert.message, style = MaterialTheme.typography.bodyMedium)
          }
        }
      }
    }
  }
}
