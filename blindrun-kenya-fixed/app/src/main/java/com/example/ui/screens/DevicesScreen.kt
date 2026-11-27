package com.example.ui.screens

import android.Manifest
import android.os.Build
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ObstacleDirection
import com.example.data.model.WearableDevice
import com.example.hardware.BleConnectionState
import com.example.hardware.BleScannedDevice
import com.example.ui.components.BleRadarVisualizer
import com.example.ui.components.DirectionalMotorBadge
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.ObsidianNight
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.VoltLime

@Composable
fun DevicesScreen(
  bleState: BleConnectionState,
  scannedDevices: List<BleScannedDevice>,
  pairedDevices: List<WearableDevice>,
  hasBlePermissions: Boolean,
  latestObstacleDirection: ObstacleDirection?,
  onStartBleScan: () -> Unit,
  onStopBleScan: () -> Unit,
  onConnectDevice: (String, String) -> Unit,
  onDisconnect: () -> Unit,
  onOpenQrScanner: () -> Unit,
  onTestHapticMotor: (ObstacleDirection) -> Unit,
) {
  var permissionDeniedNote by remember { mutableStateOf(false) }

  val blePermissions =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
      arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

  val blePermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
      val allGranted = result.values.all { it }
      permissionDeniedNote = !allGranted
      if (allGranted) onStartBleScan()
    }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      Text(
        text = "ESP32 Smart Waist Belt",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
      )
      Text(
        text = "Scan the belt QR tag or discover nearby BLE devices for automatic reconnection.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    // Radar Hero Card (mirrors Screen 3 in reference image)
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(24.dp),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(20.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          BleRadarVisualizer(isScanning = bleState.isScanning, isConnected = bleState.isConnected)
          Text(
            text = bleState.statusMessage,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text =
              if (bleState.isConnected) {
                "MAC: ${bleState.connectedMacAddress} • FW: ${bleState.firmwareVersion}"
              } else {
                bleState.diagnosticsSummary
              },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          if (bleState.isConnected) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = EmeraldSuccess.copy(alpha = 0.16f),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Icon(Icons.Rounded.BatteryChargingFull, contentDescription = null, tint = EmeraldSuccess)
                  Text(
                    text = "Battery: ${bleState.batteryPercent ?: "--"}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = EmeraldSuccess,
                  )
                }
              }
              OutlinedButton(
                onClick = onDisconnect,
                modifier = Modifier.height(48.dp).testTag("ble_disconnect_btn"),
              ) {
                Text("Disconnect", color = CoralAlert)
              }
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Button(
              onClick = {
                if (bleState.isScanning) {
                  onStopBleScan()
                } else if (hasBlePermissions) {
                  onStartBleScan()
                } else {
                  blePermissionLauncher.launch(blePermissions)
                }
              },
              colors = ButtonDefaults.buttonColors(containerColor = VoltLime, contentColor = ObsidianNight),
              modifier = Modifier.weight(1f).height(52.dp).testTag("ble_scan_btn"),
            ) {
              Icon(Icons.Rounded.Bluetooth, contentDescription = null)
              Spacer(Modifier.size(6.dp))
              Text(if (bleState.isScanning) "Stop Scan" else "Scan BLE", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
              onClick = onOpenQrScanner,
              modifier = Modifier.weight(1f).height(52.dp).testTag("open_qr_scanner_btn"),
              border = BorderStroke(1.5.dp, SignalCyan),
            ) {
              Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, tint = SignalCyan)
              Spacer(Modifier.size(6.dp))
              Text("Scan QR", color = SignalCyan, fontWeight = FontWeight.SemiBold)
            }
          }

          if (permissionDeniedNote) {
            Text(
              text = "Bluetooth Nearby Devices permission is required to scan and connect to your ESP32 belt.",
              style = MaterialTheme.typography.bodyMedium,
              color = CoralAlert,
            )
          }
        }
      }
    }

    // 4-Direction Haptic Motor Diagnostics (FR-08, AC-08)
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(20.dp),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.Vibration, contentDescription = null, tint = VoltLime)
            Text("4-Direction Haptic Motors", style = MaterialTheme.typography.titleMedium)
          }
          Text(
            text =
              "Tap any direction to verify waist-belt motor and phone tactile pulses. Note: ESP32 processes urgent obstacle avoidance locally without cloud latency.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            ObstacleDirection.entries.forEach { dir ->
              DirectionalMotorBadge(
                direction = dir,
                isAlertActive = latestObstacleDirection == dir,
                onTestClick = { onTestHapticMotor(dir) },
                modifier = Modifier.weight(1f),
              )
            }
          }
        }
      }
    }

    // Discovered Nearby BLE Devices
    if (scannedDevices.isNotEmpty()) {
      item {
        Text("Nearby BLE Devices", style = MaterialTheme.typography.titleMedium)
      }
      items(scannedDevices, key = { it.macAddress }) { device ->
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
          border =
            BorderStroke(
              1.dp,
              if (device.isWaistBeltCandidate) VoltLime else MaterialTheme.colorScheme.outline,
            ),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(device.name, style = MaterialTheme.typography.titleMedium)
              Text(
                text = "${device.macAddress} • RSSI ${device.rssi} dBm",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Button(
              onClick = { onConnectDevice(device.macAddress, device.name) },
              colors = ButtonDefaults.buttonColors(containerColor = VoltLime, contentColor = ObsidianNight),
              modifier = Modifier.height(48.dp),
            ) {
              Text("Pair", fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }

    // Previously Paired Waist Belts
    if (pairedDevices.isNotEmpty()) {
      item {
        Text("Saved Waist Belts", style = MaterialTheme.typography.titleMedium)
      }
      items(pairedDevices, key = { it.id }) { saved ->
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              modifier = Modifier.weight(1f),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(Icons.Rounded.BluetoothConnected, contentDescription = null, tint = SignalCyan)
              Column {
                Text(saved.name, style = MaterialTheme.typography.titleMedium)
                Text(
                  text = "Serial ${saved.serialCode} • ${saved.macAddress}",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            if (saved.macAddress.contains(":")) {
              OutlinedButton(
                onClick = { onConnectDevice(saved.macAddress, saved.name) },
                modifier = Modifier.height(48.dp),
              ) {
                Text("Reconnect")
              }
            }
          }
        }
      }
    }
  }
}
