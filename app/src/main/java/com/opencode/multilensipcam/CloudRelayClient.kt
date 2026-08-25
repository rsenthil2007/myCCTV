package com.opencode.multilensipcam

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Low-FPS outbound publisher for the Interserver cloud relay.
 * Posts JPEG frames and polls queued dashboard commands.
 */
class CloudRelayClient(
    context: Context,
    private val onCommand: (Map<String, String>) -> Unit,
    private val onStatus: (String) -> Unit
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val enabled = AtomicBoolean(false)
    private val streaming = AtomicBoolean(false)
    private val lastPostElapsedMs = AtomicLong(0L)
    private val latestJpeg = AtomicReference<ByteArray?>(null)
    private val deviceName = AtomicReference(android.os.Build.MODEL)

    @Volatile
    var relayBaseUrl: String = prefs.getString(KEY_BASE, "") ?: ""
        private set

    @Volatile
    var relayToken: String = prefs.getString(KEY_TOKEN, "") ?: ""
        private set

    @Volatile
    var deviceId: String = prefs.getString(KEY_DEVICE_ID, null) ?: newDeviceId().also {
        prefs.edit().putString(KEY_DEVICE_ID, it).apply()
    }
        private set

    private val commandPollRunnable = object : Runnable {
        override fun run() {
            if (!enabled.get()) return
            executor.execute { pollCommands() }
            mainHandler.postDelayed(this, COMMAND_POLL_MS)
        }
    }

    private val publishRunnable = object : Runnable {
        override fun run() {
            if (!enabled.get()) return
            executor.execute { publishLatestFrame() }
            mainHandler.postDelayed(this, MIN_FRAME_INTERVAL_MS)
        }
    }

    fun configure(baseUrl: String, token: String, customDeviceId: String? = null) {
        relayBaseUrl = normalizeBase(baseUrl)
        relayToken = token.trim()
        if (!customDeviceId.isNullOrBlank()) {
            deviceId = customDeviceId.trim()
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }
        prefs.edit()
            .putString(KEY_BASE, relayBaseUrl)
            .putString(KEY_TOKEN, relayToken)
            .apply()
    }

    fun setDeviceName(name: String) {
        deviceName.set(name.ifBlank { android.os.Build.MODEL })
    }

    fun setEnabled(value: Boolean) {
        if (value == enabled.get()) return
        enabled.set(value)
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        mainHandler.removeCallbacks(commandPollRunnable)
        mainHandler.removeCallbacks(publishRunnable)
        if (value) {
            if (relayBaseUrl.isBlank() || relayToken.isBlank()) {
                onStatus("Cloud relay needs URL and token")
                enabled.set(false)
                prefs.edit().putBoolean(KEY_ENABLED, false).apply()
                return
            }
            executor.execute { register() }
            mainHandler.post(commandPollRunnable)
            mainHandler.post(publishRunnable)
            onStatus("Cloud relay on · id $deviceId")
        } else {
            onStatus("Cloud relay off")
        }
    }

    fun isEnabled(): Boolean = enabled.get() || prefs.getBoolean(KEY_ENABLED, false)

    fun restoreOnStart() {
        if (prefs.getBoolean(KEY_ENABLED, false) && relayBaseUrl.isNotBlank() && relayToken.isNotBlank()) {
            setEnabled(true)
        }
    }

    fun setStreaming(value: Boolean) {
        streaming.set(value)
    }

    fun onFrame(frame: MjpegFrame) {
        if (!enabled.get()) return
        latestJpeg.set(frame.bytes)
    }

    fun shutdown() {
        enabled.set(false)
        mainHandler.removeCallbacks(commandPollRunnable)
        mainHandler.removeCallbacks(publishRunnable)
        executor.shutdownNow()
    }

    private fun register() {
        try {
            val payload = JSONObject()
                .put("deviceId", deviceId)
                .put("name", deviceName.get())
                .toString()
            postJson("/api/register", payload)
            mainHandler.post { onStatus("Cloud relay registered · $deviceId") }
        } catch (error: Exception) {
            Log.w(TAG, "register failed", error)
            mainHandler.post { onStatus("Cloud relay register failed: ${error.message}") }
        }
    }

    private fun publishLatestFrame() {
        val jpeg = latestJpeg.get() ?: return
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastPostElapsedMs.get() < MIN_FRAME_INTERVAL_MS) return
        lastPostElapsedMs.set(now)
        try {
            val path = "/api/devices/${enc(deviceId)}/frame?streaming=${if (streaming.get()) 1 else 0}"
            val connection = open(path, "POST")
            connection.setRequestProperty("Content-Type", "image/jpeg")
            connection.setRequestProperty("X-Device-Name", deviceName.get())
            connection.doOutput = true
            connection.outputStream.use { it.write(jpeg) }
            val code = connection.responseCode
            connection.disconnect()
            if (code !in 200..299) {
                mainHandler.post { onStatus("Cloud relay frame HTTP $code") }
            }
        } catch (error: Exception) {
            Log.w(TAG, "frame post failed", error)
        }
    }

    private fun pollCommands() {
        try {
            val connection = open("/api/devices/${enc(deviceId)}/commands", "GET")
            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            connection.disconnect()
            if (code !in 200..299) return
            val commands = JSONObject(text).optJSONArray("commands") ?: JSONArray()
            for (index in 0 until commands.length()) {
                val paramsJson = commands.getJSONObject(index).optJSONObject("params") ?: continue
                val map = mutableMapOf<String, String>()
                paramsJson.keys().forEach { key -> map[key] = paramsJson.optString(key) }
                if (map.isNotEmpty()) {
                    mainHandler.post { onCommand(map) }
                }
            }
        } catch (error: Exception) {
            Log.w(TAG, "command poll failed", error)
        }
    }

    private fun postJson(path: String, json: String) {
        val connection = open(path, "POST")
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.doOutput = true
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(json) }
        val code = connection.responseCode
        connection.disconnect()
        if (code !in 200..299) {
            throw IllegalStateException("HTTP $code")
        }
    }

    private fun open(path: String, method: String): HttpURLConnection {
        val base = relayBaseUrl.trimEnd('/')
        val connection = (URL(base + path).openConnection() as HttpURLConnection)
        connection.connectTimeout = 8_000
        connection.readTimeout = 12_000
        connection.requestMethod = method
        connection.setRequestProperty("X-Relay-Token", relayToken)
        connection.setRequestProperty("X-Device-Id", deviceId)
        connection.useCaches = false
        return connection
    }

    companion object {
        private const val TAG = "CloudRelayClient"
        private const val PREFS = "mycctv_cloud_relay"
        private const val KEY_BASE = "base"
        private const val KEY_TOKEN = "token"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_ENABLED = "enabled"
        private const val MIN_FRAME_INTERVAL_MS = 800L
        private const val COMMAND_POLL_MS = 2_000L

        private fun normalizeBase(value: String): String {
            var v = value.trim()
            if (v.isEmpty()) return ""
            if (!v.startsWith("http://") && !v.startsWith("https://")) {
                v = "https://$v"
            }
            return v.trimEnd('/')
        }

        private fun newDeviceId(): String {
            val model = android.os.Build.MODEL.replace(Regex("[^A-Za-z0-9]"), "").take(8).ifBlank { "phone" }
            return "cam-$model-${UUID.randomUUID().toString().take(8)}"
        }

        private fun enc(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")
    }
}
