package com.example.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.example.data.model.RaceSession
import com.example.data.model.RaceStatus
import com.example.data.model.SyncState
import com.example.ui.components.AccessibleStatCard
import com.example.ui.components.TrendSparklineChart
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.ObsidianNight
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.VoltLime
import com.example.ui.viewmodel.BlindRunViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class HistoryTab(val label: String) {
  SUMMARY("Summary"),
  PERFORMANCE("Performance"),
  HISTORY("History"),
}

@Composable
fun HistoryScreen(
  races: List<RaceSession>,
  syncState: SyncState,
  pendingSyncCount: Int,
  lastSyncMessage: String,
  onSyncNow: () -> Unit,
  onOpenRace: (String) -> Unit,
) {
  var selectedTab by rememberSaveable { mutableStateOf(HistoryTab.SUMMARY) }

  val completedRaces = races.filter { it.status == RaceStatus.COMPLETED }
  val totalKm = completedRaces.sumOf { it.coveredDistanceMeters } / 1000.0
  val bestPaceSec =
    completedRaces.mapNotNull { it.avgPaceSecPerKm.takeIf { p -> p > 0.0 } }.minOrNull() ?: 0.0
  val avgPaceSec =
    completedRaces.mapNotNull { it.avgPaceSecPerKm.takeIf { p -> p > 0.0 } }.average().let {
      if (it.isNaN()) 0.0 else it
    }
  val paceSeries =
    completedRaces.take(12).reversed().mapNotNull {
      it.avgSpeedKmh.takeIf { s -> s > 0.0 }?.toFloat()
    }

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
          Text("Race History", style = MaterialTheme.typography.headlineMedium)
          Text(
            text = lastSyncMessage.ifBlank { "$pendingSyncCount record(s) queued for Supabase" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Button(
          onClick = onSyncNow,
          colors = ButtonDefaults.buttonColors(containerColor = VoltLime, contentColor = ObsidianNight),
          modifier = Modifier.height(48.dp).testTag("history_sync_now_btn"),
        ) {
          Icon(Icons.Rounded.CloudSync, contentDescription = null)
          Spacer(Modifier.size(6.dp))
          Text("Sync", fontWeight = FontWeight.Bold)
        }
      }
    }

    // Segmented Tabs (mirrors Screen 6 in reference image: Summary | Performance | History)
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        HistoryTab.entries.forEach { tab ->
          FilterChip(
            selected = selectedTab == tab,
            onClick = { selectedTab = tab },
            label = { Text(tab.label) },
            colors =
              FilterChipDefaults.filterChipColors(
                selectedContainerColor = VoltLime,
                selectedLabelColor = ObsidianNight,
              ),
          )
        }
      }
    }

    if (selectedTab != HistoryTab.HISTORY) {
      item {
        TrendSparklineChart(
          values = paceSeries,
          accentColor = VoltLime,
          emptyText = "Complete a race to plot speed and performance progression.",
        )
      }

      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          AccessibleStatCard(
            title = "Total Distance",
            value = String.format("%.2f km", totalKm),
            subtitle = "${completedRaces.size} finished races",
            icon = Icons.Rounded.EmojiEvents,
            accentColor = VoltLime,
            modifier = Modifier.weight(1f),
          )
          AccessibleStatCard(
            title = "Best Pace",
            value = BlindRunViewModel.formatPace(bestPaceSec),
            subtitle = "Avg ${BlindRunViewModel.formatPace(avgPaceSec)} /km",
            icon = Icons.Rounded.Speed,
            accentColor = SignalCyan,
            modifier = Modifier.weight(1f),
          )
        }
      }
    }

    item {
      Text(
        text = if (selectedTab == HistoryTab.HISTORY) "All Recorded Runs" else "Recent Attempts",
        style = MaterialTheme.typography.titleMedium,
      )
    }

    if (races.isEmpty()) {
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
          Text(
            text = "No races recorded yet. Completed and offline-queued runs appear here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(18.dp),
          )
        }
      }
    } else {
      items(races, key = { it.id }) { race ->
        val dateText =
          DateTimeFormatter.ofPattern("MMM dd, yyyy")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(race.createdAtEpochMs))
        Card(
          onClick = { onOpenRace(race.id) },
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
          shape = RoundedCornerShape(18.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(race.name, style = MaterialTheme.typography.titleMedium)
              Text(
                text =
                  "$dateText • ${String.format("%.2f", race.coveredDistanceMeters / 1000.0)} / ${String.format("%.1f", race.totalDistanceMeters / 1000.0)} km • ${BlindRunViewModel.formatDuration(race.elapsedSeconds)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = (if (race.synced) EmeraldSuccess else AmberWarning).copy(alpha = 0.16f),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Icon(
                  imageVector =
                    if (race.synced) Icons.Rounded.CloudDone else Icons.Rounded.CloudQueue,
                  contentDescription = if (race.synced) "Synced" else "Queued offline",
                  tint = if (race.synced) EmeraldSuccess else AmberWarning,
                  modifier = Modifier.size(14.dp),
                )
                Text(
                  text = if (race.synced) "Synced" else "Queued",
                  style = MaterialTheme.typography.labelSmall,
                  color = if (race.synced) EmeraldSuccess else AmberWarning,
                )
              }
            }
          }
        }
      }
    }
  }
}
