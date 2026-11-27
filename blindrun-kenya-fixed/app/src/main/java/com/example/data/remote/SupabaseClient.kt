package com.example.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.example.BuildConfig
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class SupabaseClient {
  private val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

  private val okHttpClient: OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(20, TimeUnit.SECONDS)
      .writeTimeout(20, TimeUnit.SECONDS)
      .addInterceptor { chain ->
        val original = chain.request()
        val builder =
          original
            .newBuilder()
            .header("apikey", SupabaseConfig.supabaseAnonKey)
            .header("Content-Type", "application/json")
        if (original.header("Authorization") == null && SupabaseConfig.supabaseAnonKey.isNotBlank()) {
          builder.header("Authorization", "Bearer ${SupabaseConfig.supabaseAnonKey}")
        }
        chain.proceed(builder.build())
      }
      .apply {
        if (BuildConfig.DEBUG) {
          addInterceptor(
            HttpLoggingInterceptor().apply {
              level = HttpLoggingInterceptor.Level.BASIC
              redactHeader("Authorization")
              redactHeader("apikey")
            }
          )
        }
      }
      .build()

  private val retrofit: Retrofit? by lazy {
    if (!SupabaseConfig.isConfigured) {
      null
    } else {
      val baseUrl = "${SupabaseConfig.supabaseUrl}/"
      Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
    }
  }

  val authService: SupabaseAuthService?
    get() = retrofit?.create(SupabaseAuthService::class.java)

  val restService: SupabaseRestService?
    get() = retrofit?.create(SupabaseRestService::class.java)
}
