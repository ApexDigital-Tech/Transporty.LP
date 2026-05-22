package com.example

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import io.github.jan.tennert.supabase.postgrest.postgrest
import io.github.jan.tennert.supabase.realtime.realtime
import io.github.jan.tennert.supabase.realtime.PostgresAction
import io.github.jan.tennert.supabase.realtime.postgresChangeFlow

class TransitRepository(
    private val driverDao: DriverDao,
    private val shiftDao: ShiftDao
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var currentTenantId: String? = null
    private var realtimeChannel: io.github.jan.tennert.supabase.realtime.RealtimeChannel? = null

    val allDriversFlow: Flow<List<DriverEntity>> = driverDao.getAllDriversFlow()
    val allShiftsFlow: Flow<List<ShiftEntity>> = shiftDao.getAllShiftsFlow()

    private fun getTenantIdForPhone(phone: String): String {
        return when {
            phone.startsWith("78") -> "sindicato_norte"
            phone.startsWith("76") -> "sindicato_sur"
            else -> "sindicato_central"
        }
    }

    fun setTenant(phone: String) {
        currentTenantId = if (phone == "78756107") {
            null // SuperAdmin sees all
        } else {
            getTenantIdForPhone(phone)
        }
        Log.d("TransitRepository", "Tenant context set to: ${currentTenantId ?: "ALL (SuperAdmin)"}")
    }

    fun getDriverShiftsFlow(phone: String): Flow<List<ShiftEntity>> {
        return shiftDao.getShiftsForDriverFlow(phone)
    }

    suspend fun getDriver(phone: String): DriverEntity? {
        val local = driverDao.getDriverByPhone(phone)
        if (local != null) return local

        try {
            val client = SupabaseClient.client
            if (client != null) {
                val remote = client.postgrest.from("drivers").select {
                    filter {
                        eq("phone", phone)
                    }
                }.decodeSingleOrNull<DriverEntity>()
                
                if (remote != null) {
                    driverDao.insertDriver(remote)
                    return remote
                }
            }
        } catch (e: Exception) {
            Log.e("TransitRepository", "Error fetching driver from Supabase: ${e.message}", e)
        }
        return null
    }

    suspend fun createOrUpdateDriver(driver: DriverEntity) {
        val tenant = getTenantIdForPhone(driver.phone)
        val driverWithTenant = driver.copy(tenantId = tenant, lastUpdated = System.currentTimeMillis())

        // 1. Write locally
        driverDao.insertDriver(driverWithTenant)

        // 2. Sync to Supabase
        try {
            val client = SupabaseClient.client
            if (client != null) {
                client.postgrest.from("drivers").upsert(driverWithTenant)
                Log.d("TransitRepository", "Upserted driver to Supabase: ${driverWithTenant.phone}")
            }
        } catch (e: Exception) {
            Log.e("TransitRepository", "Error syncing driver upsert to Supabase: ${e.message}", e)
        }
    }

    suspend fun updateDriverStatus(phone: String, status: String) {
        val now = System.currentTimeMillis()
        // 1. Write locally
        driverDao.updateDriverStatus(phone, status, now)

        // 2. Sync to Supabase
        try {
            val driver = driverDao.getDriverByPhone(phone)
            val client = SupabaseClient.client
            if (driver != null && client != null) {
                client.postgrest.from("drivers").upsert(driver)
                Log.d("TransitRepository", "Synced driver status to Supabase: $phone -> $status")
            }
        } catch (e: Exception) {
            Log.e("TransitRepository", "Error syncing status update to Supabase: ${e.message}", e)
        }
    }

    suspend fun updateDriverEarnings(phone: String, earnings: Int, points: Int) {
        val now = System.currentTimeMillis()
        // 1. Write locally
        driverDao.updateDriverEarnings(phone, earnings, points, now)

        // 2. Sync to Supabase
        try {
            val driver = driverDao.getDriverByPhone(phone)
            val client = SupabaseClient.client
            if (driver != null && client != null) {
                client.postgrest.from("drivers").upsert(driver)
                Log.d("TransitRepository", "Synced driver earnings to Supabase: $phone -> $earnings")
            }
        } catch (e: Exception) {
            Log.e("TransitRepository", "Error syncing earnings update to Supabase: ${e.message}", e)
        }
    }

    suspend fun deleteDriver(driver: DriverEntity) {
        // 1. Delete locally
        driverDao.deleteDriver(driver)

        // 2. Sync to Supabase
        try {
            val client = SupabaseClient.client
            if (client != null) {
                client.postgrest.from("drivers").delete {
                    filter {
                        eq("phone", driver.phone)
                    }
                }
                Log.d("TransitRepository", "Deleted driver from Supabase: ${driver.phone}")
            }
        } catch (e: Exception) {
            Log.e("TransitRepository", "Error syncing driver delete to Supabase: ${e.message}", e)
        }
    }

    suspend fun getActiveShift(driverPhone: String): ShiftEntity? {
        val local = shiftDao.getActiveShiftForDriver(driverPhone)
        if (local != null) return local

        try {
            val client = SupabaseClient.client
            if (client != null) {
                val remote = client.postgrest.from("shifts").select {
                    filter {
                        eq("driver_phone", driverPhone)
                        eq("status", "Activo")
                    }
                }.decodeSingleOrNull<ShiftEntity>()

                if (remote != null) {
                    shiftDao.insertShift(remote)
                    return remote
                }
            }
        } catch (e: Exception) {
            Log.e("TransitRepository", "Error fetching active shift from Supabase: ${e.message}", e)
        }
        return null
    }

    suspend fun startShift(driverPhone: String, routeCode: String) {
        val tenant = getTenantIdForPhone(driverPhone)

        // End previous active shifts locally and remotely
        val active = shiftDao.getActiveShiftForDriver(driverPhone)
        if (active != null) {
            val completedLocal = active.copy(endTime = System.currentTimeMillis(), status = "Completado")
            shiftDao.updateShift(completedLocal)
            try {
                val client = SupabaseClient.client
                if (client != null) {
                    client.postgrest.from("shifts").upsert(completedLocal)
                }
            } catch (e: Exception) {
                Log.e("TransitRepository", "Error syncing closed shift: ${e.message}")
            }
        }

        // Create new active shift
        val newShift = ShiftEntity(
            driverPhone = driverPhone,
            routeCode = routeCode,
            startTime = System.currentTimeMillis(),
            endTime = 0L,
            earnings = 0,
            points = 0,
            status = "Activo",
            tenantId = tenant
        )

        shiftDao.insertShift(newShift)
        driverDao.updateDriverStatus(driverPhone, "En Ruta")

        // Sync driver status and new shift
        try {
            val updatedDriver = driverDao.getDriverByPhone(driverPhone)
            val client = SupabaseClient.client
            if (client != null) {
                if (updatedDriver != null) {
                    client.postgrest.from("drivers").upsert(updatedDriver)
                }

                val insertedShift = shiftDao.getActiveShiftForDriver(driverPhone)
                if (insertedShift != null) {
                    client.postgrest.from("shifts").upsert(insertedShift)
                    Log.d("TransitRepository", "Started shift on Supabase: ${insertedShift.id}")
                }
            }
        } catch (e: Exception) {
            Log.e("TransitRepository", "Error syncing started shift to Supabase: ${e.message}", e)
        }
    }

    suspend fun endShift(driverPhone: String, finalEarnings: Int, finalPoints: Int) {
        val active = shiftDao.getActiveShiftForDriver(driverPhone)
        if (active != null) {
            val updated = active.copy(
                endTime = System.currentTimeMillis(),
                earnings = finalEarnings,
                points = finalPoints,
                status = "Completado"
            )
            shiftDao.updateShift(updated)

            try {
                val client = SupabaseClient.client
                if (client != null) {
                    client.postgrest.from("shifts").upsert(updated)
                }
            } catch (e: Exception) {
                Log.e("TransitRepository", "Error syncing ended shift to Supabase: ${e.message}")
            }
        }

        driverDao.updateDriverStatus(driverPhone, "Fuera de Servicio")
        driverDao.updateDriverEarnings(driverPhone, finalEarnings, finalPoints)

        try {
            val updatedDriver = driverDao.getDriverByPhone(driverPhone)
            val client = SupabaseClient.client
            if (updatedDriver != null && client != null) {
                client.postgrest.from("drivers").upsert(updatedDriver)
                Log.d("TransitRepository", "Ended shift and updated driver profile on Supabase.")
            }
        } catch (e: Exception) {
            Log.e("TransitRepository", "Error syncing endShift status update: ${e.message}", e)
        }
    }

    // Sync remote data to Room
    suspend fun syncFromSupabase() {
        val client = SupabaseClient.client ?: return
        try {
            val tenant = currentTenantId
            Log.d("TransitRepository", "Fetching latest data from Supabase for tenant: ${tenant ?: "ALL"}")
            
            val remoteDrivers = if (tenant != null) {
                client.postgrest.from("drivers").select {
                    filter {
                        eq("tenant_id", tenant)
                    }
                }.decodeList<DriverEntity>()
            } else {
                client.postgrest.from("drivers").select().decodeList<DriverEntity>()
            }

            for (driver in remoteDrivers) {
                driverDao.insertDriver(driver)
            }

            val remoteShifts = if (tenant != null) {
                client.postgrest.from("shifts").select {
                    filter {
                        eq("tenant_id", tenant)
                    }
                }.decodeList<ShiftEntity>()
            } else {
                client.postgrest.from("shifts").select().decodeList<ShiftEntity>()
            }

            for (shift in remoteShifts) {
                shiftDao.insertShift(shift)
            }

            Log.d("TransitRepository", "Successfully synced ${remoteDrivers.size} drivers and ${remoteShifts.size} shifts.")
        } catch (e: Exception) {
            Log.e("TransitRepository", "Error syncing data from Supabase: ${e.message}", e)
        }
    }

    // Realtime Postgres Change Listening
    suspend fun subscribeToRealtime(scope: CoroutineScope) {
        val client = SupabaseClient.client ?: return
        val realtime = client.realtime

        try {
            realtimeChannel?.unsubscribe()

            val channelName = "realtime_transit_" + (currentTenantId ?: "all")
            val channel = realtime.createChannel(channelName)

            // 1. Listen for drivers
            val driverFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "drivers"
            }
            scope.launch(Dispatchers.IO) {
                driverFlow.collect { action ->
                    try {
                        when (action) {
                            is PostgresAction.Insert -> {
                                val driver = json.decodeFromJsonElement<DriverEntity>(action.record)
                                if (currentTenantId == null || driver.tenantId == currentTenantId) {
                                    driverDao.insertDriver(driver)
                                }
                            }
                            is PostgresAction.Update -> {
                                val driver = json.decodeFromJsonElement<DriverEntity>(action.record)
                                if (currentTenantId == null || driver.tenantId == currentTenantId) {
                                    driverDao.insertDriver(driver)
                                }
                            }
                            is PostgresAction.Delete -> {
                                val phone = action.oldRecord["phone"]?.jsonPrimitive?.content
                                if (phone != null) {
                                    val local = driverDao.getDriverByPhone(phone)
                                    if (local != null) {
                                        driverDao.deleteDriver(local)
                                    }
                                }
                            }
                            else -> {}
                        }
                    } catch (e: Exception) {
                        Log.e("TransitRepository", "Realtime drivers update failed: ${e.message}")
                    }
                }
            }

            // 2. Listen for shifts
            val shiftFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "shifts"
            }
            scope.launch(Dispatchers.IO) {
                shiftFlow.collect { action ->
                    try {
                        when (action) {
                            is PostgresAction.Insert -> {
                                val shift = json.decodeFromJsonElement<ShiftEntity>(action.record)
                                if (currentTenantId == null || shift.tenantId == currentTenantId) {
                                    shiftDao.insertShift(shift)
                                }
                            }
                            is PostgresAction.Update -> {
                                val shift = json.decodeFromJsonElement<ShiftEntity>(action.record)
                                if (currentTenantId == null || shift.tenantId == currentTenantId) {
                                    shiftDao.insertShift(shift)
                                }
                            }
                            else -> {}
                        }
                    } catch (e: Exception) {
                        Log.e("TransitRepository", "Realtime shifts update failed: ${e.message}")
                    }
                }
            }

            channel.subscribe()
            realtimeChannel = channel
            Log.d("TransitRepository", "Subscribed to realtime channel: $channelName")
        } catch (e: Exception) {
            Log.e("TransitRepository", "Realtime connection setup failed: ${e.message}", e)
        }
    }

    // Seed Sample data if database is empty
    fun seedSampleDataIfEmpty(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            try {
                val count = driverDao.getDriversCount()
                if (count == 0) {
                    val sampleDrivers = listOf(
                        DriverEntity("76543210", "Don Ciprián Mamani", "LPZ-4821", "En Ruta", "16 JULIO FARELLÓN", 450, 15, tenantId = "sindicato_sur"),
                        DriverEntity("71239845", "Doña Juana Quispe", "LPZ-7215", "Descanso", "6 DE AGOSTO COLCAPIRHUA", 320, 22, tenantId = "sindicato_central"),
                        DriverEntity("68421097", "Faustino Flores", "LPZ-9938", "Fuera de Servicio", "TELEFÉRICO CELESTE CONNECT", 0, 10, tenantId = "sindicato_central"),
                        DriverEntity("73210498", "Ramiro Vargas", "LPZ-2131", "En Ruta", "LÍNEA AMARILLA EXPRESO", 580, 31, tenantId = "sindicato_central")
                    )
                    for (driver in sampleDrivers) {
                        driverDao.insertDriver(driver)
                        try {
                            val client = SupabaseClient.client
                            if (client != null) {
                                client.postgrest.from("drivers").upsert(driver)
                            }
                        } catch (e: Exception) {
                            Log.e("TransitRepository", "Seeding driver to Supabase failed: ${e.message}")
                        }
                    }

                    val sampleShifts = listOf(
                        ShiftEntity(
                            driverPhone = "76543210",
                            routeCode = "212_CEJA",
                            startTime = System.currentTimeMillis() - 7200000,
                            endTime = System.currentTimeMillis() - 3600000,
                            earnings = 150,
                            points = 5,
                            status = "Completado",
                            tenantId = "sindicato_sur"
                        ),
                        ShiftEntity(
                            driverPhone = "71239845",
                            routeCode = "300_SOPOCACHI",
                            startTime = System.currentTimeMillis() - 14400000,
                            endTime = System.currentTimeMillis() - 10800000,
                            earnings = 220,
                            points = 12,
                            status = "Completado",
                            tenantId = "sindicato_central"
                        )
                    )
                    for (shift in sampleShifts) {
                        shiftDao.insertShift(shift)
                        try {
                            val client = SupabaseClient.client
                            if (client != null) {
                                client.postgrest.from("shifts").upsert(shift)
                            }
                        } catch (e: Exception) {
                            Log.e("TransitRepository", "Seeding shift to Supabase failed: ${e.message}")
                        }
                    }
                    Log.d("TransitRepository", "Local and remote databases seeded successfully.")
                }
            } catch (e: Exception) {
                Log.e("TransitRepository", "Error seeding sample data: ${e.message}", e)
            }
        }
    }
}
