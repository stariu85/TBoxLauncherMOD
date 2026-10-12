package vad.dashing.tbox.tbm

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class TbmHealthResponse(
    val ok: Boolean,
    val apiVersion: Int,
    val catalogVersion: Int,
    val serverEnabled: Boolean,
    val pairingActive: Boolean,
    val appVersion: String,
)

data class TbmPairResponse(
    val status: String, // "approved" | "pending" | "rejected"
    val requestId: String? = null,
    val accessToken: String? = null,
    val clientId: String? = null,
    val expiresAt: Long? = null,
)

data class TbmSignalValue(
    val id: String,
    val source: String? = null,
    val valueType: String? = null,
    val value: Any? = null,
    val available: Boolean = false,
)

data class TbmActionItem(
    val type: String = "can_command",
    val bus: String? = null,
    val propertyId: Int? = null,
    val operation: String? = null,
    val value: Any? = null,
)

data class TbmActionResult(
    val success: Boolean,
    val message: String = "",
)

data class TbmAutomationItem(
    val id: String,
    val name: String,
    val enabled: Boolean,
)

data class TbmAutomationRunResponse(
    val success: Boolean,
    val message: String = "",
)

class TbmApiClient(
    private val okHttpClient: OkHttpClient = defaultOkHttpClient(),
    private val host: String = "127.0.0.1",
    private val portProvider: () -> Int = { 8765 },
) {
    companion object {
        private fun defaultOkHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .build()
        }
    }

    private val baseUrl: String
        get() = "http://$host:${portProvider()}/v1"

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun checkHealth(): Result<TbmHealthResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$baseUrl/health")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}: ${response.message}")
                }
                val body = response.body?.string()?.trim() ?: throw IOException("Empty response body")
                val json = if (body.startsWith("{")) JSONObject(body) else JSONObject()
                TbmHealthResponse(
                    ok = json.optBoolean("ok", response.isSuccessful),
                    apiVersion = json.optInt("apiVersion", 1),
                    catalogVersion = json.optInt("catalogVersion", 0),
                    serverEnabled = json.optBoolean("serverEnabled", true),
                    pairingActive = json.optBoolean("pairingActive", false),
                    appVersion = json.optString("appVersion", ""),
                )
            }
        }
    }

    private val pairingOkHttpClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    suspend fun requestPairing(
        clientId: String,
        clientName: String,
        clientKind: String = "launcher",
        requestId: String? = null,
    ): Result<TbmPairResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val jsonBody = JSONObject().apply {
                put("clientId", clientId)
                put("clientName", clientName)
                put("clientKind", clientKind)
                requestId?.let { put("requestId", it) }
            }.toString()

            val request = Request.Builder()
                .url("$baseUrl/pair/request")
                .post(jsonBody.toRequestBody(jsonMediaType))
                .build()

            pairingOkHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string()?.trim() ?: throw IOException("Empty response body")
                val json = if (body.startsWith("{")) JSONObject(body) else JSONObject()
                TbmPairResponse(
                    status = json.optString("status", if (response.isSuccessful) "approved" else "pending"),
                    requestId = json.optString("requestId").takeIf { it.isNotEmpty() },
                    accessToken = json.optString("accessToken").takeIf { it.isNotEmpty() },
                    clientId = json.optString("clientId").takeIf { it.isNotEmpty() },
                    expiresAt = if (json.has("expiresAt") && !json.isNull("expiresAt")) json.optLong("expiresAt") else null,
                )
            }
        }
    }

    suspend fun getSignals(
        ids: List<String>,
        source: String? = null,
        token: String,
    ): Result<List<TbmSignalValue>> = withContext(Dispatchers.IO) {
        runCatching {
            val idsParam = ids.joinToString(",")
            var url = "$baseUrl/signals?ids=$idsParam"
            if (!source.isNullOrEmpty()) {
                url += "&source=$source"
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}: ${response.message}")
                }
                val body = response.body?.string()?.trim() ?: throw IOException("Empty response body")
                val signalsArray: JSONArray = if (body.startsWith("[")) {
                    JSONArray(body)
                } else {
                    val json = JSONObject(body)
                    json.optJSONArray("signals") ?: json.optJSONArray("items") ?: JSONArray()
                }

                val list = mutableListOf<TbmSignalValue>()
                for (i in 0 until signalsArray.length()) {
                    val item = signalsArray.optJSONObject(i) ?: continue
                    val value = if (item.isNull("value")) null else item.opt("value")
                    list.add(
                        TbmSignalValue(
                            id = item.optString("id"),
                            source = item.optString("source").takeIf { it.isNotEmpty() },
                            valueType = item.optString("valueType").takeIf { it.isNotEmpty() },
                            value = value,
                            available = item.optBoolean("available", true),
                        ),
                    )
                }
                list
            }
        }
    }

    suspend fun invokeActions(
        actions: List<TbmActionItem>,
        token: String,
    ): Result<List<TbmActionResult>> = withContext(Dispatchers.IO) {
        runCatching {
            val actionsArray = JSONArray()
            actions.forEach { (type, bus, propertyId, operation, value) ->
                val actObj = JSONObject().apply {
                    put("type", type)
                    bus?.let { put("bus", it) }
                    propertyId?.let { put("propertyId", it) }
                    operation?.let { put("operation", it) }
                    value?.let { put("value", it) }
                }
                actionsArray.put(actObj)
            }
            val root = JSONObject().apply { put("actions", actionsArray) }

            val request = Request.Builder()
                .url("$baseUrl/actions/invoke")
                .addHeader("Authorization", "Bearer $token")
                .post(root.toString().toRequestBody(jsonMediaType))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}: ${response.message}")
                }
                val body = response.body?.string()?.trim() ?: ""
                val resultsArray: JSONArray = if (body.startsWith("[")) {
                    JSONArray(body)
                } else if (body.startsWith("{")) {
                    val json = JSONObject(body)
                    json.optJSONArray("results") ?: json.optJSONArray("items") ?: JSONArray()
                } else {
                    JSONArray()
                }

                val list = mutableListOf<TbmActionResult>()
                for (i in 0 until resultsArray.length()) {
                    val item = resultsArray.optJSONObject(i)
                    if (item != null) {
                        list.add(
                            TbmActionResult(
                                success = item.optBoolean("success", true),
                                message = item.optString("message", ""),
                            ),
                        )
                    } else {
                        list.add(TbmActionResult(success = response.isSuccessful, message = "OK"))
                    }
                }
                if (list.isEmpty()) {
                    list.add(TbmActionResult(success = response.isSuccessful, message = "OK"))
                }
                list
            }
        }
    }

    suspend fun getAutomations(token: String): Result<List<TbmAutomationItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$baseUrl/automations")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}: ${response.message}")
                }
                val body = response.body?.string()?.trim() ?: throw IOException("Empty response body")
                val autoArray: JSONArray = if (body.startsWith("[")) {
                    JSONArray(body)
                } else {
                    val json = JSONObject(body)
                    json.optJSONArray("automations")
                        ?: json.optJSONArray("items")
                        ?: json.optJSONArray("data")
                        ?: json.optJSONArray("rules")
                        ?: JSONArray()
                }

                val list = mutableListOf<TbmAutomationItem>()
                for (i in 0 until autoArray.length()) {
                    val item = autoArray.optJSONObject(i) ?: continue
                    val id = item.optString("id").ifEmpty { item.optString("uuid").ifEmpty { item.optString("key") } }
                    val name = item.optString("name").ifEmpty { item.optString("title").ifEmpty { item.optString("label", "Automation ${i + 1}") } }
                    val enabled = if (item.has("enabled")) item.optBoolean("enabled", true) else true
                    if (id.isNotEmpty()) {
                        list.add(
                            TbmAutomationItem(
                                id = id,
                                name = name,
                                enabled = enabled,
                            ),
                        )
                    }
                }
                list
            }
        }
    }

    suspend fun runAutomation(automationId: String, token: String): Result<TbmAutomationRunResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$baseUrl/automations/$automationId/run")
                .addHeader("Authorization", "Bearer $token")
                .post("{}".toRequestBody(jsonMediaType))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}: ${response.message}")
                }
                val body = response.body?.string()?.trim() ?: ""
                if (body.isEmpty()) {
                    return@use TbmAutomationRunResponse(success = true, message = "OK")
                }
                val json = if (body.startsWith("{")) JSONObject(body) else JSONObject()
                TbmAutomationRunResponse(
                    success = if (json.has("success")) json.optBoolean("success", true) else response.isSuccessful,
                    message = json.optString("message", ""),
                )
            }
        }
    }
}
