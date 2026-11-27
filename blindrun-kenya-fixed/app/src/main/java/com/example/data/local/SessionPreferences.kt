package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore: DataStore<Preferences> by
  preferencesDataStore(name = "blindrun_session_prefs")

data class StoredSession(
  val userId: String = "",
  val email: String = "",
  val fullName: String = "",
  val role: String = "athlete",
  val accessToken: String = "",
  val refreshToken: String = "",
  val tokenExpiresAtEpochSec: Long = 0L,
  val isOfflineSession: Boolean = false,
  val pairedBeltMac: String = "",
  val pairedBeltSerial: String = "",
  val highContrastLightRaceHud: Boolean = false,
  val hapticIntensity: Int = 85,
  val audioCuesEnabled: Boolean = true,
  val lastSyncEpochMs: Long = 0L,
) {
  val isAuthenticated: Boolean
    get() = userId.isNotBlank() && (accessToken.isNotBlank() || isOfflineSession)
}

class SessionPreferences(private val context: Context) {

  private object Keys {
    val USER_ID = stringPreferencesKey("user_id")
    val EMAIL = stringPreferencesKey("email")
    val FULL_NAME = stringPreferencesKey("full_name")
    val ROLE = stringPreferencesKey("role")
    val ACCESS_TOKEN = stringPreferencesKey("access_token")
    val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
    val TOKEN_EXPIRES_AT = longPreferencesKey("token_expires_at")
    val IS_OFFLINE_SESSION = booleanPreferencesKey("is_offline_session")
    val PAIRED_BELT_MAC = stringPreferencesKey("paired_belt_mac")
    val PAIRED_BELT_SERIAL = stringPreferencesKey("paired_belt_serial")
    val LIGHT_RACE_HUD = booleanPreferencesKey("light_race_hud")
    val HAPTIC_INTENSITY = intPreferencesKey("haptic_intensity")
    val AUDIO_CUES = booleanPreferencesKey("audio_cues")
    val LAST_SYNC_MS = longPreferencesKey("last_sync_ms")
  }

  val sessionFlow: Flow<StoredSession> =
    context.sessionDataStore.data.map { prefs ->
      StoredSession(
        userId = prefs[Keys.USER_ID] ?: "",
        email = prefs[Keys.EMAIL] ?: "",
        fullName = prefs[Keys.FULL_NAME] ?: "",
        role = prefs[Keys.ROLE] ?: "athlete",
        accessToken = prefs[Keys.ACCESS_TOKEN] ?: "",
        refreshToken = prefs[Keys.REFRESH_TOKEN] ?: "",
        tokenExpiresAtEpochSec = prefs[Keys.TOKEN_EXPIRES_AT] ?: 0L,
        isOfflineSession = prefs[Keys.IS_OFFLINE_SESSION] ?: false,
        pairedBeltMac = prefs[Keys.PAIRED_BELT_MAC] ?: "",
        pairedBeltSerial = prefs[Keys.PAIRED_BELT_SERIAL] ?: "",
        highContrastLightRaceHud = prefs[Keys.LIGHT_RACE_HUD] ?: false,
        hapticIntensity = prefs[Keys.HAPTIC_INTENSITY] ?: 85,
        audioCuesEnabled = prefs[Keys.AUDIO_CUES] ?: true,
        lastSyncEpochMs = prefs[Keys.LAST_SYNC_MS] ?: 0L,
      )
    }

  suspend fun getSession(): StoredSession = sessionFlow.first()

  suspend fun saveAuthenticatedSession(
    userId: String,
    email: String,
    fullName: String,
    role: String,
    accessToken: String,
    refreshToken: String,
    expiresAtEpochSec: Long,
    isOffline: Boolean,
  ) {
    context.sessionDataStore.edit { prefs ->
      prefs[Keys.USER_ID] = userId
      prefs[Keys.EMAIL] = email
      prefs[Keys.FULL_NAME] = fullName
      prefs[Keys.ROLE] = role
      prefs[Keys.ACCESS_TOKEN] = accessToken
      prefs[Keys.REFRESH_TOKEN] = refreshToken
      prefs[Keys.TOKEN_EXPIRES_AT] = expiresAtEpochSec
      prefs[Keys.IS_OFFLINE_SESSION] = isOffline
    }
  }

  suspend fun savePairedBelt(macAddress: String, serialCode: String) {
    context.sessionDataStore.edit { prefs ->
      prefs[Keys.PAIRED_BELT_MAC] = macAddress
      prefs[Keys.PAIRED_BELT_SERIAL] = serialCode
    }
  }

  suspend fun updateAccessibilitySettings(hapticIntensity: Int, audioCues: Boolean, lightHud: Boolean) {
    context.sessionDataStore.edit { prefs ->
      prefs[Keys.HAPTIC_INTENSITY] = hapticIntensity.coerceIn(20, 100)
      prefs[Keys.AUDIO_CUES] = audioCues
      prefs[Keys.LIGHT_RACE_HUD] = lightHud
    }
  }

  suspend fun updateLastSync(epochMs: Long) {
    context.sessionDataStore.edit { prefs -> prefs[Keys.LAST_SYNC_MS] = epochMs }
  }

  suspend fun clearSession() {
    context.sessionDataStore.edit { prefs ->
      prefs.remove(Keys.USER_ID)
      prefs.remove(Keys.EMAIL)
      prefs.remove(Keys.FULL_NAME)
      prefs.remove(Keys.ROLE)
      prefs.remove(Keys.ACCESS_TOKEN)
      prefs.remove(Keys.REFRESH_TOKEN)
      prefs.remove(Keys.TOKEN_EXPIRES_AT)
      prefs.remove(Keys.IS_OFFLINE_SESSION)
    }
  }
}
