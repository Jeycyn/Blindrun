package com.example.hardware

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.model.ObstacleDirection
import com.example.data.model.ObstacleSeverity
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BleScannedDevice(
  val name: String,
  val macAddress: String,
  val rssi: Int,
  val isWaistBeltCandidate: Boolean,
)

data class BleObstaclePacket(
  val direction: ObstacleDirection,
  val distanceCm: Int,
  val severity: ObstacleSeverity,
  val timestampMs: Long = System.currentTimeMillis(),
)

data class QrBeltIdentity(
  val serialCode: String,
  val deviceName: String,
  val macAddress: String?,
  val firmwareVersion: String,
)

data class BleConnectionState(
  val bluetoothSupported: Boolean = false,
  val bluetoothEnabled: Boolean = false,
  val isScanning: Boolean = false,
  val isConnected: Boolean = false,
  val isReconnecting: Boolean = false,
  val connectedDeviceName: String = "",
  val connectedMacAddress: String = "",
  val batteryPercent: Int? = null,
  val firmwareVersion: String = "ESP32-S3 v1.4",
  val diagnosticsSummary: String = "Awaiting waist belt connection",
  val statusMessage: String = "Ready to scan for ESP32 Smart Waist Belt",
)

class BleWaistBeltManager(context: Context) {
  private val appContext = context.applicationContext
  private val scope = CoroutineScope(Dispatchers.IO)

  private val bluetoothManager: BluetoothManager? =
    appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
  private val bluetoothAdapter: BluetoothAdapter?
    get() = bluetoothManager?.adapter

  private var bluetoothGatt: BluetoothGatt? = null
  private var scanJob: Job? = null
  private var activeScanCallback: ScanCallback? = null

  private val _connectionState = MutableStateFlow(BleConnectionState())
  val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

  private val _scannedDevices = MutableStateFlow<List<BleScannedDevice>>(emptyList())
  val scannedDevices: StateFlow<List<BleScannedDevice>> = _scannedDevices.asStateFlow()

  private val _obstacleEvents = MutableSharedFlow<BleObstaclePacket>(extraBufferCapacity = 16)
  val obstacleEvents: SharedFlow<BleObstaclePacket> = _obstacleEvents.asSharedFlow()

  private val _disconnectionAlerts = MutableSharedFlow<String>(extraBufferCapacity = 4)
  val disconnectionAlerts: SharedFlow<String> = _disconnectionAlerts.asSharedFlow()

  init {
    refreshBluetoothAvailability()
  }

  fun refreshBluetoothAvailability() {
    val hasFeature = appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    val adapter = bluetoothAdapter
    _connectionState.update { it.copy(
        bluetoothSupported = hasFeature && adapter != null,
        bluetoothEnabled = adapter?.isEnabled == true,
      ) }
  }

