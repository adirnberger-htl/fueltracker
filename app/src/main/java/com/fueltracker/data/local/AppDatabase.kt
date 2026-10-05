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
        PriceSnapshotEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun refuelLogDao(): RefuelLogDao
    abstract fun fuelStationDao(): FuelStationDao
    abstract fun priceSnapshotDao(): PriceSnapshotDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fuel_tracker.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
