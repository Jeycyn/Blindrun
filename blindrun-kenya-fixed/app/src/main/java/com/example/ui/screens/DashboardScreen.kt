package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AppAlert
import com.example.data.model.HealthMetricRecord
import com.example.data.model.RaceSession
import com.example.data.model.RaceStatus
import com.example.data.model.SyncState
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.model.WearableDevice
import com.example.hardware.BleConnectionState
import com.example.ui.components.AccessibleStatCard
import com.example.ui.components.SyncStatusPill
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.ObsidianNight
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.VoltLime
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun DashboardScreen(
  profile: UserProfile?,
  bleState: BleConnectionState,
  pairedDevices: List<WearableDevice>,
  races: List<RaceSession>,
  healthRecords: List<HealthMetricRecord>,
  alerts: List<AppAlert>,
  syncState: SyncState,
  pendingSyncCount: Int,
  onSyncNow: () -> Unit,
  onOpenDevices: () -> Unit,
  onOpenQrScanner: () -> Unit,
  onOpenRaceHub: () -> Unit,
  onStartOrResumeRace: (String) -> Unit,
  onOpenHealth: () -> Unit,
  onOpenCoachPortal: () -> Unit,
  onOpenAdminPortal: () -> Unit,
  onAcknowledgeAlert: (String) -> Unit,
) {
  val latestHealth = healthRecords.firstOrNull()
  val activeOrNextRace =
    races.firstOrNull { it.status == RaceStatus.RUNNING || it.status == RaceStatus.PAUSED }
      ?: races.firstOrNull { it.status == RaceStatus.CONFIGURED }
  val completedCount = races.count { it.status == RaceStatus.COMPLETED }
  val activeAlerts = alerts.filter { !it.acknowledged }.take(3)

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    // Top Header
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Jambo, ${profile?.fullName?.substringBefore(" ") ?: "Runner"}",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
          )
          Text(
            text = "${profile?.accessibilityMode?.label ?: "Athlete"} • ${profile?.role?.label ?: "Athlete"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        SyncStatusPill(
          syncState = syncState,
          pendingCount = pendingSyncCount,
          onClick = onSyncNow,
        )
      }
    }

    // Weekly Calendar Strip (mirrors Screen 2 in reference image)
    item { WeeklyReadinessStrip(completedRaces = completedCount) }

    // Role Shortcut for Coach / Admin
    if (profile?.role == UserRole.COACH || profile?.role == UserRole.ADMIN) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          if (profile.role == UserRole.COACH || profile.role == UserRole.ADMIN) {
            OutlinedButton(
              onClick = onOpenCoachPortal,
              modifier = Modifier.weight(1f).height(50.dp).testTag("open_coach_portal_btn"),
              border = BorderStroke(1.5.dp, SignalCyan),
            ) {
              Icon(Icons.Rounded.Groups, contentDescription = null, tint = SignalCyan)
              Spacer(Modifier.size(8.dp))
              Text("Coach Desk", color = SignalCyan)
            }
          }
          if (profile.role == UserRole.ADMIN) {
            OutlinedButton(
              onClick = onOpenAdminPortal,
              modifier = Modifier.weight(1f).height(50.dp).testTag("open_admin_portal_btn"),
              border = BorderStroke(1.5.dp, VoltLime),
            ) {
              Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null, tint = VoltLime)
              Spacer(Modifier.size(8.dp))
              Text("Admin Console", color = VoltLime)
            }
          }
        }
      }
    }

    // Active / Configured Race Card
    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.5.dp, VoltLime.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(22.dp),
      ) {
        Column(
          modifier = Modifier.padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Icon(Icons.AutoMirrored.Rounded.DirectionsRun, contentDescription = null, tint = VoltLime)
              Text(
                text =
                  if (activeOrNextRace != null) activeOrNextRace.name
                  else "Ready for Your Next Run",
                style = MaterialTheme.typography.titleLarge,
              )
            }
            Surface(
              shape = CircleShape,
              color = VoltLime.copy(alpha = 0.16f),
            ) {
              Text(
                text = activeOrNextRace?.status?.label ?: "New",
                style = MaterialTheme.typography.labelMedium,
                color = VoltLime,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
              )
            }
          }

          if (activeOrNextRace != null) {
            val totalKm = activeOrNextRace.totalDistanceMeters / 1000.0
            val coveredKm = activeOrNextRace.coveredDistanceMeters / 1000.0
            val remainingKm = activeOrNextRace.remainingDistanceMeters / 1000.0
            Text(
              text =
                "${String.format("%.2f", coveredKm)} km covered • ${String.format("%.2f", remainingKm)} km remaining (Target ${String.format("%.1f", totalKm)} km)",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
              progress = { activeOrNextRace.progressFraction },
              modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
              color = VoltLime,
              trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
            )
            Button(
              onClick = { onStartOrResumeRace(activeOrNextRace.id) },
              colors = ButtonDefaults.buttonColors(containerColor = VoltLime, contentColor = ObsidianNight),
              modifier = Modifier.fillMaxWidth().height(52.dp).testTag("dashboard_launch_race_btn"),
            ) {
              Icon(Icons.Rounded.PlayArrow, contentDescription = null)
              Spacer(Modifier.size(6.dp))
              Text(
                text =
                  if (activeOrNextRace.status == RaceStatus.CONFIGURED) "Start Race Tracking"
                  else "Open Live Race HUD",
                fontWeight = FontWeight.Bold,
              )
            }
          } else {
            Text(
              text =
                "Configure a race distance, starting point, and GPX/TCX route to pair with your ESP32 waist belt.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
              onClick = onOpenRaceHub,
              colors = ButtonDefaults.buttonColors(containerColor = VoltLime, contentColor = ObsidianNight),
              modifier = Modifier.fillMaxWidth().height(52.dp).testTag("dashboard_configure_race_btn"),
            ) {
              Text("Configure New Race", fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }

    // Two-column Device & Health Summary Grid
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        val beltSubtitle =
          when {
            bleState.isConnected -> "Battery ${bleState.batteryPercent ?: "--"}% • Armed"
            bleState.isReconnecting -> "Auto-reconnecting..."
            pairedDevices.isNotEmpty() -> "Paired: ${pairedDevices.first().serialCode}"
            else -> "Tap to scan QR / BLE"
          }
        AccessibleStatCard(
          title = "Waist Belt",
          value = if (bleState.isConnected) "Online" else "Standby",
          subtitle = beltSubtitle,
          icon =
            if (bleState.isConnected) Icons.Rounded.BluetoothConnected
            else Icons.Rounded.BluetoothDisabled,
          accentColor = if (bleState.isConnected) EmeraldSuccess else AmberWarning,
          modifier = Modifier.weight(1f).testTag("dashboard_belt_card"),
          onClick = onOpenDevices,
        )

        AccessibleStatCard(
          title = "Heart Rate",
          value = if (latestHealth != null && latestHealth.heartRateBpm > 0) "${latestHealth.heartRateBpm} bpm" else "-- bpm",
          subtitle = latestHealth?.source ?: "Sync Health Connect",
          icon = Icons.Rounded.Favorite,
          accentColor = CoralAlert,
          modifier = Modifier.weight(1f).testTag("dashboard_health_card"),
          onClick = onOpenHealth,
        )
      }
    }

    // Quick Hardware Actions
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        OutlinedButton(
          onClick = onOpenQrScanner,
          modifier = Modifier.weight(1f).height(50.dp).testTag("dashboard_qr_btn"),
        ) {
          Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, tint = SignalCyan)
          Spacer(Modifier.size(8.dp))
          Text("Scan Belt QR")
        }
        OutlinedButton(
          onClick = onOpenHealth,
          modifier = Modifier.weight(1f).height(50.dp).testTag("dashboard_watch_btn"),
        ) {
          Icon(Icons.Rounded.Watch, contentDescription = null, tint = VoltLime)
          Spacer(Modifier.size(8.dp))
          Text("Watch Vitals")
        }
      }
    }

    // Belt Battery & Safety Alerts
    if (bleState.batteryPercent != null && bleState.batteryPercent <= 20) {
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = AmberWarning.copy(alpha = 0.16f)),
          border = BorderStroke(1.dp, AmberWarning),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(Icons.Rounded.BatteryChargingFull, contentDescription = null, tint = AmberWarning)
            Text(
              text = "Low Waist Belt Battery (${bleState.batteryPercent}%). Charge ESP32 pack before long distance trials.",
              style = MaterialTheme.typography.bodyMedium,
              color = AmberWarning,
            )
          }
        }
      }
    }

    if (activeAlerts.isNotEmpty()) {
      item {
        Text(
          text = "Safety & System Alerts",
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onBackground,
        )
      }
      items(activeAlerts, key = { it.id }) { alert ->
        Card(
          onClick = { onAcknowledgeAlert(alert.id) },
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
          border = BorderStroke(1.dp, CoralAlert.copy(alpha = 0.45f)),
          modifier =
            Modifier.fillMaxWidth().semantics {
              contentDescription = "Alert: ${alert.title}. ${alert.message}. Tap to acknowledge."
            },
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = CoralAlert)
            Column(modifier = Modifier.weight(1f)) {
              Text(alert.title, style = MaterialTheme.typography.titleMedium)
              Text(
                alert.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun WeeklyReadinessStrip(completedRaces: Int) {
  val today = LocalDate.now()
  val startOfWeek = today.with(DayOfWeek.MONDAY)
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    shape = RoundedCornerShape(18.dp),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text("This Week", style = MaterialTheme.typography.labelLarge)
        Text(
          text = "$completedRaces completed runs",
          style = MaterialTheme.typography.labelMedium,
          color = VoltLime,
        )
      }
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        for (i in 0..6) {
          val day = startOfWeek.plusDays(i.toLong())
          val isToday = day == today
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
              Modifier.clip(RoundedCornerShape(12.dp))
                .background(if (isToday) VoltLime else MaterialTheme.colorScheme.surface)
                .padding(horizontal = 10.dp, vertical = 8.dp),
          ) {
            Text(
              text = day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2),
              style = MaterialTheme.typography.labelSmall,
              color = if (isToday) ObsidianNight else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = day.dayOfMonth.toString(),
              style = MaterialTheme.typography.titleMedium,
              color = if (isToday) ObsidianNight else MaterialTheme.colorScheme.onSurface,
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }
    }
  }
}
