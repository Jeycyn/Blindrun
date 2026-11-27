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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.ObsidianNight
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.VoltLime

@Composable
fun ProfileScreen(
  profile: UserProfile?,
  isOfflineSession: Boolean,
  onSaveProfile: (UserProfile) -> Unit,
  onOpenCoachPortal: () -> Unit,
  onOpenAdminPortal: () -> Unit,
  onSignOut: () -> Unit,
) {
  if (profile == null) return

  var fullName by rememberSaveable(profile.id) { mutableStateOf(profile.fullName) }
  var phone by rememberSaveable(profile.id) { mutableStateOf(profile.phone) }
  var emergencyName by rememberSaveable(profile.id) { mutableStateOf(profile.emergencyContactName) }
  var emergencyPhone by rememberSaveable(profile.id) { mutableStateOf(profile.emergencyContactPhone) }
  var mode by rememberSaveable(profile.id) { mutableStateOf(profile.accessibilityMode) }
  var intensity by rememberSaveable(profile.id) { mutableIntStateOf(profile.hapticIntensity) }
  var audioCues by rememberSaveable(profile.id) { mutableStateOf(profile.audioCuesEnabled) }
  var coachConsent by rememberSaveable(profile.id) { mutableStateOf(profile.consentedCoachSharing) }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      Text("Athlete Profile & Safety", style = MaterialTheme.typography.headlineMedium)
      Text(
        text =
          "${profile.email} • Role: ${profile.role.label} • " +
            if (isOfflineSession) "Offline Local Session" else "Supabase Authenticated",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    if (profile.role == UserRole.COACH || profile.role == UserRole.ADMIN) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          OutlinedButton(
            onClick = onOpenCoachPortal,
            modifier = Modifier.weight(1f).height(48.dp),
          ) {
            Icon(Icons.Rounded.Groups, contentDescription = null, tint = SignalCyan)
            Spacer(Modifier.size(6.dp))
            Text("Coach Desk")
          }
          if (profile.role == UserRole.ADMIN) {
            OutlinedButton(
              onClick = onOpenAdminPortal,
              modifier = Modifier.weight(1f).height(48.dp),
            ) {
              Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null, tint = VoltLime)
              Spacer(Modifier.size(6.dp))
              Text("Admin")
            }
          }
        }
      }
    }

    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(20.dp),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          OutlinedTextField(
            value = fullName,
            onValueChange = { fullName = it },
            label = { Text("Full Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Phone Number") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          Text("Accessibility Mode", style = MaterialTheme.typography.labelLarge)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AccessibilityMode.entries.forEach { item ->
              FilterChip(
                selected = mode == item,
                onClick = {
                  mode = item
                  if (item == AccessibilityMode.DEAFBLIND) audioCues = false
                },
                label = { Text(item.label) },
              )
            }
          }
          Text("Haptic Intensity: $intensity%")
          Slider(
            value = intensity.toFloat(),
            onValueChange = { intensity = it.toInt() },
            valueRange = 20f..100f,
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("Spoken Voice Cues", modifier = Modifier.weight(1f))
            Switch(
              checked = audioCues && mode != AccessibilityMode.DEAFBLIND,
              onCheckedChange = { audioCues = it },
              enabled = mode != AccessibilityMode.DEAFBLIND,
            )
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("Share Consented Health with Coach", modifier = Modifier.weight(1f))
            Switch(checked = coachConsent, onCheckedChange = { coachConsent = it })
          }
          OutlinedTextField(
            value = emergencyName,
            onValueChange = { emergencyName = it },
            label = { Text("Emergency Contact Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = emergencyPhone,
            onValueChange = { emergencyPhone = it },
            label = { Text("Emergency Contact Phone") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          Button(
            onClick = {
              onSaveProfile(
                profile.copy(
                  fullName = fullName.trim().ifBlank { profile.fullName },
                  phone = phone.trim(),
                  emergencyContactName = emergencyName.trim(),
                  emergencyContactPhone = emergencyPhone.trim(),
                  accessibilityMode = mode,
                  hapticIntensity = intensity,
                  audioCuesEnabled = audioCues && mode != AccessibilityMode.DEAFBLIND,
                  consentedCoachSharing = coachConsent,
                )
              )
            },
            colors = ButtonDefaults.buttonColors(containerColor = VoltLime, contentColor = ObsidianNight),
            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("save_profile_btn"),
          ) {
            Icon(Icons.Rounded.Save, contentDescription = null)
            Spacer(Modifier.size(6.dp))
            Text("Save Profile Changes", fontWeight = FontWeight.Bold)
          }
        }
      }
    }

    item {
      OutlinedButton(
        onClick = onSignOut,
        border = BorderStroke(1.5.dp, CoralAlert),
        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("sign_out_btn"),
      ) {
        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, tint = CoralAlert)
        Spacer(Modifier.size(8.dp))
        Text("Sign Out", color = CoralAlert, fontWeight = FontWeight.Bold)
      }
    }
  }
}
