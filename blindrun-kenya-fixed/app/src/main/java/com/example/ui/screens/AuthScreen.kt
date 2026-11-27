package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OfflineBolt
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.UserRole
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.DeepSlate
import com.example.ui.theme.ElectricCobalt
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.ObsidianNight
import com.example.ui.theme.SurfaceCardDark
import com.example.ui.theme.VoltLime
import com.example.ui.viewmodel.AuthFeedback

private enum class AuthTab {
  SIGN_IN,
  REGISTER,
  RECOVER,
}

@Composable
fun AuthScreen(
  isBusy: Boolean,
  feedbackMessage: AuthFeedback?,
  isSupabaseConfigured: Boolean,
  onSignIn: (String, String) -> Unit,
  onRegister: (String, String, String, UserRole) -> Unit,
  onRecoverPassword: (String) -> Unit,
  onContinueOffline: (String, String, UserRole) -> Unit,
  onDismissFeedback: () -> Unit,
) {
  var activeTab by rememberSaveable { mutableStateOf(AuthTab.SIGN_IN) }
  var email by rememberSaveable { mutableStateOf("") }
  var password by rememberSaveable { mutableStateOf("") }
  var fullName by rememberSaveable { mutableStateOf("") }
  var selectedRole by rememberSaveable { mutableStateOf(UserRole.ATHLETE) }
  var showPassword by rememberSaveable { mutableStateOf(false) }

  LaunchedEffect(feedbackMessage) {
    if (feedbackMessage?.switchToSignIn == true) {
      activeTab = AuthTab.SIGN_IN
    }
  }

  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(
          Brush.verticalGradient(
            colors = listOf(ObsidianNight, DeepSlate, ObsidianNight),
          )
        )
        .systemBarsPadding(),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier =
        Modifier.widthIn(max = 560.dp)
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 24.dp, vertical = 28.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Surface(
        shape = RoundedCornerShape(20.dp),
        color = ElectricCobalt,
        shadowElevation = 8.dp,
        modifier = Modifier.size(68.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.DirectionsRun,
            contentDescription = stringResource(R.string.app_name),
            tint = VoltLime,
            modifier = Modifier.size(36.dp),
          )
        }
      }

      Spacer(Modifier.height(18.dp))

      Text(
        text =
          when (activeTab) {
            AuthTab.SIGN_IN -> stringResource(R.string.welcome_back)
            AuthTab.REGISTER -> stringResource(R.string.create_account_title)
            AuthTab.RECOVER -> stringResource(R.string.password_reset_title)
          },
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.onBackground,
      )
      Spacer(Modifier.height(6.dp))
      Text(
        text =
          when (activeTab) {
            AuthTab.SIGN_IN -> stringResource(R.string.sign_in_subtitle)
            AuthTab.REGISTER -> stringResource(R.string.create_account_subtitle)
            AuthTab.RECOVER -> "Enter your registered email to receive Supabase reset instructions."
          },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Spacer(Modifier.height(20.dp))

      if (!isSupabaseConfigured) {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = AmberWarning.copy(alpha = 0.14f),
          border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.5f)),
          modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
        ) {
          Text(
            text =
              "Supabase Secrets pending: configure SUPABASE_URL & SUPABASE_ANON_KEY in AI Studio Secrets for cloud sync, or use Offline Mode below for immediate BLE & GPS running.",
            style = MaterialTheme.typography.bodyMedium,
            color = AmberWarning,
            modifier = Modifier.padding(12.dp),
          )
        }
      }

      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceCardDark),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
        shape = RoundedCornerShape(24.dp),
      ) {
        Column(
          modifier = Modifier.padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            AuthModeChip("Sign In", activeTab == AuthTab.SIGN_IN) {
              activeTab = AuthTab.SIGN_IN
              onDismissFeedback()
            }
            AuthModeChip("Register", activeTab == AuthTab.REGISTER) {
              activeTab = AuthTab.REGISTER
              onDismissFeedback()
            }
            AuthModeChip("Recover", activeTab == AuthTab.RECOVER) {
              activeTab = AuthTab.RECOVER
              onDismissFeedback()
            }
          }

          AnimatedVisibility(visible = activeTab == AuthTab.REGISTER) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
              OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it },
                label = { Text(stringResource(R.string.full_name_label)) },
                leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("auth_full_name_input"),
              )
              Text(
                text = "Account Role",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                UserRole.entries.filter { it != UserRole.ADMIN }.forEach { role ->
                  FilterChip(
                    selected = selectedRole == role,
                    onClick = { selectedRole = role },
                    label = { Text(role.label) },
                    modifier = Modifier.testTag("role_chip_${role.apiValue}"),
                    colors =
                      FilterChipDefaults.filterChipColors(
                        selectedContainerColor = VoltLime,
                        selectedLabelColor = ObsidianNight,
                      ),
                  )
                }
              }
            }
          }

          OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(stringResource(R.string.email_label)) },
            leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("auth_email_input"),
          )

          if (activeTab != AuthTab.RECOVER) {
            OutlinedTextField(
              value = password,
              onValueChange = { password = it },
              label = { Text(stringResource(R.string.password_label)) },
              leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
              trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                  Icon(
                    imageVector =
                      if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = if (showPassword) "Hide password" else "Show password",
                  )
                }
              },
              visualTransformation =
                if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("auth_password_input"),
            )
          }

          if (feedbackMessage != null && feedbackMessage.message.isNotBlank()) {
            val containerColor =
              if (feedbackMessage.isError) MaterialTheme.colorScheme.error.copy(alpha = 0.16f)
              else EmeraldSuccess.copy(alpha = 0.16f)
            val textColor =
              if (feedbackMessage.isError) MaterialTheme.colorScheme.error else EmeraldSuccess
            val borderColor =
              if (feedbackMessage.isError) MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
              else EmeraldSuccess.copy(alpha = 0.5f)
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = containerColor,
              border = BorderStroke(1.dp, borderColor),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text = feedbackMessage.message,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                modifier = Modifier.padding(12.dp),
              )
            }
          }

          Button(
            onClick = {
              when (activeTab) {
                AuthTab.SIGN_IN -> onSignIn(email, password)
                AuthTab.REGISTER -> onRegister(email, password, fullName, selectedRole)
                AuthTab.RECOVER -> onRecoverPassword(email)
              }
            },
            enabled = !isBusy,
            colors =
              ButtonDefaults.buttonColors(
                containerColor = VoltLime,
                contentColor = ObsidianNight,
              ),
            modifier =
              Modifier.fillMaxWidth()
                .height(54.dp)
                .testTag("auth_primary_button"),
          ) {
            if (isBusy) {
              CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = ObsidianNight,
                strokeWidth = 2.5.dp,
              )
            } else {
              Text(
                text =
                  when (activeTab) {
                    AuthTab.SIGN_IN -> stringResource(R.string.sign_in_button)
                    AuthTab.REGISTER -> stringResource(R.string.register_button)
                    AuthTab.RECOVER -> stringResource(R.string.reset_password_button)
                  },
                fontWeight = FontWeight.Bold,
              )
            }
          }

          if (activeTab == AuthTab.SIGN_IN) {
            TextButton(
              onClick = { activeTab = AuthTab.RECOVER },
              modifier = Modifier.align(Alignment.End),
            ) {
              Text("Forgot Password?")
            }
          }
        }
      }

      Spacer(Modifier.height(18.dp))

      // Role-aware Offline Local Session (FR-10: essential functions operate without internet)
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceCardDark.copy(alpha = 0.7f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(20.dp),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            text = "Offline & Field Mode",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text =
              "Run BLE waist-belt pairing, GPS tracking, haptic obstacle alerts, and local race storage without internet connectivity. Sync later to Supabase.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            UserRole.entries.filter { it != UserRole.ADMIN }.forEach { role ->
              FilterChip(
                selected = selectedRole == role,
                onClick = { selectedRole = role },
                label = { Text(role.label) },
              )
            }
          }
          OutlinedButton(
            onClick = { onContinueOffline(fullName, email, selectedRole) },
            enabled = !isBusy,
            modifier =
              Modifier.fillMaxWidth()
                .height(52.dp)
                .testTag("offline_continue_button")
                .semantics { contentDescription = "Continue offline in ${selectedRole.label} mode" },
            border = BorderStroke(1.5.dp, VoltLime.copy(alpha = 0.7f)),
          ) {
            Icon(Icons.Rounded.OfflineBolt, contentDescription = null, tint = VoltLime)
            Spacer(Modifier.size(8.dp))
            Text(
              text = "Continue Offline as ${selectedRole.label}",
              color = VoltLime,
              fontWeight = FontWeight.SemiBold,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun AuthModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
  FilterChip(
    selected = selected,
    onClick = onClick,
    label = { Text(label) },
    colors =
      FilterChipDefaults.filterChipColors(
        selectedContainerColor = ElectricCobalt,
        selectedLabelColor = MaterialTheme.colorScheme.onBackground,
      ),
  )
}
