package com.midea.acremote.ir

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Phát tín hiệu IR qua IR blaster WiFi bằng HTTP.
 *
 * Body là một template chứa placeholder được thay thế khi gửi:
 *  - `{carrier}`      tần số carrier, vd `38000`
 *  - `{timings}`      mảng JSON các xung µs, vd `[4480,4480,560,...]`
 *  - `{timings_csv}`  các xung cách nhau bằng dấu phẩy
 *  - `{repeat}`       số lần lặp (mặc định 1)
 *
 * Body mặc định hợp lệ với phần lớn firmware mở (`{"carrier":{carrier},"timings":[{timings}],"repeat":{repeat}}`).
 */
class HttpIrSender(
    private val config: HttpConfig,
    private val client: OkHttpClient = defaultClient
) : IrSender {

    data class HttpConfig(
        val url: String,
        val bodyTemplate: String = DEFAULT_BODY_TEMPLATE,
        val authHeader: String = ""
    ) {
        val isConfigured: Boolean get() = url.startsWith("http://") || url.startsWith("https://")
    }

    override val id: String = IrChannel.WIFI_IR.id
    override val label: String = IrChannel.WIFI_IR.label
    override var lastError: String? = null
        private set

    override suspend fun isAvailable(): Boolean = config.isConfigured

    override suspend fun send(signal: IrSignal) {
        if (!config.isConfigured) {
            lastError = "Chưa cấu hình địa chỉ IR blaster WiFi"
            throw IllegalStateException(lastError)
        }
        val timings = signal.pattern.joinToString(",")
        val timingsJson = signal.pattern.joinToString(",")
        val body = config.bodyTemplate
            .replace("{carrier}", signal.carrierHz.toString())
            .replace("{timings}", timingsJson)
            .replace("{timings_csv}", timings)
            .replace("{repeat}", "1")

        val builder = Request.Builder()
            .url(config.url)
            .post(body.toRequestBody("application/json".toMediaType()))
        if (config.authHeader.isNotBlank()) {
            builder.header("Authorization", config.authHeader)
        }

        try {
            withContext(Dispatchers.IO) {
                client.newCall(builder.build()).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        lastError = "Server trả về HTTP ${resp.code}"
                        throw IllegalStateException(lastError)
                    }
                }
            }
            lastError = null
        } catch (t: Throwable) {
            if (lastError == null) lastError = t.message ?: "Gửi HTTP thất bại"
            throw t
        }
    }

    companion object {
        const val DEFAULT_BODY_TEMPLATE =
            """{"carrier":{carrier},"timings":[{timings}],"repeat":{repeat}}"""

        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .writeTimeout(6, TimeUnit.SECONDS)
            .build()
    }
}
