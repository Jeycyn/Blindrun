package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import com.example.data.model.HealthMetricRecord
import com.example.hardware.HealthConnectStatus
import com.example.ui.components.AccessibleStatCard
import com.example.ui.components.TrendSparklineChart
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.ObsidianNight
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.SkyCobalt
import com.example.ui.theme.VoltLime
import com.example.ui.viewmodel.BlindRunViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HealthScreen(
  healthStatus: HealthConnectStatus,
  healthRecords: List<HealthMetricRecord>,
  requiredPermissions: Set<String>,
  onSyncWatchData: () -> Unit,
  onRefreshPermissions: () -> Unit,
) {
  val permissionLauncher =
    rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
      onRefreshPermissions()
      onSyncWatchData()
    }

  val latest = healthRecords.firstOrNull()
  val hrValues = healthRecords.take(14).reversed().mapNotNull { it.heartRateBpm.takeIf { hr -> hr > 0 }?.toFloat() }
  val timestampText =
    latest?.let {
      DateTimeFormatter.ofPattern("MMM dd, HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(it.recordedAtEpochMs))
    } ?: "No readings yet"

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
        Column(modifier = Modifier.weight(1f)) {
          Text("Health & Vitals", style = MaterialTheme.typography.headlineMedium)
          Text(
            text =
              if (latest != null) "Source: ${latest.source} • $timestampText"
              else healthStatus.statusMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Button(
          onClick = {
            if (healthStatus.isAvailable && !healthStatus.hasPermissions) {
              permissionLauncher.launch(requiredPermissions)
            } else {
              onSyncWatchData()
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = VoltLime, contentColor = ObsidianNight),
          modifier = Modifier.height(48.dp).testTag("sync_health_btn"),
        ) {
          Icon(Icons.Rounded.Sync, contentDescription = null)
          Spacer(Modifier.size(6.dp))
          Text(
            if (healthStatus.isAvailable && !healthStatus.hasPermissions) "Grant" else "Sync",
            fontWeight = FontWeight.Bold,
          )
        }
      }
    }

    // Main Heart Rate Highlight Card (mirrors Screen 5 in reference image)
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, CoralAlert.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(22.dp),
      ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column {
              Text("LATEST HEART RATE", style = MaterialTheme.typography.labelMedium, color = CoralAlert)
              Text(
                text = if (latest != null && latest.heartRateBpm > 0) "${latest.heartRateBpm} bpm" else "-- bpm",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
            Column(horizontalAlignment = Alignment.End) {
              Text(
                text = "Avg ${latest?.avgHeartRateBpm?.takeIf { it > 0 } ?: "--"} bpm",
                style = MaterialTheme.typography.bodyMedium,
              )
              Text(
                text = "Max ${latest?.maxHeartRateBpm?.takeIf { it > 0 } ?: "--"} bpm",
                style = MaterialTheme.typography.bodyMedium,
                color = CoralAlert,
              )
            }
          }

          TrendSparklineChart(
            values = hrValues,
            accentColor = CoralAlert,
            emptyText = "Sync your smartwatch via Health Connect to chart heart rate readings.",
          )
        }
      }
    }

    // 4-Tile Vitals Grid
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        AccessibleStatCard(
          title = "Steps",
          value = latest?.steps?.toString() ?: "--",
          subtitle = "24h aggregate",
          icon = Icons.AutoMirrored.Rounded.DirectionsWalk,
          accentColor = VoltLime,
          modifier = Modifier.weight(1f),
        )
        AccessibleStatCard(
          title = "Active Burn",
          value = if (latest != null && latest.activeCalories > 0) "${latest.activeCalories} kcal" else "--",
          subtitle = "Active energy",
          icon = Icons.Rounded.LocalFireDepartment,
          accentColor = AmberWarning,
          modifier = Modifier.weight(1f),
        )
      }
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        AccessibleStatCard(
          title = "Exercise Time",
          value =
            if (latest != null && latest.exerciseDurationSec > 0) {
              BlindRunViewModel.formatDuration(latest.exerciseDurationSec)
            } else {
              "--"
            },
          subtitle = "Recorded sessions",
          icon = Icons.Rounded.Timer,
          accentColor = SignalCyan,
          modifier = Modifier.weight(1f),
        )
        AccessibleStatCard(
          title = "SpO2 / Sleep",
          value = latest?.spo2Percent?.let { "$it%" } ?: "--%",
          subtitle = latest?.sleepMinutes?.let { "Sleep: ${it / 60}h ${it % 60}m" } ?: "Optional metrics",
          icon = if (latest?.spo2Percent != null) Icons.Rounded.Air else Icons.Rounded.Bedtime,
          accentColor = SkyCobalt,
          modifier = Modifier.weight(1f),
        )
      }
    }

    // Permission / Unsupported Hardware Notice (FR-05)
    if (!healthStatus.isAvailable || !healthStatus.hasPermissions || healthStatus.unsupportedNotes.isNotEmpty()) {
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Icon(Icons.Rounded.Favorite, contentDescription = null, tint = SignalCyan)
              Text("Smartwatch Integration Status", style = MaterialTheme.typography.titleMedium)
            }
            Text(
              text = healthStatus.statusMessage,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            healthStatus.unsupportedNotes.forEach { note ->
              Text(
                text = "• $note",
                style = MaterialTheme.typography.bodyMedium,
                color = AmberWarning,
              )
            }
            if (healthStatus.isAvailable && !healthStatus.hasPermissions) {
              OutlinedButton(
                onClick = { permissionLauncher.launch(requiredPermissions) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
              ) {
                Text("Grant Health Connect Permissions")
              }
            }
          }
        }
      }
    }
  }
}
