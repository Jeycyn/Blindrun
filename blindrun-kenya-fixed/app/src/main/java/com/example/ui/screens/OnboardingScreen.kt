package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContactPhone
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AccessibilityMode
import com.example.data.model.ObstacleDirection
import com.example.data.model.UserProfile
import com.example.ui.theme.ObsidianNight
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.VoltLime

@Composable
fun OnboardingScreen(
  profile: UserProfile,
  onTestHaptic: (ObstacleDirection) -> Unit,
  onCompleteOnboarding: (UserProfile) -> Unit,
) {
  var fullName by rememberSaveable(profile.id) { mutableStateOf(profile.fullName) }
  var phone by rememberSaveable(profile.id) { mutableStateOf(profile.phone) }
  var emergencyName by rememberSaveable(profile.id) { mutableStateOf(profile.emergencyContactName) }
  var emergencyPhone by rememberSaveable(profile.id) { mutableStateOf(profile.emergencyContactPhone) }
  var mode by rememberSaveable(profile.id) { mutableStateOf(profile.accessibilityMode) }
  var hapticIntensity by rememberSaveable(profile.id) { mutableIntStateOf(profile.hapticIntensity) }
  var audioCues by rememberSaveable(profile.id) { mutableStateOf(profile.audioCuesEnabled) }
  var coachConsent by rememberSaveable(profile.id) { mutableStateOf(profile.consentedCoachSharing) }

  Column(
    modifier =
      Modifier.fillMaxSize()
        .systemBarsPadding()
        .verticalScroll(rememberScrollState())
        .padding(20.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      Icon(Icons.Rounded.AccessibilityNew, contentDescription = null, tint = VoltLime)
      Text(
        text = "Athlete Accessibility Setup",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
      )
    }
    Text(
      text =
        "Configure your non-visual and haptic feedback profile. BlindRun Kenya uses distinct directional vibration waveforms for Front, Back, Left, and Right obstacles.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    OutlinedTextField(
      value = fullName,
      onValueChange = { fullName = it },
      label = { Text("Athlete Full Name") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
      value = phone,
      onValueChange = { phone = it },
      label = { Text("Phone Number (Optional)") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )

    Text("Accessibility Profile", style = MaterialTheme.typography.titleMedium)
    AccessibilityMode.entries.forEach { option ->
      val selected = mode == option
      Card(
        onClick = {
          mode = option
          if (option == AccessibilityMode.DEAFBLIND) audioCues = false
        },
        colors =
          CardDefaults.cardColors(
            containerColor =
              if (selected) VoltLime.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant
          ),
        border =
          BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) VoltLime else MaterialTheme.colorScheme.outline,
          ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().testTag("accessibility_mode_${option.apiValue}"),
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            text = option.label,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) VoltLime else MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = option.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      shape = RoundedCornerShape(18.dp),
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(Icons.Rounded.Vibration, contentDescription = null, tint = SignalCyan)
          Text("Haptic & Audio Calibration", style = MaterialTheme.typography.titleMedium)
        }
        Text("Vibration Intensity: $hapticIntensity%", style = MaterialTheme.typography.bodyMedium)
        Slider(
          value = hapticIntensity.toFloat(),
          onValueChange = { hapticIntensity = it.toInt() },
          valueRange = 20f..100f,
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Spoken Voice Cues (TTS)", style = MaterialTheme.typography.bodyLarge)
            Text(
              "Automatically disabled in Deafblind mode",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(
            checked = audioCues && mode != AccessibilityMode.DEAFBLIND,
            onCheckedChange = { audioCues = it },
            enabled = mode != AccessibilityMode.DEAFBLIND,
          )
        }
        OutlinedButton(
          onClick = { onTestHaptic(ObstacleDirection.FRONT) },
          modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
          Text("Feel Sample Front Haptic Pulse")
        }
      }
    }

    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      shape = RoundedCornerShape(18.dp),
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(Icons.Rounded.ContactPhone, contentDescription = null, tint = VoltLime)
          Text("Emergency Contact & Coach Consent", style = MaterialTheme.typography.titleMedium)
        }
        OutlinedTextField(
          value = emergencyName,
          onValueChange = { emergencyName = it },
          label = { Text("Emergency Contact Name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("emergency_name_input"),
        )
        OutlinedTextField(
          value = emergencyPhone,
          onValueChange = { emergencyPhone = it },
          label = { Text("Emergency Contact Phone") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("emergency_phone_input"),
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Share authorized health & live race data with assigned coach",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
          )
          Switch(checked = coachConsent, onCheckedChange = { coachConsent = it })
        }
      }
    }

    Button(
      onClick = {
        onCompleteOnboarding(
          profile.copy(
            fullName = fullName.ifBlank { profile.fullName },
            phone = phone.trim(),
            emergencyContactName = emergencyName.trim(),
            emergencyContactPhone = emergencyPhone.trim(),
            accessibilityMode = mode,
            hapticIntensity = hapticIntensity,
            audioCuesEnabled = audioCues && mode != AccessibilityMode.DEAFBLIND,
            consentedCoachSharing = coachConsent,
            onboardingCompleted = true,
          )
        )
      },
      colors = ButtonDefaults.buttonColors(containerColor = VoltLime, contentColor = ObsidianNight),
      modifier = Modifier.fillMaxWidth().height(54.dp).testTag("complete_onboarding_button"),
    ) {
      Icon(Icons.Rounded.CheckCircle, contentDescription = null)
      Spacer(Modifier.padding(horizontal = 4.dp))
      Text("Save Profile & Enter Dashboard", fontWeight = FontWeight.Bold)
    }
  }
}
