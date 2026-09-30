package com.woojik.aircallai.core.storage

import android.content.Context
import java.io.File

/**
 * PRD-02 storage layout inside the app-private internal storage:
 * app-private/{config,credentials,conversations,cache,models}
 */
object AppStorage {
    fun configDir(context: Context): File = dir(context, "config")
    fun credentialsDir(context: Context): File = dir(context, "credentials")
    fun conversationsDir(context: Context): File = dir(context, "conversations")
    fun cacheDir(context: Context): File = dir(context, "cache")
    fun modelsDir(context: Context): File = dir(context, "models")

    private fun dir(context: Context, name: String): File =
        File(context.filesDir, name).apply { mkdirs() }
}
