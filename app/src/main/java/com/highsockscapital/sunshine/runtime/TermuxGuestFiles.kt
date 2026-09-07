package com.highsockscapital.sunshine.runtime

import android.content.Context
import android.util.Base64
import com.highsockscapital.sunshine.termux.TermuxBashTool
import com.highsockscapital.sunshine.termux.TermuxContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * File helpers for the Termux home directory. The Sunshine app sandbox cannot
 * touch Termux storage directly, so every operation is dispatched as a managed
 * bash command through [TermuxBashTool].
 */
class TermuxGuestFiles(
    context: Context,
    private val bashTool: TermuxBashTool,
) {
    private val appContext = context.applicationContext

    private val home = TermuxContract.HomeDirectory

    suspend fun execute(command: String, workingDirectory: String = home): JSONObject =
        withContext(Dispatchers.IO) {
            JSONObject(bashTool.executeCommand(command, workingDirectory))
        }

    suspend fun ensureDirectory(path: String) {
        val result = execute("mkdir -p ${shellQuote(path)}")
        require(result.optBoolean("ok")) {
            failureDetail(result, "Couldn't create directory: $path")
        }
    }

    suspend fun exists(path: String): Boolean {
        val result = execute("test -e ${shellQuote(path)} && echo YES || echo NO")
        return result.optBoolean("ok") && result.optString("stdout").trim() == "YES"
    }

    /** Writes bytes to an absolute Termux path using chunked base64 appends. */
    suspend fun writeFileBytes(path: String, bytes: ByteArray) {
        ensureDirectory(path.substringBeforeLast('/'))
        val temp = "$path.b64.part"
        execute("rm -f ${shellQuote(temp)} ${shellQuote(path)}")
        try {
            var offset = 0
            while (offset < bytes.size) {
                val end = minOf(offset + WriteChunkBytes, bytes.size)
                val chunk = Base64.encodeToString(
                    bytes.copyOfRange(offset, end),
                    Base64.NO_WRAP,
                )
                val result = execute("printf '%s' ${shellQuote(chunk)} >> ${shellQuote(temp)}")
                require(result.optBoolean("ok")) {
                    failureDetail(result, "Couldn't write file: $path")
                }
                offset = end
            }
            val decode = execute("base64 -d ${shellQuote(temp)} > ${shellQuote(path)}")
            require(decode.optBoolean("ok")) {
                failureDetail(decode, "Couldn't write file: $path")
            }
        } finally {
            execute("rm -f ${shellQuote(temp)}")
        }
    }

    suspend fun readFileBytes(path: String, byteLimit: Long = 32L * 1024 * 1024): ByteArray {
        // Fused into a single RUN_COMMAND: size check + base64 payload come back together,
        // saving one IPC round-trip per read versus wc-then-base64.
        val combined = execute(
            "size=$(wc -c < ${shellQuote(path)} | tr -d '[:space:]'); " +
                "echo \"SIZE:${'$'}size\"; " +
                "base64 < ${shellQuote(path)} | tr -d '\\n'; echo"
        )
        require(combined.optBoolean("ok")) {
            failureDetail(combined, "Couldn't read file: $path")
        }
        val stdout = combined.optString("stdout")
        val firstNewline = stdout.indexOf('\n')
        val sizeLine = if (firstNewline < 0) stdout else stdout.substring(0, firstNewline)
        val size = sizeLine.substringAfter("SIZE:", "").trim().toLongOrNull()
            ?: error("Couldn't read file size: $path")
        require(size <= byteLimit) { "File is too large: $path" }
        val payload = if (firstNewline < 0) "" else stdout.substring(firstNewline + 1).trim()
        if (payload.isBlank()) {
            if (size == 0L) return ByteArray(0)
            error("Couldn't read file: $path")
        }
        return Base64.decode(payload.replace("\\s".toRegex(), ""), Base64.DEFAULT)
    }

    /** Copies an APK asset into the Termux home. */
    suspend fun installAsset(assetPath: String, destinationPath: String) {
        val bytes = withContext(Dispatchers.IO) {
            appContext.assets.open(assetPath).use { it.readBytes() }
        }
        writeFileBytes(destinationPath, bytes)
    }

    suspend fun deleteRecursively(path: String) {
        execute("rm -rf ${shellQuote(path)}")
    }

    companion object {
        // Keep each RUN_COMMAND intent small: large extras get rejected by
        // startService on some devices (e.g. Samsung), failing the whole write.
        private const val WriteChunkBytes = 24 * 1024

        /** Prefers stderr, but dispatch-level failures report errmsg/hint instead. */
        private fun failureDetail(result: JSONObject, fallback: String): String =
            listOf(
                result.optString("stderr"),
                result.optString("errmsg"),
                result.optString("hint"),
            ).map { it.trim() }
                .filter { it.isNotBlank() }
                .joinToString(separator = " ")
                .ifBlank { fallback }

        fun shellQuote(value: String): String =
            "'" + value.replace("'", "'\"'\"'") + "'"
    }
}
