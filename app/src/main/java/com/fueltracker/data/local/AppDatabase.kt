package com.fueltracker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.fueltracker.data.local.dao.FuelStationDao
import com.fueltracker.data.local.dao.PriceSnapshotDao
import com.fueltracker.data.local.dao.RefuelLogDao
import com.fueltracker.data.local.dao.VehicleDao
import com.fueltracker.data.local.entity.FuelStationEntity
import com.fueltracker.data.local.entity.PriceSnapshotEntity
import com.fueltracker.data.local.entity.RefuelLogEntity
import com.fueltracker.data.local.entity.VehicleEntity
import com.fueltracker.domain.model.FuelType
import com.fueltracker.domain.model.PowertrainType

class Converters {
    @TypeConverter
    fun fromPowertrain(value: PowertrainType?): String? = value?.name

    @TypeConverter
    fun toPowertrain(value: String?): PowertrainType? = value?.let { enumValueOf<PowertrainType>(it) }

    @TypeConverter
    fun fromFuelType(value: FuelType?): String? = value?.name

    @TypeConverter
    fun toFuelType(value: String?): FuelType? = value?.let { enumValueOf<FuelType>(it) }
}

@Database(
    entities = [
        VehicleEntity::class,
        RefuelLogEntity::class,
        FuelStationEntity::class,
        PriceSnapshotEntity::class,
        com.fueltracker.data.local.entity.StationPriceProfileEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun refuelLogDao(): RefuelLogDao
    abstract fun fuelStationDao(): FuelStationDao
    abstract fun priceSnapshotDao(): PriceSnapshotDao
    abstract fun stationPriceProfileDao(): com.fueltracker.data.local.dao.StationPriceProfileDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `station_price_profiles` (
                        `stationId` TEXT NOT NULL,
                        `fuelType` TEXT NOT NULL,
                        `dayOfWeek` INTEGER NOT NULL,
                        `hourOfDay` INTEGER NOT NULL,
                        `runningAvgPrice` REAL NOT NULL,
                        `sampleCount` INTEGER NOT NULL,
                        `lastUpdatedEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`stationId`, `fuelType`, `dayOfWeek`, `hourOfDay`),
                        FOREIGN KEY(`stationId`) REFERENCES `fuel_stations`(`id`) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_station_price_profiles_stationId` ON `station_price_profiles` (`stationId`)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fuel_tracker.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
