package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
  entities =
    [
      UserProfileEntity::class,
      WearableDeviceEntity::class,
      RaceEntity::class,
      GpsTrackPointEntity::class,
      ObstacleEventEntity::class,
      HealthRecordEntity::class,
      AlertEntity::class,
      CoachAssignmentEntity::class,
      AdminAuditLogEntity::class,
    ],
  version = 1,
  exportSchema = false,
)
abstract class BlindRunDatabase : RoomDatabase() {
  abstract fun userProfileDao(): UserProfileDao

  abstract fun wearableDeviceDao(): WearableDeviceDao

  abstract fun raceDao(): RaceDao

  abstract fun gpsTrackPointDao(): GpsTrackPointDao

  abstract fun obstacleEventDao(): ObstacleEventDao

  abstract fun healthRecordDao(): HealthRecordDao

  abstract fun alertDao(): AlertDao

  abstract fun coachAssignmentDao(): CoachAssignmentDao

  abstract fun adminAuditLogDao(): AdminAuditLogDao

  companion object {
    @Volatile private var INSTANCE: BlindRunDatabase? = null

    fun getInstance(context: Context): BlindRunDatabase {
      return INSTANCE
        ?: synchronized(this) {
          val instance =
            Room.databaseBuilder(
                context.applicationContext,
                BlindRunDatabase::class.java,
                "blindrun_kenya_offline.db",
              )
              .fallbackToDestructiveMigration(dropAllTables = true)
              .build()
          INSTANCE = instance
          instance
        }
    }
  }
}
