package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.WearableDevice
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.ObsidianNight
import com.example.ui.theme.VoltLime
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

@Composable
fun QrScannerScreen(
  onQrDecoded: (String) -> Result<WearableDevice>,
  onBack: () -> Unit,
) {
  BackHandler { onBack() }

  val context = LocalContext.current
  var hasCameraPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    )
  }
  var manualQrInput by remember { mutableStateOf("") }
  var scanFeedback by remember { mutableStateOf<String?>(null) }
  var scanError by remember { mutableStateOf<String?>(null) }

  val cameraPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasCameraPermission = granted
    }

  LaunchedEffect(Unit) {
    if (!hasCameraPermission) {
      cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }
  }

  Column(
    modifier = Modifier.fillMaxSize().systemBarsPadding().padding(18.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      IconButton(onClick = onBack, modifier = Modifier.testTag("qr_back_btn")) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to devices")
      }
      Text("Scan Waist Belt QR Code", style = MaterialTheme.typography.headlineMedium)
    }

    Text(
      text =
        "Point the camera at the QR tag attached to your ESP32 waist belt. QR codes carry only device identification (no reusable secret credentials).",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (hasCameraPermission) {
      Surface(
        modifier = Modifier.fillMaxWidth().weight(1f),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(2.dp, VoltLime.copy(alpha = 0.7f)),
      ) {
        Box(modifier = Modifier.fillMaxSize()) {
          CameraQrPreview(
            onQrDetected = { raw ->
              val res = onQrDecoded(raw)
              res.fold(
                onSuccess = { device ->
                  scanError = null
                  scanFeedback = "Paired ${device.name} (${device.serialCode})"
                  onBack()
                },
                onFailure = { err -> scanError = err.localizedMessage },
              )
            }
          )
          Surface(
            color = ObsidianNight.copy(alpha = 0.7f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, tint = VoltLime)
              Text("Align QR code inside frame", color = VoltLime)
            }
          }
        }
      }
    } else {
      Card(
        modifier = Modifier.fillMaxWidth().weight(1f),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      ) {
        Column(
          modifier = Modifier.fillMaxSize().padding(20.dp),
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Icon(Icons.Rounded.CameraAlt, contentDescription = null, tint = VoltLime)
          Spacer(Modifier.height(10.dp))
          Text("Camera permission needed to scan waist belt QR tags.")
          Spacer(Modifier.height(10.dp))
          OutlinedButton(
            onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
            modifier = Modifier.height(48.dp),
          ) {
            Text("Grant Camera Permission")
          }
        }
      }
    }

    if (!scanError.isNullOrBlank()) {
      Text(scanError!!, color = CoralAlert, style = MaterialTheme.typography.bodyMedium)
    }
    if (!scanFeedback.isNullOrBlank()) {
      Text(scanFeedback!!, color = VoltLime, style = MaterialTheme.typography.bodyMedium)
    }

    // Accessible Serial / QR Payload Entry Fallback (FR-03 + NFR-01 screen-reader accessible)
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      shape = RoundedCornerShape(18.dp),
    ) {
      Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Or Enter Belt Serial / Tag URI", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
          value = manualQrInput,
          onValueChange = { manualQrInput = it },
          label = { Text("e.g. BRK-BELT-01 or blindrun://belt?serial=...") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("manual_qr_input"),
        )
        Button(
          onClick = {
            onQrDecoded(manualQrInput).fold(
              onSuccess = {
                scanError = null
                onBack()
              },
              onFailure = { scanError = it.localizedMessage },
            )
          },
          colors = ButtonDefaults.buttonColors(containerColor = VoltLime, contentColor = ObsidianNight),
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("validate_belt_qr_btn"),
        ) {
          Text("Validate & Pair Belt", fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}

@Composable
private fun CameraQrPreview(onQrDetected: (String) -> Unit) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val executor = remember { Executors.newSingleThreadExecutor() }
  val previewView = remember { PreviewView(context) }
  var hasEmitted by remember { mutableStateOf(false) }

  DisposableEffect(Unit) { onDispose { executor.shutdown() } }

  LaunchedEffect(lifecycleOwner) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    cameraProviderFuture.addListener(
      {
        val cameraProvider = cameraProviderFuture.get()
        val preview =
          Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
        val analysis =
          ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { imageAnalysis ->
              imageAnalysis.setAnalyzer(executor) { imageProxy ->
                if (!hasEmitted) {
                  decodeQrFromProxy(imageProxy)?.let { decoded ->
                    hasEmitted = true
                    ContextCompat.getMainExecutor(context).execute { onQrDetected(decoded) }
                  }
                }
                imageProxy.close()
              }
            }
        try {
          cameraProvider.unbindAll()
          cameraProvider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            analysis,
          )
        } catch (_: Exception) {}
      },
      ContextCompat.getMainExecutor(context),
    )
  }

  AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

private fun decodeQrFromProxy(image: ImageProxy): String? {
  return try {
    val plane = image.planes.firstOrNull() ?: return null
    val buffer = plane.buffer
    val data = ByteArray(buffer.remaining())
    buffer.get(data)
    val source =
      PlanarYUVLuminanceSource(
        data,
        image.width,
        image.height,
        0,
        0,
        image.width,
        image.height,
        false,
      )
    val bitmap = BinaryBitmap(HybridBinarizer(source))
    val reader =
      MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
      }
    reader.decode(bitmap).text
  } catch (_: Exception) {
    null
  }
}
