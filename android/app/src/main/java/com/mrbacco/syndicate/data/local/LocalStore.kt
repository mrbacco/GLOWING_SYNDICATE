/*
 * File:     android/app/src/main/java/com/mrbacco/syndicate/data/local/LocalStore.kt
 * Author:   mrbacco04@gmail.com
 * GitHub:   https://github.com/mrbacco
 * Created:  2026-11-09
 * Updated:  2026-11-23
 * Project:  SYNDICATE
 * License:  Proprietary, all rights reserved (see LICENSE)
 */

package com.mrbacco.syndicate.data.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** The saved file exists but can't be read. It is left untouched, so nothing is lost. */
class UnreadableDataException(message: String, cause: Throwable) : IOException(message, cause)

/**
 * Keeps [AppData] in one JSON file. Changes are written to a temporary file first and then swapped
 * in, so a crash or a flat battery mid-save never leaves a half-written file behind.
 */
class LocalStore(private val file: File) {

    private val mutex = Mutex()
    private var cache: AppData? = null

    suspend fun read(): AppData = mutex.withLock { load() }

    /** Applies [change] and saves the result. Anything [change] throws cancels the change. */
    suspend fun update(change: (AppData) -> AppData): AppData = mutex.withLock {
        val updated = change(load())
        withContext(Dispatchers.IO) { save(updated) }
        cache = updated
        updated
    }

    private suspend fun load(): AppData = cache ?: withContext(Dispatchers.IO) {
        val data = if (file.exists()) decode(file.readText()) else AppData()
        data.also { cache = it }
    }

    private fun save(data: AppData) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(json.encodeToString(AppData.serializer(), data))
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    companion object {
        const val FILE_NAME = "syndicate-data.json"

        val json = Json {
            ignoreUnknownKeys = true // a newer app version may have added fields
            encodeDefaults = true
            explicitNulls = false
        }

        fun decode(text: String): AppData = try {
            json.decodeFromString(AppData.serializer(), text)
        } catch (e: SerializationException) {
            throw UnreadableDataException("The saved data couldn't be read.", e)
        } catch (e: IllegalArgumentException) {
            throw UnreadableDataException("The saved data couldn't be read.", e)
        }
    }
}
