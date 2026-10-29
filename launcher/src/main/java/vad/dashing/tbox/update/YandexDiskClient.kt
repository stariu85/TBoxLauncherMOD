package vad.dashing.tbox.update

import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class YandexDiskClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .build(),
) {
    @Volatile
    private var mailRuWeblinkViewBaseUrl: String? = null

    fun fetchText(publicKey: String, path: String): String {
        val href = resolveDownloadHref(publicKey, path)
        val request = createGetRequest(href)
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Download failed: HTTP ${response.code}")
            }
            return response.body?.string().orEmpty()
        }
    }

    suspend fun downloadToFile(
        publicKey: String,
        path: String,
        destination: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> },
    ) {
        val href = resolveDownloadHref(publicKey, path)
        val request = createGetRequest(href)
        val coroutineContext = currentCoroutineContext()
        val call = client.newCall(request)
        val cancelHandle = coroutineContext.job.invokeOnCompletion { cause ->
            if (cause is CancellationException) {
                call.cancel()
            }
        }
        try {
            coroutineContext.ensureActive()
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Download failed: HTTP ${response.code}")
                }
                val body = response.body ?: throw IOException("Download body is empty")
                val totalBytes = body.contentLength().takeIf { it >= 0L }
                destination.parentFile?.mkdirs()
                val tempFile = File(destination.parentFile, "${destination.name}.part")
                var completed = false
                try {
                    tempFile.outputStream().use { output ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var downloaded = 0L
                            while (true) {
                                coroutineContext.ensureActive()
                                val read = input.read(buffer)
                                coroutineContext.ensureActive()
                                if (read <= 0) break
                                output.write(buffer, 0, read)
                                downloaded += read
                                onProgress(downloaded, totalBytes)
                            }
                        }
                    }
                    if (destination.exists()) {
                        destination.delete()
                    }
                    if (!tempFile.renameTo(destination)) {
                        throw IOException("Failed to finalize download file")
                    }
                    completed = true
                } finally {
                    if (!completed) {
                        tempFile.delete()
                    }
                }
            }
        } catch (error: IOException) {
            if (!coroutineContext.isActive) {
                throw CancellationException("Update download canceled", error)
            }
            throw error
        } finally {
            cancelHandle.dispose()
        }
    }

    fun fetchPublicResourceSize(publicKey: String, path: String): Long {
        if (publicKey.isBlank()) {
            throw IOException("Update source URL is not configured")
        }
        if (isMailRuUrl(publicKey)) {
            return fetchMailRuResourceSize(publicKey, path)
        }
        val encodedKey = URLEncoder.encode(publicKey, Charsets.UTF_8.name())
        val encodedPath = URLEncoder.encode(path, Charsets.UTF_8.name())
        val apiUrl =
            "https://cloud-api.yandex.net/v1/disk/public/resources" +
                "?public_key=$encodedKey&path=$encodedPath"
        val request = Request.Builder().url(apiUrl).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Yandex Disk metadata failed: HTTP ${response.code}")
            }
            val size = JSONObject(response.body?.string().orEmpty()).optLong("size", -1L)
            if (size <= 0L) {
                throw IOException("Yandex Disk metadata returned empty file size")
            }
            return size
        }
    }

    private fun resolveDownloadHref(publicKey: String, path: String): String {
        if (publicKey.isBlank()) {
            throw IOException("Update source URL is not configured")
        }
        if (isMailRuUrl(publicKey)) {
            return resolveMailRuDownloadHref(publicKey, path)
        }
        val encodedKey = URLEncoder.encode(publicKey, Charsets.UTF_8.name())
        val encodedPath = URLEncoder.encode(path, Charsets.UTF_8.name())
        val apiUrl =
            "https://cloud-api.yandex.net/v1/disk/public/resources/download" +
                "?public_key=$encodedKey&path=$encodedPath"
        val request = Request.Builder().url(apiUrl).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Yandex Disk API failed: HTTP ${response.code}")
            }
            val json = response.body?.string().orEmpty()
            val href = JSONObject(json).optString("href")
            if (href.isBlank()) {
                throw IOException("Yandex Disk API returned empty download link")
            }
            return href
        }
    }

    private fun isMailRuUrl(url: String): Boolean {
        return url.contains("cloud.mail.ru") || url.contains("mail.ru/public/")
    }

    private fun parseMailRuWeblink(sourceUrl: String): String {
        var cleaned = sourceUrl.trim()
        val publicPrefix = "cloud.mail.ru/public/"
        if (cleaned.contains(publicPrefix)) {
            cleaned = cleaned.substringAfter(publicPrefix)
        } else if (cleaned.startsWith("https://") || cleaned.startsWith("http://")) {
            cleaned = cleaned.substringAfter("://").substringAfter("/")
        }
        return cleaned.trim('/')
    }

    private fun getMailRuWeblinkViewBaseUrl(weblink: String): String {
        mailRuWeblinkViewBaseUrl?.let { return it }
        val apiUrl = "https://cloud.mail.ru/api/v2/dispatcher?weblink=$weblink"
        val request = Request.Builder()
            .url(apiUrl)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val bodyObj = json.optJSONObject("body") ?: return@runCatching null
                val weblinkViewArr = bodyObj.optJSONArray("weblink_view") ?: return@runCatching null
                if (weblinkViewArr.length() > 0) {
                    val url = weblinkViewArr.optJSONObject(0)?.optString("url")
                    if (!url.isNullOrBlank()) url else null
                } else null
            }
        }.getOrNull()?.also {
            mailRuWeblinkViewBaseUrl = it
        } ?: DEFAULT_MAIL_RU_WEBLINK_VIEW_URL
    }

    private fun resolveMailRuDownloadHref(sourceUrl: String, path: String): String {
        val weblink = parseMailRuWeblink(sourceUrl)
        val baseUrl = getMailRuWeblinkViewBaseUrl(weblink)
        val cleanPath = path.trim('/')
        val fullRelativePath = if (cleanPath.isBlank()) weblink else "$weblink/$cleanPath"
        return "${baseUrl.trimEnd('/')}/$fullRelativePath"
    }

    private fun fetchMailRuResourceSize(sourceUrl: String, path: String): Long {
        val weblink = parseMailRuWeblink(sourceUrl)
        val cleanPath = path.trim('/')
        val apiUrl = "https://cloud.mail.ru/api/v2/folder?weblink=$weblink"
        val request = Request.Builder()
            .url(apiUrl)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        runCatching {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string().orEmpty())
                    val body = json.optJSONObject("body")
                    val list = body?.optJSONArray("list")
                    if (list != null) {
                        val fileName = cleanPath.substringAfterLast('/')
                        for (i in 0 until list.length()) {
                            val item = list.optJSONObject(i) ?: continue
                            if (item.optString("name").equals(fileName, ignoreCase = true)) {
                                val size = item.optLong("size", -1L)
                                if (size > 0L) return size
                            }
                        }
                    }
                }
            }
        }
        val href = resolveMailRuDownloadHref(sourceUrl, path)
        val downloadReq = createGetRequest(href)
        return runCatching {
            client.newCall(downloadReq).execute().use { response ->
                val size = response.body?.contentLength() ?: -1L
                if (size > 0L) size else -1L
            }
        }.getOrDefault(-1L).takeIf { it > 0L } ?: throw IOException("Could not determine file size from Mail.ru Cloud")
    }

    private fun createGetRequest(url: String): Request {
        return Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private const val DEFAULT_MAIL_RU_WEBLINK_VIEW_URL =
            "https://cloclo62.cloud.mail.ru/weblink/view/"
    }
}
