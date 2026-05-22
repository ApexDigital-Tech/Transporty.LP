package com.example

import android.util.Log
import io.github.jan.tennert.supabase.createSupabaseClient
import io.github.jan.tennert.supabase.postgrest.Postgrest
import io.github.jan.tennert.supabase.realtime.Realtime
import io.github.jan.tennert.supabase.postgrest.postgrest
import io.github.jan.tennert.supabase.realtime.realtime

object SupabaseClient {
    private const val TAG = "SupabaseClient"

    val client by lazy {
        try {
            val url = BuildConfig.SUPABASE_URL
            val key = BuildConfig.SUPABASE_ANON_KEY
            Log.d(TAG, "Initializing Supabase with URL: $url")
            createSupabaseClient(
                supabaseUrl = url,
                supabaseKey = key
            ) {
                install(Postgrest)
                install(Realtime)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Supabase client: ${e.message}", e)
            null
        }
    }

    val postgrest get() = client?.postgrest
    val realtime get() = client?.realtime
}
