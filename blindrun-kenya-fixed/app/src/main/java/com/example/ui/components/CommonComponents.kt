package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.GpsTrackPoint
import com.example.data.model.ObstacleDirection
import com.example.data.model.RouteWaypoint
import com.example.data.model.SyncState
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.VoltLime

@Composable
fun SyncStatusPill(
  syncState: SyncState,
  pendingCount: Int,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val (label, icon, tint) =
    when (syncState) {
      SyncState.SYNCING -> Triple("Syncing...", Icons.Rounded.CloudSync, SignalCyan)
      SyncState.SYNCED ->
        if (pendingCount > 0) {
          Triple("$pendingCount Queued", Icons.Rounded.CloudOff, AmberWarning)
        } else {
          Triple("Synced", Icons.Rounded.CloudDone, EmeraldSuccess)
        }
      SyncState.QUEUED_OFFLINE -> Triple("$pendingCount Offline", Icons.Rounded.CloudOff, AmberWarning)
      SyncState.BACKEND_UNCONFIGURED ->
        Triple(
          if (pendingCount > 0) "Local ($pendingCount)" else "Local Mode",
          Icons.Rounded.CloudOff,
          MaterialTheme.colorScheme.onSurfaceVariant,
        )
      SyncState.ERROR -> Triple("Sync Retry", Icons.Rounded.WarningAmber, CoralAlert)
    }

  Surface(
    onClick = onClick,
    shape = CircleShape,
    color = tint.copy(alpha = 0.16f),
    border = BorderStroke(1.dp, tint.copy(alpha = 0.45f)),
    modifier =
      modifier
        .testTag("sync_status_pill")
        .semantics { contentDescription = "Synchronization status: $label" },
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = tint,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}

@Composable
fun AccessibleStatCard(
  title: String,
  value: String,
  subtitle: String,
  icon: ImageVector,
  accentColor: Color,
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
) {
  val containerColor = MaterialTheme.colorScheme.surfaceVariant
  val cardContent: @Composable () -> Unit = {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = title.uppercase(),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
          modifier =
            Modifier.size(32.dp)
              .clip(CircleShape)
              .background(accentColor.copy(alpha = 0.16f)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
        }
      }
      Text(
        text = value,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }

  if (onClick != null) {
    Card(
      onClick = onClick,
      modifier = modifier.semantics { contentDescription = "$title: $value, $subtitle" },
      colors = CardDefaults.cardColors(containerColor = containerColor),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
      shape = MaterialTheme.shapes.medium,
    ) {
      cardContent()
    }
  } else {
    Card(
      modifier = modifier.semantics { contentDescription = "$title: $value, $subtitle" },
      colors = CardDefaults.cardColors(containerColor = containerColor),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
      shape = MaterialTheme.shapes.medium,
    ) {
      cardContent()
    }
  }
}

@Composable
fun BleRadarVisualizer(
  isScanning: Boolean,
  isConnected: Boolean,
  modifier: Modifier = Modifier,
) {
  val infiniteTransition = rememberInfiniteTransition(label = "radar")
  val pulse by
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 2200, easing = LinearEasing),
          repeatMode = RepeatMode.Restart,
        ),
      label = "radarPulse",
    )

  val ringColor =
    when {
      isConnected -> VoltLime
      isScanning -> SignalCyan
      else -> MaterialTheme.colorScheme.outline
    }

  Box(modifier = modifier.size(190.dp), contentAlignment = Alignment.Center) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val center = Offset(size.width / 2f, size.height / 2f)
      val maxRadius = size.minDimension / 2f
      for (i in 1..3) {
        drawCircle(
          color = ringColor.copy(alpha = 0.25f),
          radius = maxRadius * (i / 3f),
          center = center,
          style = Stroke(width = 2.dp.toPx()),
        )
      }
      if (isScanning) {
        drawCircle(
          color = ringColor.copy(alpha = (1f - pulse) * 0.6f),
          radius = maxRadius * pulse,
          center = center,
          style = Stroke(width = 3.dp.toPx()),
        )
      }
    }
    Surface(
      shape = CircleShape,
      color = ringColor.copy(alpha = 0.2f),
      border = BorderStroke(2.dp, ringColor),
      modifier = Modifier.size(72.dp),
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector =
            if (isConnected) Icons.Rounded.BluetoothConnected else Icons.Rounded.BluetoothSearching,
          contentDescription = if (isConnected) "Waist belt connected" else "Scanning for waist belt",
          tint = ringColor,
          modifier = Modifier.size(34.dp),
        )
      }
    }
  }
}

