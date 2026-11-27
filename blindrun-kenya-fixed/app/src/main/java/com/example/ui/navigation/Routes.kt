package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface AppRoute {
  @Serializable data object Auth : AppRoute

  @Serializable data object Onboarding : AppRoute

  @Serializable data object Dashboard : AppRoute

  @Serializable data object Devices : AppRoute

  @Serializable data object QrScanner : AppRoute

  @Serializable data object RaceHub : AppRoute

  @Serializable data class LiveRace(val raceId: String) : AppRoute

  @Serializable data object Health : AppRoute

  @Serializable data object History : AppRoute

  @Serializable data object Profile : AppRoute

  @Serializable data object CoachDashboard : AppRoute

  @Serializable data object AdminDashboard : AppRoute
}
