package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
  @Query("SELECT * FROM user_profiles WHERE id = :userId LIMIT 1")
  fun observeProfile(userId: String): Flow<UserProfileEntity?>

  @Query("SELECT * FROM user_profiles WHERE id = :userId LIMIT 1")
  suspend fun getProfile(userId: String): UserProfileEntity?

  @Query("SELECT * FROM user_profiles ORDER BY fullName ASC")
  fun observeAllProfiles(): Flow<List<UserProfileEntity>>

  @Query("SELECT * FROM user_profiles WHERE synced = 0")
  suspend fun getUnsyncedProfiles(): List<UserProfileEntity>

  @Query("SELECT EXISTS(SELECT 1 FROM user_profiles WHERE synced = 0)")
  suspend fun hasUnsynced(): Boolean

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertProfile(profile: UserProfileEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertProfiles(profiles: List<UserProfileEntity>)
}

@Dao
interface WearableDeviceDao {
  @Query("SELECT * FROM wearable_devices ORDER BY lastSeenEpochMs DESC")
  fun observeAllDevices(): Flow<List<WearableDeviceEntity>>

  @Query("SELECT * FROM wearable_devices WHERE ownerAthleteId = :athleteId ORDER BY lastSeenEpochMs DESC")
  fun observeDevicesForAthlete(athleteId: String): Flow<List<WearableDeviceEntity>>

  @Query("SELECT * FROM wearable_devices WHERE serialCode = :serialCode LIMIT 1")
  suspend fun getBySerial(serialCode: String): WearableDeviceEntity?

  @Query("SELECT * FROM wearable_devices WHERE synced = 0")
  suspend fun getUnsyncedDevices(): List<WearableDeviceEntity>

  @Query("SELECT EXISTS(SELECT 1 FROM wearable_devices WHERE synced = 0)")
  suspend fun hasUnsynced(): Boolean

  @Query("SELECT COUNT(*) FROM wearable_devices WHERE ownerAthleteId = :athleteId AND synced = 0")
  fun observeUnsyncedCount(athleteId: String): Flow<Int>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertDevice(device: WearableDeviceEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertDevices(devices: List<WearableDeviceEntity>)
}

@Dao
interface RaceDao {
  @Query("SELECT * FROM races WHERE athleteId = :athleteId ORDER BY createdAtEpochMs DESC")
  fun observeRacesForAthlete(athleteId: String): Flow<List<RaceEntity>>

  @Query("SELECT * FROM races ORDER BY createdAtEpochMs DESC")
  fun observeAllRaces(): Flow<List<RaceEntity>>

  @Query("SELECT * FROM races WHERE id = :raceId LIMIT 1")
  fun observeRace(raceId: String): Flow<RaceEntity?>

  @Query("SELECT * FROM races WHERE id = :raceId LIMIT 1")
  suspend fun getRace(raceId: String): RaceEntity?

  @Query("SELECT * FROM races WHERE synced = 0")
  suspend fun getUnsyncedRaces(): List<RaceEntity>

  @Query("SELECT EXISTS(SELECT 1 FROM races WHERE synced = 0)")
  suspend fun hasUnsynced(): Boolean

  @Query("SELECT COUNT(*) FROM races WHERE athleteId = :athleteId AND synced = 0")
  fun observeUnsyncedCount(athleteId: String): Flow<Int>

  // After a process death a race can be left "running" with no GPS/timer behind it.
  @Query("UPDATE races SET status = 'paused', synced = 0 WHERE status = 'running'")
  suspend fun pauseRunningRaces()

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertRace(race: RaceEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertRaces(races: List<RaceEntity>)

  @Query("DELETE FROM races WHERE id = :raceId")
  suspend fun deleteRace(raceId: String)
}

@Dao
interface GpsTrackPointDao {
  @Query("SELECT * FROM gps_trackpoints WHERE raceId = :raceId ORDER BY recordedAtEpochMs ASC")
  fun observeTrackPoints(raceId: String): Flow<List<GpsTrackPointEntity>>

  @Query("SELECT * FROM gps_trackpoints WHERE synced = 0")
  suspend fun getUnsyncedTrackPoints(): List<GpsTrackPointEntity>

  @Query("SELECT EXISTS(SELECT 1 FROM gps_trackpoints WHERE synced = 0)")
  suspend fun hasUnsynced(): Boolean

  @Query("SELECT COUNT(*) FROM gps_trackpoints WHERE athleteId = :athleteId AND synced = 0")
  fun observeUnsyncedCount(athleteId: String): Flow<Int>

