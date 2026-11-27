package com.example

import com.example.data.model.ObstacleDirection
import com.example.data.model.ObstacleSeverity
import com.example.data.model.RaceSession
import com.example.data.model.RouteWaypoint
import com.example.data.remote.SupabaseAuthResponse
import com.example.hardware.BleWaistBeltManager
import com.example.hardware.GpsTrackingManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlindRunDomainUnitTest {

  @Test
  fun remainingDistanceFollowsFormulaDtMinusDc() {
    val race =
      RaceSession(
        id = "race-1",
        athleteId = "athlete-1",
        createdBy = "athlete-1",
        name = "Nairobi Athletics Trial",
        raceType = "Long distance",
        totalDistanceMeters = 10_000.0,
        coveredDistanceMeters = 3_640.0,
      )
    assertEquals(6_360.0, race.remainingDistanceMeters, 0.01)
    assertEquals(0.364f, race.progressFraction, 0.001f)
  }

  @Test
  fun remainingDistanceClampsAtZeroWhenCompletedOrOvershot() {
    val race =
      RaceSession(
        id = "race-2",
        athleteId = "athlete-1",
        createdBy = "athlete-1",
        name = "Finish Test",
        raceType = "Sprint",
        totalDistanceMeters = 5_000.0,
        coveredDistanceMeters = 5_120.0,
      )
    assertEquals(0.0, race.remainingDistanceMeters, 0.0)
  }

  @Test
  fun bleObstaclePacketParsesAllFourDirections() {
    val front = BleWaistBeltManager.parseObstaclePacket("OBS:FRONT:85:HIGH")
    assertNotNull(front)
    assertEquals(ObstacleDirection.FRONT, front!!.direction)
    assertEquals(85, front.distanceCm)
    assertEquals(ObstacleSeverity.HIGH, front.severity)

    val left = BleWaistBeltManager.parseObstaclePacket("OBS:LEFT:120:MEDIUM")
    assertEquals(ObstacleDirection.LEFT, left!!.direction)

    val right = BleWaistBeltManager.parseObstaclePacket("RIGHT,150,LOW")
    assertEquals(ObstacleDirection.RIGHT, right!!.direction)

    val back = BleWaistBeltManager.parseObstaclePacket("OBS:BACK:95:MEDIUM")
    assertEquals(ObstacleDirection.BACK, back!!.direction)
  }

  @Test
  fun qrCodeParserRejectsEmbeddedSecretsAndParsesDeviceIdentity() {
    val insecure =
      BleWaistBeltManager.parseBeltQrCode(
        "blindrun://belt?serial=BRK-01&token=supersecret123"
      )
    assertNull(insecure)

    val validSerial = BleWaistBeltManager.parseBeltQrCode("BRK-BELT-042")
    assertNotNull(validSerial)
    assertEquals("BRK-BELT-042", validSerial!!.serialCode)
  }

  @Test
  fun haversineAndRouteDeviationCalculateAccurateDistances() {
    val wp1 = RouteWaypoint(-1.2921, 36.8219)
    val wp2 = RouteWaypoint(-1.2921, 36.8309) // ~1.0 km east near Nairobi
    val dist = GpsTrackingManager.haversineMeters(wp1.latitude, wp1.longitude, wp2.latitude, wp2.longitude)
    assertTrue("Expected ~1000m, got $dist", dist in 950.0..1050.0)

    val devOnRoute = GpsTrackingManager.calculateMinDeviationMeters(-1.2921, 36.8219, listOf(wp1, wp2))
    assertEquals(0.0, devOnRoute, 0.5)
  }

  @Test
  fun supabaseSignupParsesBothEmailConfirmationAndInstantSessionPayloads() {
    val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    val adapter = moshi.adapter(SupabaseAuthResponse::class.java)

    val pendingConfirmJson =
      """
      {
        "id": "b1f2a3c4-1111-2222-3333-444455556666",
        "aud": "authenticated",
        "role": "authenticated",
        "email": "tarqueentech@gmail.com",
        "confirmation_sent_at": "2026-11-26T18:45:00Z",
        "user_metadata": {"full_name": "TARQUEEN JEPKOECH", "role": "athlete"},
        "identities": [{"id": "id-1"}]
      }
      """.trimIndent()
    val pending = adapter.fromJson(pendingConfirmJson)
    assertNotNull(pending)
    assertEquals("b1f2a3c4-1111-2222-3333-444455556666", pending!!.id)
    assertEquals("tarqueentech@gmail.com", pending.email)
    assertNull(pending.accessToken)
    assertNotNull(pending.confirmationSentAt)
    assertEquals(1, pending.identities?.size)

    val instantSessionJson =
      """
      {
        "access_token": "jwt.token.value",
        "refresh_token": "refresh-123",
        "expires_in": 3600,
        "user": {
          "id": "u-999",
          "email": "tarqueentech@gmail.com"
        }
      }
      """.trimIndent()
    val instant = adapter.fromJson(instantSessionJson)
    assertNotNull(instant)
    assertEquals("jwt.token.value", instant!!.accessToken)
    assertEquals("u-999", instant.user?.id)
  }

  @Test
  fun deviationIsMeasuredToRouteSegmentsNotJustWaypoints() {
    // Two waypoints ~1 km apart. A runner exactly mid-way and ~10 m north of the line is ON route;
    // the old vertex-only maths reported ~500 m off route.
    val a = RouteWaypoint(-1.2921, 36.8219)
    val b = RouteWaypoint(-1.2921, 36.8309)
    val midLon = (a.longitude + b.longitude) / 2.0
    val tenMetresNorth = -1.2921 + 10.0 / 111_195.0
    val dev = GpsTrackingManager.calculateMinDeviationMeters(tenMetresNorth, midLon, listOf(a, b))
    assertEquals(10.0, dev, 1.0)

    // Beyond the end of the route the distance is to the end point, not to the infinite line.
    val pastEnd = GpsTrackingManager.calculateMinDeviationMeters(-1.2921, 36.8309 + 0.001, listOf(a, b))
    assertTrue("Expected ~111m past the end, got $pastEnd", pastEnd in 100.0..122.0)
  }

  @Test
  fun malformedObstaclePacketsAreRejectedInsteadOfBecomingFrontObstacles() {
    assertNull(BleWaistBeltManager.parseObstaclePacket("OBS:UP:85:HIGH"))
    assertNull(BleWaistBeltManager.parseObstaclePacket("garbage"))
    assertNull(BleWaistBeltManager.parseObstaclePacket(""))
  }
}