@Composable
fun RoutePathCanvas(
  plannedWaypoints: List<RouteWaypoint>,
  recordedTrack: List<GpsTrackPoint>,
  deviationMeters: Double,
  modifier: Modifier = Modifier,
) {
  val lineColor = MaterialTheme.colorScheme.primary
  val plannedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
  val warnColor = CoralAlert

  Surface(
    modifier = modifier.fillMaxWidth().height(175.dp),
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.surfaceVariant,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
  ) {
    Box(modifier = Modifier.fillMaxSize().padding(14.dp)) {
      val allCoords =
        plannedWaypoints.map { it.latitude to it.longitude } +
          recordedTrack.map { it.latitude to it.longitude }

      if (allCoords.size < 2) {
        Column(
          modifier = Modifier.align(Alignment.Center),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text(
            text = "GPS Route Canvas Ready",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Spacer(Modifier.height(4.dp))
          Text(
            text = "Start the race or import a GPX/TCX file to visualize live path & deviation.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      } else {
        Canvas(modifier = Modifier.fillMaxSize()) {
          val minLat = allCoords.minOf { it.first }
          val maxLat = allCoords.maxOf { it.first }
          val minLon = allCoords.minOf { it.second }
          val maxLon = allCoords.maxOf { it.second }
          val latSpan = (maxLat - minLat).coerceAtLeast(0.0005)
          val lonSpan = (maxLon - minLon).coerceAtLeast(0.0005)

          fun toCanvasOffset(lat: Double, lon: Double): Offset {
            val x = (((lon - minLon) / lonSpan).toFloat()) * (size.width - 24f) + 12f
            val y = (1f - ((lat - minLat) / latSpan).toFloat()) * (size.height - 24f) + 12f
            return Offset(x, y)
          }

          if (plannedWaypoints.size >= 2) {
            val plannedPath = Path()
            plannedWaypoints.forEachIndexed { idx, wp ->
              val pt = toCanvasOffset(wp.latitude, wp.longitude)
              if (idx == 0) plannedPath.moveTo(pt.x, pt.y) else plannedPath.lineTo(pt.x, pt.y)
            }
            drawPath(
              path = plannedPath,
              color = plannedColor,
              style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
            )
          }

          if (recordedTrack.size >= 2) {
            val activePath = Path()
            recordedTrack.forEachIndexed { idx, tp ->
              val pt = toCanvasOffset(tp.latitude, tp.longitude)
              if (idx == 0) activePath.moveTo(pt.x, pt.y) else activePath.lineTo(pt.x, pt.y)
            }
            drawPath(
              path = activePath,
              color = if (deviationMeters > 35.0) warnColor else lineColor,
              style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
            )
            val latest = recordedTrack.last()
            val latestPt = toCanvasOffset(latest.latitude, latest.longitude)
            drawCircle(
              color = if (deviationMeters > 35.0) warnColor else VoltLime,
              radius = 7.dp.toPx(),
              center = latestPt,
            )
          }
        }
      }
    }
  }
}

@Composable
fun TrendSparklineChart(
  values: List<Float>,
  accentColor: Color,
  emptyText: String,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier.fillMaxWidth().height(150.dp),
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.surfaceVariant,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
  ) {
    if (values.size < 2) {
      Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Text(
          text = emptyText,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    } else {
      Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 20.dp)) {
        val minVal = values.minOrNull() ?: 0f
        val maxVal = values.maxOrNull() ?: 1f
        val span = (maxVal - minVal).coerceAtLeast(1f)
        val stepX = size.width / (values.size - 1).coerceAtLeast(1)

        // Subtle horizontal grid lines
        for (i in 0..2) {
          val y = size.height * (i / 2f)
          drawLine(
            color = Color.White.copy(alpha = 0.08f),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1.dp.toPx(),
          )
        }

        val path = Path()
        values.forEachIndexed { idx, v ->
          val x = idx * stepX
          val y = size.height - ((v - minVal) / span) * size.height
          if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
          path = path,
          color = accentColor,
          style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round),
        )
        values.forEachIndexed { idx, v ->
          val x = idx * stepX
          val y = size.height - ((v - minVal) / span) * size.height
          drawCircle(color = accentColor, radius = 4.dp.toPx(), center = Offset(x, y))
        }
      }
    }
  }
}

@Composable
fun DirectionalMotorBadge(
  direction: ObstacleDirection,
  isAlertActive: Boolean,
  onTestClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val bgColor =
    if (isAlertActive) CoralAlert.copy(alpha = 0.25f)
    else MaterialTheme.colorScheme.surfaceElevatedOrCard()
  val borderColor = if (isAlertActive) CoralAlert else VoltLime.copy(alpha = 0.5f)
  Surface(
    onClick = onTestClick,
    modifier =
      modifier
        .testTag("motor_badge_${direction.apiValue}")
        .semantics {
          contentDescription = "Test ${direction.label} vibration motor channel ${direction.motorChannel}"
        },
    shape = RoundedCornerShape(16.dp),
    color = bgColor,
    border = BorderStroke(1.5.dp, borderColor),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        text = direction.label.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = if (isAlertActive) CoralAlert else VoltLime,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = "CH ${direction.motorChannel}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun androidx.compose.material3.ColorScheme.surfaceElevatedOrCard(): Color = surfaceVariant