  @Query("UPDATE gps_trackpoints SET synced = 1 WHERE id IN (:ids)")
  suspend fun markSynced(ids: List<String>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertTrackPoint(point: GpsTrackPointEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertTrackPoints(points: List<GpsTrackPointEntity>)
}

@Dao
interface ObstacleEventDao {
  @Query("SELECT * FROM obstacle_events WHERE athleteId = :athleteId ORDER BY recordedAtEpochMs DESC LIMIT 50")
  fun observeObstaclesForAthlete(athleteId: String): Flow<List<ObstacleEventEntity>>

  @Query("SELECT * FROM obstacle_events WHERE raceId = :raceId ORDER BY recordedAtEpochMs DESC")
  fun observeObstaclesForRace(raceId: String): Flow<List<ObstacleEventEntity>>

  @Query("SELECT * FROM obstacle_events WHERE synced = 0")
  suspend fun getUnsyncedObstacles(): List<ObstacleEventEntity>

  @Query("SELECT EXISTS(SELECT 1 FROM obstacle_events WHERE synced = 0)")
  suspend fun hasUnsynced(): Boolean

  @Query("SELECT COUNT(*) FROM obstacle_events WHERE athleteId = :athleteId AND synced = 0")
  fun observeUnsyncedCount(athleteId: String): Flow<Int>

  @Query("UPDATE obstacle_events SET synced = 1 WHERE id IN (:ids)")
  suspend fun markSynced(ids: List<String>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertObstacle(event: ObstacleEventEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertObstacles(events: List<ObstacleEventEntity>)
}

@Dao
interface HealthRecordDao {
  @Query("SELECT * FROM health_records WHERE athleteId = :athleteId ORDER BY recordedAtEpochMs DESC LIMIT 100")
  fun observeHealthRecords(athleteId: String): Flow<List<HealthRecordEntity>>

  @Query("SELECT * FROM health_records WHERE synced = 0")
  suspend fun getUnsyncedHealthRecords(): List<HealthRecordEntity>

  @Query("SELECT EXISTS(SELECT 1 FROM health_records WHERE synced = 0)")
  suspend fun hasUnsynced(): Boolean

  @Query("SELECT COUNT(*) FROM health_records WHERE athleteId = :athleteId AND synced = 0")
  fun observeUnsyncedCount(athleteId: String): Flow<Int>

  @Query("UPDATE health_records SET synced = 1 WHERE id IN (:ids)")
  suspend fun markSynced(ids: List<String>)

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertHealthRecordIgnoreDuplicate(record: HealthRecordEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertHealthRecords(records: List<HealthRecordEntity>)
}

@Dao
interface AlertDao {
  @Query("SELECT * FROM alerts WHERE athleteId = :athleteId ORDER BY createdAtEpochMs DESC LIMIT 50")
  fun observeAlertsForAthlete(athleteId: String): Flow<List<AlertEntity>>

  @Query("SELECT * FROM alerts ORDER BY createdAtEpochMs DESC LIMIT 100")
  fun observeAllAlerts(): Flow<List<AlertEntity>>

  @Query("SELECT * FROM alerts WHERE synced = 0")
  suspend fun getUnsyncedAlerts(): List<AlertEntity>

  @Query("SELECT EXISTS(SELECT 1 FROM alerts WHERE synced = 0)")
  suspend fun hasUnsynced(): Boolean

  @Query("SELECT COUNT(*) FROM alerts WHERE athleteId = :athleteId AND synced = 0")
  fun observeUnsyncedCount(athleteId: String): Flow<Int>

  @Query("SELECT * FROM alerts WHERE id = :alertId LIMIT 1")
  suspend fun getAlert(alertId: String): AlertEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAlert(alert: AlertEntity)

  @Query("UPDATE alerts SET acknowledged = 1, synced = 0 WHERE id = :alertId")
  suspend fun acknowledgeAlert(alertId: String)
}

@Dao
interface CoachAssignmentDao {
  @Query("SELECT * FROM coach_assignments ORDER BY createdAtEpochMs DESC")
  fun observeAllAssignments(): Flow<List<CoachAssignmentEntity>>

  @Query("SELECT * FROM coach_assignments WHERE coachId = :coachId ORDER BY createdAtEpochMs DESC")
  fun observeAssignmentsForCoach(coachId: String): Flow<List<CoachAssignmentEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAssignment(assignment: CoachAssignmentEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAssignments(assignments: List<CoachAssignmentEntity>)

  @Query("DELETE FROM coach_assignments WHERE id = :id")
  suspend fun deleteAssignment(id: String)
}

@Dao
interface AdminAuditLogDao {
  @Query("SELECT * FROM admin_audit_logs ORDER BY createdAtEpochMs DESC LIMIT 100")
  fun observeLogs(): Flow<List<AdminAuditLogEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertLog(log: AdminAuditLogEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertLogs(logs: List<AdminAuditLogEntity>)
}
