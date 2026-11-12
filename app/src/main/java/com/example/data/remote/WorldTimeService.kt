package com.example.data.remote

import com.example.data.model.GlobalTimeStamp
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class TimeApiIoResponse(
  @Json(name = "year") val year: Int = 0,
  @Json(name = "month") val month: Int = 0,
  @Json(name = "day") val day: Int = 0,
  @Json(name = "hour") val hour: Int = 0,
  @Json(name = "minute") val minute: Int = 0,
  @Json(name = "seconds") val seconds: Int = 0,
  @Json(name = "dateTime") val dateTime: String? = null,
  @Json(name = "timeZone") val timeZone: String? = null,
)

@JsonClass(generateAdapter = true)
data class WorldTimeApiResponse(
  @Json(name = "unixtime") val unixtime: Long = 0L,
  @Json(name = "utc_datetime") val utcDatetime: String? = null,
)

interface WorldTimeApi {
  @GET("https://timeapi.io/api/time/current/zone?timeZone=UTC")
  suspend fun getTimeApiUtc(): TimeApiIoResponse

  @GET("https://worldtimeapi.org/api/timezone/Etc/UTC")
  suspend fun getWorldTimeUtc(): WorldTimeApiResponse
}

/**
 * Fetches authoritative network UTC time without relying on the device's local clock.
 * Uses multiple independent global time sources (timeapi.io, worldtimeapi.org, and HTTP Date headers).
 */
class WorldTimeService(
  private val okHttpClient: OkHttpClient = defaultClient(),
  private val api: WorldTimeApi = defaultApi(okHttpClient),
) {

  suspend fun fetchAuthoritativeGlobalTime(): Result<GlobalTimeStamp> = withContext(Dispatchers.IO) {
    // 1. Try timeapi.io UTC
    runCatching {
      val response = api.getTimeApiUtc()
      val epochMs = parseIsoUtcMillis(response.dateTime)
        ?: buildUtcMillis(
          response.year,
          response.month,
          response.day,
          response.hour,
          response.minute,
          response.seconds
        )
      require(epochMs > 1_700_000_000_000L) { "Invalid epoch from timeapi.io" }
      formatGlobalTimeStamp(epochMs, "timeapi.io (UTC)")
    }.onSuccess { return@withContext Result.success(it) }

    // 2. Try worldtimeapi.org UTC
    runCatching {
      val response = api.getWorldTimeUtc()
      val epochMs = if (response.unixtime > 0L) {
        response.unixtime * 1000L
      } else {
        parseIsoUtcMillis(response.utcDatetime) ?: 0L
      }
      require(epochMs > 1_700_000_000_000L) { "Invalid epoch from worldtimeapi.org" }
      formatGlobalTimeStamp(epochMs, "worldtimeapi.org (UTC)")
    }.onSuccess { return@withContext Result.success(it) }

    // 3. Fallback: authoritative HTTP Date header from global edge servers
    val edgeTargets = listOf(
      "https://www.google.com" to "Google Global Server (UTC)",
      "https://1.1.1.1" to "Cloudflare Edge (UTC)",
    )
    for ((url, label) in edgeTargets) {
      runCatching {
        val request = Request.Builder().url(url).head().build()
        okHttpClient.newCall(request).execute().use { response ->
          val date: Date? = response.headers.getDate("Date")
          requireNotNull(date) { "Missing HTTP Date header from $url" }
          val epochMs = date.time
          require(epochMs > 1_700_000_000_000L) { "Invalid HTTP Date epoch" }
          return@withContext Result.success(formatGlobalTimeStamp(epochMs, label))
        }
      }
    }

    Result.failure(
      IllegalStateException("تعذر الاتصال بخوادم الوقت العالمي. يرجى التحقق من اتصال الإنترنت.")
    )
  }

  companion object {
    private val BAGHDAD_TZ: TimeZone = TimeZone.getTimeZone("Asia/Baghdad")
    private val UTC_TZ: TimeZone = TimeZone.getTimeZone("UTC")

    fun formatGlobalTimeStamp(epochMs: Long, sourceName: String): GlobalTimeStamp {
      val date = Date(epochMs)

      val utcIsoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = UTC_TZ
      }
      val utcDisplayFmt = SimpleDateFormat("HH:mm:ss 'UTC'", Locale.US).apply {
        timeZone = UTC_TZ
      }
      val baghdadDateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = BAGHDAD_TZ
      }
      val baghdadTimeFmt = SimpleDateFormat("hh:mm:ss a", Locale("ar", "IQ")).apply {
        timeZone = BAGHDAD_TZ
      }

      return GlobalTimeStamp(
        epochMs = epochMs,
        utcIso = utcIsoFmt.format(date),
        workDateBaghdad = baghdadDateFmt.format(date),
        displayTimeBaghdad = baghdadTimeFmt.format(date),
        displayTimeUtc = utcDisplayFmt.format(date),
        sourceName = sourceName,
      )
    }

    private fun parseIsoUtcMillis(raw: String?): Long? {
      if (raw.isNullOrBlank()) return null
      // Trim fractional seconds beyond millis and normalize Z
      val normalized = raw
        .replace(Regex("\\.(\\d{3})\\d+"), ".$1")
        .let { if (it.endsWith("Z") || it.contains("+")) it else "${it}Z" }
      val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
      )
      for (pattern in patterns) {
        val parsed = runCatching {
          SimpleDateFormat(pattern, Locale.US).apply { timeZone = UTC_TZ }.parse(normalized)?.time
        }.getOrNull()
        if (parsed != null) return parsed
      }
      return null
    }

    private fun buildUtcMillis(
      year: Int,
      month: Int,
      day: Int,
      hour: Int,
      minute: Int,
      second: Int,
    ): Long {
      val cal = java.util.Calendar.getInstance(UTC_TZ).apply {
        set(year, month - 1, day, hour, minute, second)
        set(java.util.Calendar.MILLISECOND, 0)
      }
      return cal.timeInMillis
    }

    private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
      .connectTimeout(7, TimeUnit.SECONDS)
      .readTimeout(7, TimeUnit.SECONDS)
      .build()

    private fun defaultApi(client: OkHttpClient): WorldTimeApi {
      val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
      return Retrofit.Builder()
        .baseUrl("https://timeapi.io/")
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(WorldTimeApi::class.java)
    }
  }
}
