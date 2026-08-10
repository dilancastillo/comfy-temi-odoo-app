// conserva en archivos diarios los eventos relevantes de las pruebas de campo
package com.example.comfyapp.logging

import android.content.Context
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

object PersistentLog {

    private const val LOG_DIRECTORY = "weekly-test-logs"
    private const val RETENTION_DAYS = 10

    // cambiar a false al finalizar las pruebas para dejar de persistir las frases de voz
    private const val INCLUDE_AGENT_TRACE = true

    private val includedTags = setOf(
        "TemiController",
        "ProductsUserActivity",
        "ProductListTiming",
        "OdooTiming",
        "AgentSpeech",
        "AgentAiClient",
        "CoverActivity"
    )
    private val writer = Executors.newSingleThreadExecutor()
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val fileDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    @Volatile
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
        writer.execute(::removeExpiredFiles)
    }

    fun d(tag: String, message: String): Int = write(android.util.Log.DEBUG, tag, message)

    fun i(tag: String, message: String): Int = write(android.util.Log.INFO, tag, message)

    fun w(tag: String, message: String): Int = write(android.util.Log.WARN, tag, message)

    fun w(tag: String, message: String, throwable: Throwable): Int =
        write(android.util.Log.WARN, tag, message, throwable)

    fun e(tag: String, message: String): Int = write(android.util.Log.ERROR, tag, message)

    fun e(tag: String, message: String, throwable: Throwable): Int =
        write(android.util.Log.ERROR, tag, message, throwable)

    private fun write(priority: Int, tag: String, message: String, throwable: Throwable? = null): Int {
        val result = if (throwable == null) {
            android.util.Log.println(priority, tag, message)
        } else {
            android.util.Log.println(priority, tag, "$message\n${android.util.Log.getStackTraceString(throwable)}")
        }

        if (shouldPersist(tag)) {
            val line = buildString {
                append(formatTimestamp())
                append(' ')
                append(priorityName(priority))
                append(' ')
                append(tag)
                append("  ")
                append(message)
                if (throwable != null) {
                    append('\n')
                    append(android.util.Log.getStackTraceString(throwable))
                }
                append('\n')
            }
            writer.execute { appendToDailyFile(line) }
        }
        return result
    }

    private fun shouldPersist(tag: String): Boolean =
        tag in includedTags || (INCLUDE_AGENT_TRACE && tag == "AgentTrace")

    private fun formatTimestamp(): String = synchronized(timestampFormat) {
        timestampFormat.format(Date())
    }

    private fun appendToDailyFile(line: String) {
        val context = appContext ?: return
        runCatching {
            val directory = File(context.filesDir, LOG_DIRECTORY).apply { mkdirs() }
            val file = File(directory, "comfy-${fileDateFormat.format(Date())}.txt")
            FileWriter(file, true).use { it.append(line) }
        }
    }

    private fun removeExpiredFiles() {
        val context = appContext ?: return
        val directory = File(context.filesDir, LOG_DIRECTORY)
        val cutoff = System.currentTimeMillis() - RETENTION_DAYS * 24L * 60L * 60L * 1000L
        directory.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < cutoff) file.delete()
        }
    }

    private fun priorityName(priority: Int): String = when (priority) {
        android.util.Log.DEBUG -> "D"
        android.util.Log.INFO -> "I"
        android.util.Log.WARN -> "W"
        android.util.Log.ERROR -> "E"
        else -> priority.toString()
    }
}
