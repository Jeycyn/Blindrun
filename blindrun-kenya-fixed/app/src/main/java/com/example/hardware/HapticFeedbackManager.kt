package com.example.hardware

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import com.example.data.model.AccessibilityMode
import com.example.data.model.ObstacleDirection
import java.util.Locale

class HapticFeedbackManager(context: Context) {
  private val appContext = context.applicationContext

  private val vibrator: Vibrator? by lazy {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val manager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
      manager?.defaultVibrator
    } else {
      @Suppress("DEPRECATION")
      appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
  }

  private var tts: TextToSpeech? = null
  private var ttsReady: Boolean = false

  init {
    tts =
      TextToSpeech(appContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
          tts?.language = Locale.getDefault()
          ttsReady = true
        }
      }
  }

  fun triggerDirectionalObstacle(
    direction: ObstacleDirection,
    intensityPercent: Int,
    accessibilityMode: AccessibilityMode,
    audioCuesEnabled: Boolean,
    distanceCm: Int,
  ) {
    val amp = ((intensityPercent.coerceIn(20, 100) / 100f) * 255).toInt().coerceIn(50, 255)
    val timings: LongArray
    val amplitudes: IntArray
    when (direction) {
      ObstacleDirection.FRONT -> {
        timings = longArrayOf(0, 180, 70, 180)
        amplitudes = intArrayOf(0, amp, 0, amp)
      }
      ObstacleDirection.LEFT -> {
        timings = longArrayOf(0, 90, 60, 90)
        amplitudes = intArrayOf(0, amp, 0, amp)
      }
      ObstacleDirection.RIGHT -> {
        timings = longArrayOf(0, 80, 55, 80, 55, 80)
        amplitudes = intArrayOf(0, amp, 0, amp, 0, amp)
      }
      ObstacleDirection.BACK -> {
        timings = longArrayOf(0, 260, 80, 100)
        amplitudes = intArrayOf(0, amp, 0, amp)
      }
    }
    vibrateWaveform(timings, amplitudes)
    if (audioCuesEnabled && accessibilityMode != AccessibilityMode.DEAFBLIND) {
      speak("Obstacle ${direction.label}, $distanceCm centimeters")
    }
  }

  fun triggerRaceEvent(
    eventLabel: String,
    timings: LongArray,
    amplitudes: IntArray,
    accessibilityMode: AccessibilityMode,
    audioCuesEnabled: Boolean,
  ) {
    vibrateWaveform(timings, amplitudes)
    if (audioCuesEnabled && accessibilityMode != AccessibilityMode.DEAFBLIND) {
      speak(eventLabel)
    }
  }

  fun triggerStartCue(accessibilityMode: AccessibilityMode, audioCuesEnabled: Boolean) {
    triggerRaceEvent(
      eventLabel = "Race started",
      timings = longArrayOf(0, 100, 60, 120, 60, 220),
      amplitudes = intArrayOf(0, 160, 0, 200, 0, 255),
      accessibilityMode = accessibilityMode,
      audioCuesEnabled = audioCuesEnabled,
    )
  }

  fun triggerPauseCue(accessibilityMode: AccessibilityMode, audioCuesEnabled: Boolean) {
    triggerRaceEvent(
      eventLabel = "Race paused",
      timings = longArrayOf(0, 220, 90, 120),
      amplitudes = intArrayOf(0, 210, 0, 140),
      accessibilityMode = accessibilityMode,
      audioCuesEnabled = audioCuesEnabled,
    )
  }

  fun triggerFinishCue(accessibilityMode: AccessibilityMode, audioCuesEnabled: Boolean) {
    triggerRaceEvent(
      eventLabel = "Race completed",
      timings = longArrayOf(0, 300, 100, 350),
      amplitudes = intArrayOf(0, 255, 0, 255),
      accessibilityMode = accessibilityMode,
      audioCuesEnabled = audioCuesEnabled,
    )
  }

  fun triggerDeviationWarning(
    deviationMeters: Int,
    accessibilityMode: AccessibilityMode,
    audioCuesEnabled: Boolean,
  ) {
    triggerRaceEvent(
      eventLabel = "Route deviation, $deviationMeters meters off course",
      timings = longArrayOf(0, 110, 50, 110, 50, 110, 50, 220),
      amplitudes = intArrayOf(0, 255, 0, 255, 0, 255, 0, 255),
      accessibilityMode = accessibilityMode,
      audioCuesEnabled = audioCuesEnabled,
    )
  }

  fun triggerDisconnectWarning(accessibilityMode: AccessibilityMode, audioCuesEnabled: Boolean) {
    triggerRaceEvent(
      eventLabel = "Waist belt disconnected",
      timings = longArrayOf(0, 250, 90, 250, 90, 250),
      amplitudes = intArrayOf(0, 255, 0, 255, 0, 255),
      accessibilityMode = accessibilityMode,
      audioCuesEnabled = audioCuesEnabled,
    )
  }

  fun speak(message: String) {
    if (ttsReady) {
      tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "blindrun_${System.currentTimeMillis()}")
    }
  }

  private fun vibrateWaveform(timings: LongArray, amplitudes: IntArray) {
    val vib = vibrator ?: return
    if (!vib.hasVibrator()) return
    try {
      if (vib.hasAmplitudeControl()) {
        vib.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
      } else {
        vib.vibrate(VibrationEffect.createWaveform(timings, -1))
      }
    } catch (_: Exception) {
      // Ignore if vibration permission/service temporarily unavailable
    }
  }

  fun shutdown() {
    tts?.stop()
    tts?.shutdown()
  }
}
