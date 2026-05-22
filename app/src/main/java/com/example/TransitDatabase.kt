package com.example

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

// 1. Entities

@Serializable
@Entity(tableName = "drivers")
data class DriverEntity(
    @PrimaryKey
    @SerialName("phone") val phone: String,
    @SerialName("name") val name: String,
    @SerialName("license_plate") val licensePlate: String,
    @SerialName("status") val status: String, // "En Ruta", "Fuera de Servicio", "Descanso"
    @SerialName("current_route") val currentRoute: String,
    @SerialName("earnings") val earnings: Int,
    @SerialName("points") val points: Int,
    @SerialName("last_updated") val lastUpdated: Long = System.currentTimeMillis(),
    @SerialName("tenant_id") val tenantId: String = "sindicato_central"
)

@Serializable
@Entity(tableName = "shifts")
data class ShiftEntity(
    @PrimaryKey(autoGenerate = true)
    @SerialName("id") val id: Int = 0,
    @SerialName("driver_phone") val driverPhone: String,
    @SerialName("route_code") val routeCode: String,
    @SerialName("start_time") val startTime: Long,
    @SerialName("end_time") val endTime: Long,
    @SerialName("earnings") val earnings: Int,
    @SerialName("points") val points: Int,
    @SerialName("status") val status: String, // "Activo", "Completado"
    @SerialName("tenant_id") val tenantId: String = "sindicato_central"
)

// 2. DAOs

@Dao
interface DriverDao {
    @Query("SELECT * FROM drivers ORDER BY lastUpdated DESC")
    fun getAllDriversFlow(): Flow<List<DriverEntity>>

    @Query("SELECT * FROM drivers WHERE phone = :phone LIMIT 1")
    suspend fun getDriverByPhone(phone: String): DriverEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDriver(driver: DriverEntity)

    @Update
    suspend fun updateDriver(driver: DriverEntity)

    @Query("UPDATE drivers SET status = :status, lastUpdated = :lastUpdated WHERE phone = :phone")
    suspend fun updateDriverStatus(phone: String, status: String, lastUpdated: Long = System.currentTimeMillis())

    @Query("UPDATE drivers SET earnings = :earnings, points = :points, lastUpdated = :lastUpdated WHERE phone = :phone")
    suspend fun updateDriverEarnings(phone: String, earnings: Int, points: Int, lastUpdated: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteDriver(driver: DriverEntity)

    @Query("SELECT COUNT(*) FROM drivers")
    suspend fun getDriversCount(): Int
}

@Dao
interface ShiftDao {
    @Query("SELECT * FROM shifts ORDER BY startTime DESC")
    fun getAllShiftsFlow(): Flow<List<ShiftEntity>>

    @Query("SELECT * FROM shifts WHERE driverPhone = :driverPhone AND status = 'Activo' LIMIT 1")
    suspend fun getActiveShiftForDriver(driverPhone: String): ShiftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShift(shift: ShiftEntity)

    @Update
    suspend fun updateShift(shift: ShiftEntity)

    @Query("SELECT * FROM shifts WHERE driverPhone = :driverPhone ORDER BY startTime DESC")
    fun getShiftsForDriverFlow(driverPhone: String): Flow<List<ShiftEntity>>
}

// 3. Database

@Database(entities = [DriverEntity::class, ShiftEntity::class], version = 3, exportSchema = false)
abstract class TransitDatabase : RoomDatabase() {
    abstract fun driverDao(): DriverDao
    abstract fun shiftDao(): ShiftDao

    companion object {
        @Volatile
        private var INSTANCE: TransitDatabase? = null

        fun getDatabase(context: Context): TransitDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TransitDatabase::class.java,
                    "la_paz_transit_database_v3"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

