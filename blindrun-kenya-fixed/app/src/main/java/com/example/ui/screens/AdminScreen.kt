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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.People
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.example.data.model.AdminAuditLog
import com.example.data.model.CoachAssignment
import com.example.data.model.RaceSession
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.model.WearableDevice
import com.example.ui.components.AccessibleStatCard
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.ObsidianNight
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.VoltLime

@Composable
fun AdminScreen(
  profiles: List<UserProfile>,
  devices: List<WearableDevice>,
  races: List<RaceSession>,
  assignments: List<CoachAssignment>,
  auditLogs: List<AdminAuditLog>,
  onUpdateUserRole: (UserProfile, UserRole) -> Unit,
  onAssignCoach: (coach: UserProfile, athlete: UserProfile) -> Unit,
  onRemoveAssignment: (CoachAssignment) -> Unit,
  onRegisterDevice: (serial: String, name: String, mac: String, fw: String) -> Unit,
  onBack: () -> Unit,
) {
  BackHandler { onBack() }

  val coaches = profiles.filter { it.role == UserRole.COACH }
  val athletes = profiles.filter { it.role == UserRole.ATHLETE }

  var selectedCoachId by rememberSaveable { mutableStateOf(coaches.firstOrNull()?.id ?: "") }
  var selectedAthleteId by rememberSaveable { mutableStateOf(athletes.firstOrNull()?.id ?: "") }

  var deviceSerial by rememberSaveable { mutableStateOf("") }
  var deviceName by rememberSaveable { mutableStateOf("") }
  var deviceMac by rememberSaveable { mutableStateOf("") }
  var deviceFw by rememberSaveable { mutableStateOf("ESP32-S3 v1.4") }

  LazyColumn(
    modifier = Modifier.fillMaxSize().systemBarsPadding(),
    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(onClick = onBack, modifier = Modifier.testTag("admin_back_btn")) {
          Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
        Column {
          Text("System Administration", style = MaterialTheme.typography.headlineMedium)
          Text(
            text = "Users, role permissions, coach assignments & hardware registry",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    // Organizational Report Summary (FR-12)
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        AccessibleStatCard(
          title = "Accounts",
          value = "${profiles.size}",
          subtitle = "${athletes.size} athletes • ${coaches.size} coaches",
          icon = Icons.Rounded.People,
          accentColor = VoltLime,
          modifier = Modifier.weight(1f),
        )
        AccessibleStatCard(
          title = "Hardware & Runs",
          value = "${devices.size} / ${races.size}",
          subtitle = "Belts & recorded events",
          icon = Icons.Rounded.Devices,
          accentColor = SignalCyan,
          modifier = Modifier.weight(1f),
        )
      }
    }

    // Coach-Athlete Assignment Builder
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(20.dp),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.GroupAdd, contentDescription = null, tint = VoltLime)
            Text("Assign Coach to Athlete", style = MaterialTheme.typography.titleMedium)
          }
          if (coaches.isEmpty() || athletes.isEmpty()) {
            Text(
              text = "Need at least one Coach and one Athlete profile to create an assignment.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          } else {
            Text("Select Coach", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              coaches.forEach { coach ->
                FilterChip(
                  selected = selectedCoachId == coach.id,
                  onClick = { selectedCoachId = coach.id },
                  label = { Text(coach.fullName) },
                )
              }
            }
            Text("Select Athlete", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              athletes.forEach { athlete ->
                FilterChip(
                  selected = selectedAthleteId == athlete.id,
                  onClick = { selectedAthleteId = athlete.id },
                  label = { Text(athlete.fullName) },
                )
              }
            }
            Button(
              onClick = {
                val c = coaches.firstOrNull { it.id == selectedCoachId } ?: coaches.first()
                val a = athletes.firstOrNull { it.id == selectedAthleteId } ?: athletes.first()
                onAssignCoach(c, a)
              },
              colors = ButtonDefaults.buttonColors(containerColor = VoltLime, contentColor = ObsidianNight),
              modifier = Modifier.fillMaxWidth().height(48.dp).testTag("admin_assign_coach_btn"),
            ) {
              Text("Link Coach & Athlete", fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }

    if (assignments.isNotEmpty()) {
      item { Text("Active Coach Assignments", style = MaterialTheme.typography.titleMedium) }
      items(assignments, key = { it.id }) { item ->
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("${item.coachName} -> ${item.athleteName}", style = MaterialTheme.typography.bodyLarge)
            IconButton(onClick = { onRemoveAssignment(item) }) {
              Icon(Icons.Rounded.Delete, contentDescription = "Remove assignment", tint = CoralAlert)
            }
          }
        }
      }
    }

    // Register Wearable Waist Belt
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(20.dp),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.Bluetooth, contentDescription = null, tint = SignalCyan)
            Text("Register ESP32 Waist Belt", style = MaterialTheme.typography.titleMedium)
          }
          OutlinedTextField(
            value = deviceSerial,
            onValueChange = { deviceSerial = it },
            label = { Text("Serial Code (e.g. BRK-BELT-01)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = deviceName,
            onValueChange = { deviceName = it },
            label = { Text("Device Label") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = deviceMac,
            onValueChange = { deviceMac = it },
            label = { Text("BLE MAC Address (e.g. AA:BB:CC:DD:EE:FF)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = deviceFw,
            onValueChange = { deviceFw = it },
            label = { Text("Firmware Version") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          Button(
            onClick = {
              if (deviceSerial.isNotBlank() && deviceName.isNotBlank()) {
                onRegisterDevice(deviceSerial, deviceName, deviceMac, deviceFw)
                deviceSerial = ""
                deviceName = ""
                deviceMac = ""
              }
            },
            colors = ButtonDefaults.buttonColors(containerColor = SignalCyan, contentColor = ObsidianNight),
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("admin_register_device_btn"),
          ) {
            Text("Add to Device Registry", fontWeight = FontWeight.Bold)
          }
        }
      }
    }

    // Manage User Roles & Permissions
    item { Text("User Accounts & Roles", style = MaterialTheme.typography.titleMedium) }
    items(profiles, key = { it.id }) { user ->
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(user.fullName, style = MaterialTheme.typography.titleMedium)
          Text(user.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UserRole.entries.forEach { role ->
              FilterChip(
                selected = user.role == role,
                onClick = { onUpdateUserRole(user, role) },
                label = { Text(role.label) },
              )
            }
          }
        }
      }
    }

    // Admin Activity Audit Logs
    if (auditLogs.isNotEmpty()) {
      item {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(Icons.Rounded.History, contentDescription = null, tint = VoltLime)
          Text("Administrative Activity Log", style = MaterialTheme.typography.titleMedium)
        }
      }
      items(auditLogs.take(15), key = { it.id }) { log ->
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
          Column(modifier = Modifier.padding(12.dp)) {
            Text("${log.action} • ${log.targetEntity}", style = MaterialTheme.typography.labelLarge, color = VoltLime)
            Text(log.details, style = MaterialTheme.typography.bodyMedium)
          }
        }
      }
    }
  }
}
