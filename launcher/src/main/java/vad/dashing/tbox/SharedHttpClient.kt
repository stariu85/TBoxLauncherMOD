package vad.dashing.tbox

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Shared application-wide [OkHttpClient] instance so all HTTP clients
 * (OTA updater, TBM local API, etc.) share one connection and thread pool.
 * Callers requiring custom timeouts should derive via `SharedHttpClient.client.newBuilder()`.
 */
object SharedHttpClient {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