  fun hasRequiredPermissions(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.BLUETOOTH_SCAN) ==
        PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(
          appContext,
          android.Manifest.permission.BLUETOOTH_CONNECT,
        ) == PackageManager.PERMISSION_GRANTED
    } else {
      ContextCompat.checkSelfPermission(
        appContext,
        android.Manifest.permission.ACCESS_FINE_LOCATION,
      ) == PackageManager.PERMISSION_GRANTED
    }
  }

  @SuppressLint("MissingPermission")
  fun startScan(targetMacOrName: String? = null) {
    refreshBluetoothAvailability()
    if (!hasRequiredPermissions()) {
      _connectionState.update { it.copy(statusMessage = "Bluetooth permissions required to scan") }
      return
    }
    val adapter = bluetoothAdapter
    if (adapter == null || !adapter.isEnabled) {
      _connectionState.update { it.copy(
          bluetoothEnabled = false,
          statusMessage = "Turn on Bluetooth to discover your ESP32 waist belt",
        ) }
      return
    }
    val scanner: BluetoothLeScanner? = adapter.bluetoothLeScanner
    if (scanner == null) {
      _connectionState.update { it.copy(statusMessage = "BLE scanner unavailable on this device") }
      return
    }

    stopScan()
    _scannedDevices.value = emptyList()
    _connectionState.update { it.copy(
        isScanning = true,
        statusMessage = "Scanning for BlindRun ESP32 Smart Waist Belt...",
      ) }

    val callback =
      object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
          val device = result.device ?: return
          val mac = device.address ?: return
          val name = device.name?.takeIf { it.isNotBlank() } ?: "BLE Device ($mac)"
          val candidate =
            name.contains("BlindRun", ignoreCase = true) ||
              name.contains("ESP32", ignoreCase = true) ||
              name.contains("Belt", ignoreCase = true) ||
              result.scanRecord?.serviceUuids?.any { it.uuid == SERVICE_BLINDRUN } == true

          val updated = _scannedDevices.value.toMutableList()
          val existingIndex = updated.indexOfFirst { it.macAddress == mac }
          val item = BleScannedDevice(name, mac, result.rssi, candidate)
          if (existingIndex >= 0) updated[existingIndex] = item else updated.add(item)
          _scannedDevices.value = updated.sortedByDescending { it.isWaistBeltCandidate }

          if (
            !targetMacOrName.isNullOrBlank() &&
              (mac.equals(targetMacOrName, ignoreCase = true) ||
                name.contains(targetMacOrName, ignoreCase = true))
          ) {
            stopScan()
            connectToAddress(mac, name)
          }
        }

        override fun onScanFailed(errorCode: Int) {
          _connectionState.update { it.copy(
              isScanning = false,
              statusMessage = "BLE scan failed (code $errorCode). Ensure Bluetooth is enabled.",
            ) }
        }
      }

    activeScanCallback = callback
    val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
    scanner.startScan(null, settings, callback)

    scanJob =
      scope.launch {
        delay(12_000L)
        stopScan()
      }
  }

  @SuppressLint("MissingPermission")
  fun stopScan() {
    scanJob?.cancel()
    scanJob = null
    val callback = activeScanCallback ?: return
    activeScanCallback = null
    try {
      if (hasRequiredPermissions()) {
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(callback)
      }
    } catch (e: Exception) {
      Log.w(TAG, "Error stopping BLE scan: ${e.message}")
    }
    _connectionState.update { it.copy(
        isScanning = false,
        statusMessage =
          if (_connectionState.value.isConnected) {
            "Connected to ${_connectionState.value.connectedDeviceName}"
          } else if (_scannedDevices.value.isEmpty()) {
            "No BLE waist belts detected nearby. Check belt power or scan QR code."
          } else {
            "Found ${_scannedDevices.value.size} nearby BLE device(s)"
          },
      ) }
  }

  @SuppressLint("MissingPermission")
  fun connectToAddress(macAddress: String, displayName: String = "ESP32 Waist Belt"): Boolean {
    if (!hasRequiredPermissions()) {
      _connectionState.update { it.copy(statusMessage = "Bluetooth permission required to connect") }
      return false
    }
    val adapter = bluetoothAdapter ?: return false
    if (!adapter.isEnabled || !BluetoothAdapter.checkBluetoothAddress(macAddress)) {
      _connectionState.update { it.copy(
          statusMessage = "Invalid MAC or Bluetooth disabled ($macAddress)"
        ) }
      return false
    }

    stopScan()
    bluetoothGatt?.close()

    _connectionState.update { it.copy(
        isReconnecting = true,
        connectedDeviceName = displayName,
        connectedMacAddress = macAddress,
        statusMessage = "Connecting to $displayName ($macAddress)...",
      ) }

    val device = adapter.getRemoteDevice(macAddress)
    // autoConnect = true enables OS-managed automatic reconnection on temporary BLE drops (FR-04, NFR-03)
    bluetoothGatt =
      device.connectGatt(appContext, true, gattCallback, BluetoothDevice.TRANSPORT_LE)
    return true
  }

  @SuppressLint("MissingPermission")
  fun disconnect() {
    clearOps()
    try {
      bluetoothGatt?.disconnect()
      bluetoothGatt?.close()
    } catch (_: Exception) {}
    bluetoothGatt = null
    _connectionState.update { it.copy(
        isConnected = false,
        isReconnecting = false,
        statusMessage = "Disconnected from waist belt",
      ) }
  }

  @SuppressLint("MissingPermission")
  fun sendRaceConfiguration(raceName: String, totalDistanceMeters: Double, status: String): Boolean {
    val gatt = bluetoothGatt ?: return false
    val service = gatt.getService(SERVICE_BLINDRUN) ?: return false
    val char = service.getCharacteristic(CHAR_RACE_CONFIG) ?: return false
    val payload = "CFG|${raceName.take(24)}|${totalDistanceMeters.toInt()}|$status".toByteArray()
    return writeCharacteristicBytes(gatt, char, payload)
  }

  @SuppressLint("MissingPermission")
  fun sendHapticCommand(direction: ObstacleDirection, intensityPercent: Int): Boolean {
    val gatt = bluetoothGatt ?: return false
    val service = gatt.getService(SERVICE_BLINDRUN) ?: return false
    val char = service.getCharacteristic(CHAR_HAPTIC_CMD) ?: return false
    val payload = "HAP|${direction.motorChannel}|${intensityPercent.coerceIn(20, 100)}".toByteArray()
    return writeCharacteristicBytes(gatt, char, payload)
  }

  @SuppressLint("MissingPermission")
  private fun writeCharacteristicBytes(
    gatt: BluetoothGatt,
    char: BluetoothGattCharacteristic,
    bytes: ByteArray,
  ): Boolean {
    return try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        gatt.writeCharacteristic(char, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) ==
          BluetoothGatt.GATT_SUCCESS
      } else {
        @Suppress("DEPRECATION")
        char.value = bytes
        @Suppress("DEPRECATION")
        gatt.writeCharacteristic(char)
      }
    } catch (e: Exception) {
      Log.w(TAG, "GATT write failed: ${e.message}")
      false
    }
  }

  private val gattCallback =
    object : BluetoothGattCallback() {
      @SuppressLint("MissingPermission")
      override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        if (newState == BluetoothProfile.STATE_CONNECTED) {
          _connectionState.update { it.copy(
              isConnected = true,
              isReconnecting = false,
              statusMessage = "Connected to ${gatt.device.name ?: gatt.device.address}",
              diagnosticsSummary = "GATT active • Auto-reconnect armed",
            ) }
          gatt.discoverServices()
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
          clearOps()
          val wasConnected = _connectionState.value.isConnected
          _connectionState.update { it.copy(
              isConnected = false,
              isReconnecting = true,
              statusMessage = "Connection lost — attempting auto-reconnect...",
            ) }
          if (wasConnected) {
            _disconnectionAlerts.tryEmit(gatt.device.name ?: gatt.device.address)
          }
        }
      }

      @SuppressLint("MissingPermission")
      override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        if (status != BluetoothGatt.GATT_SUCCESS) return

        // Refuse anything that is not a BlindRun belt (a QR code / saved MAC can point anywhere).
        if (gatt.getService(SERVICE_BLINDRUN) == null) {
          _connectionState.update {
            it.copy(statusMessage = "This Bluetooth device is not a BlindRun waist belt. Disconnected.")
          }
          disconnect()
          return
        }

        clearOps()
        // 1. Safety-critical first: obstacle notifications.
        gatt.getService(SERVICE_BLINDRUN)?.getCharacteristic(CHAR_OBSTACLE_NOTIFY)?.let { obsChar ->
          enqueueOp { enableNotifications(gatt, obsChar) }
        }
        // 2. Battery level + notifications.
        gatt.getService(SERVICE_BATTERY)?.getCharacteristic(CHAR_BATTERY_LEVEL)?.let { batChar ->
          enqueueOp { gatt.readCharacteristic(batChar) }
          enqueueOp { enableNotifications(gatt, batChar) }
        }
        // 3. ESP32 diagnostics.
        gatt.getService(SERVICE_BLINDRUN)?.getCharacteristic(CHAR_DIAGNOSTICS)?.let { diagChar ->
          enqueueOp { gatt.readCharacteristic(diagChar) }
        }
      }

      override fun onDescriptorWrite(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int,
      ) {
        completeOp()
      }

      @Deprecated("Deprecated in API 33")
      override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int,
      ) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
          @Suppress("DEPRECATION")
          handleIncomingCharacteristic(characteristic.uuid, characteristic.value ?: byteArrayOf())
        }
        completeOp()
      }

      override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int,
      ) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
          handleIncomingCharacteristic(characteristic.uuid, value)
        }
        completeOp()
      }

      @Deprecated("Deprecated in API 33")
      override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
      ) {
        @Suppress("DEPRECATION")
        handleIncomingCharacteristic(characteristic.uuid, characteristic.value ?: byteArrayOf())
      }

      override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
      ) {
        handleIncomingCharacteristic(characteristic.uuid, value)
      }
    }

  // ---- GATT operation queue -------------------------------------------------------------
  // Android's GATT stack runs ONE read/write at a time. Firing several back-to-back makes the
  // later ones fail silently, which used to leave the obstacle-notification CCCD unwritten
  // (so the belt connected but no obstacle alerts ever arrived).
  private val opLock = Any()
  private val pendingOps = ArrayDeque<() -> Boolean>()
  private var opInFlight = false
  private var opSeq = 0

  private fun enqueueOp(op: () -> Boolean) {
    synchronized(opLock) { pendingOps.addLast(op) }
    drainOps()
  }

  private fun drainOps() {
    synchronized(opLock) {
      if (opInFlight) return
      while (pendingOps.isNotEmpty()) {
        val op = pendingOps.removeFirst()
        val started = try { op() } catch (e: Exception) { Log.w(TAG, "GATT op failed: ${e.message}"); false }
        if (started) {
          opInFlight = true
          val token = ++opSeq
          // Watchdog: never let one lost callback stall the queue forever.
          scope.launch {
            delay(OP_TIMEOUT_MS)
            val stale = synchronized(opLock) { (opInFlight && opSeq == token).also { if (it) opInFlight = false } }
            if (stale) drainOps()
          }
          return
        }
      }
    }
  }

  private fun completeOp() {
    synchronized(opLock) { opInFlight = false }
    drainOps()
  }

  private fun clearOps() {
    synchronized(opLock) {
      pendingOps.clear()
      opInFlight = false
    }
  }

  @SuppressLint("MissingPermission")
  private fun enableNotifications(gatt: BluetoothGatt, char: BluetoothGattCharacteristic): Boolean {
    gatt.setCharacteristicNotification(char, true)
    val cccd = char.getDescriptor(UUID_CCCD) ?: return false
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ==
        BluetoothGatt.GATT_SUCCESS
    } else {
      @Suppress("DEPRECATION")
      cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
      @Suppress("DEPRECATION")
      gatt.writeDescriptor(cccd)
    }
  }

  private fun handleIncomingCharacteristic(uuid: UUID, bytes: ByteArray) {
    when (uuid) {
      CHAR_BATTERY_LEVEL -> {
        val level = bytes.firstOrNull()?.toInt()?.and(0xFF)?.coerceIn(0, 100) ?: return
        _connectionState.update { it.copy(batteryPercent = level) }
      }
      CHAR_DIAGNOSTICS -> {
        val text = bytes.decodeToString().trim()
        if (text.isNotEmpty()) {
          _connectionState.update { it.copy(diagnosticsSummary = text) }
        }
      }
      CHAR_OBSTACLE_NOTIFY -> {
        val packet = parseObstaclePacket(bytes.decodeToString())
        if (packet != null) {
          _obstacleEvents.tryEmit(packet)
        }
      }
    }
  }

  companion object {
    private const val TAG = "BleWaistBeltManager"
    private const val OP_TIMEOUT_MS = 3_000L

    val SERVICE_BATTERY: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    val CHAR_BATTERY_LEVEL: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
    val UUID_CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // Custom BlindRun Kenya ESP32 GATT Service UUIDs
    val SERVICE_BLINDRUN: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
    val CHAR_RACE_CONFIG: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
    val CHAR_OBSTACLE_NOTIFY: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
    val CHAR_HAPTIC_CMD: UUID = UUID.fromString("6e400004-b5a3-f393-e0a9-e50e24dcca9e")
    val CHAR_DIAGNOSTICS: UUID = UUID.fromString("6e400005-b5a3-f393-e0a9-e50e24dcca9e")

    fun parseObstaclePacket(raw: String): BleObstaclePacket? {
      // Expected ESP32 ASCII frame: "OBS:FRONT:85:HIGH" or "FRONT,85,HIGH"
      val parts = raw.trim().split(":", ",").map { it.trim() }
      if (parts.isEmpty()) return null
      val offset = if (parts[0].equals("OBS", ignoreCase = true)) 1 else 0
      val dirStr = parts.getOrNull(offset) ?: return null
      val distCm = parts.getOrNull(offset + 1)?.toIntOrNull() ?: 100
      val sevStr = parts.getOrNull(offset + 2) ?: "medium"
      val direction =
        ObstacleDirection.entries.firstOrNull { it.apiValue.equals(dirStr, ignoreCase = true) }
          ?: return null
      return BleObstaclePacket(
        direction = direction,
        distanceCm = distCm,
        severity = ObstacleSeverity.fromApi(sevStr),
      )
    }

    /**
     * Parses a QR code attached to an ESP32 smart waist belt.
     * Enforces FR-03: QR codes identify the device (serial/MAC/name/fw) and never contain reusable secret credentials.
     */
    fun parseBeltQrCode(rawQr: String): QrBeltIdentity? {
      val trimmed = rawQr.trim()
      if (trimmed.isBlank()) return null

      // Reject QR codes attempting to embed secret tokens or passwords
      val lower = trimmed.lowercase()
      if (lower.contains("secret=") || lower.contains("password=") || lower.contains("token=")) {
        return null
      }

      if (trimmed.startsWith("blindrun://", ignoreCase = true)) {
        val uri = Uri.parse(trimmed)
        val serial = uri.getQueryParameter("serial")?.takeIf { it.isNotBlank() } ?: return null
        val mac = uri.getQueryParameter("mac")?.takeIf { BluetoothAdapter.checkBluetoothAddress(it) }
        val name = uri.getQueryParameter("name")?.takeIf { it.isNotBlank() } ?: "BlindRun Belt $serial"
        val fw = uri.getQueryParameter("fw")?.takeIf { it.isNotBlank() } ?: "ESP32-S3 v1.4"
        return QrBeltIdentity(serialCode = serial, deviceName = name, macAddress = mac, firmwareVersion = fw)
      }

      // Support compact pipe or serial formats like "BRK-BELT-042|AA:BB:CC:DD:EE:FF" or "BRK-BELT-042"
      val segments = trimmed.split("|", ",").map { it.trim() }
      val serial = segments.firstOrNull()?.takeIf { it.length in 4..48 } ?: return null
      val mac = segments.getOrNull(1)?.takeIf { BluetoothAdapter.checkBluetoothAddress(it) }
      val name = segments.getOrNull(2)?.takeIf { it.isNotBlank() } ?: "BlindRun Belt $serial"
      return QrBeltIdentity(serialCode = serial, deviceName = name, macAddress = mac, firmwareVersion = "ESP32-S3 v1.4")
    }
  }
}
