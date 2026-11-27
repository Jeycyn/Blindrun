package com.example.data.remote

import com.example.BuildConfig

object SupabaseConfig {
  val supabaseUrl: String
    get() = BuildConfig.SUPABASE_URL.trim().trimEnd('/')

  val supabaseAnonKey: String
    get() = BuildConfig.SUPABASE_ANON_KEY.trim()

  val isConfigured: Boolean
    get() =
      supabaseUrl.startsWith("https://") &&
        !supabaseUrl.contains("YOUR_PROJECT_REF") &&
        supabaseAnonKey.isNotBlank() &&
        !supabaseAnonKey.contains("YOUR_SUPABASE_ANON_KEY")
}
