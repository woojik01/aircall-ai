package com.woojik.aircallai.backup

import com.woojik.aircallai.AppGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppDataTransfer {
    suspend fun capture(graph: AppGraph): BackupSnapshot {
        check(graph.chatRooms.ready.value)
        val settings = graph.settings
        return BackupSnapshot(graph.chatRooms.rooms.value.toList(), graph.notesStore.all(),
            graph.credentials.load("personal_memory")?.decodeToString().orEmpty(), mapOf(
                "theme" to settings.themeMode(), "aiMode" to settings.aiProviderMode(),
                "endpoint" to settings.cloudBaseUrl(), "model" to settings.cloudModel(),
                "gpu" to settings.localUseGpu().toString(), "streaming" to settings.cloudStreaming().toString(),
                "nativeTools" to settings.nativeCloudTools().toString()))
    }
    suspend fun import(graph: AppGraph, snapshot: BackupSnapshot, restorePreferences: Boolean): Int {
        // Full validation completes before any writes. Existing data is merged, never erased.
        val validated = withContext(Dispatchers.IO) { BackupSnapshot.decode(snapshot.encode()) }
        graph.notesStore.mergeImported(validated.notes)
        val count = graph.chatRooms.importRooms(validated.rooms)
        if (graph.credentials.load("personal_memory") == null && validated.memory.isNotBlank())
            graph.credentials.save("personal_memory", validated.memory.toByteArray(Charsets.UTF_8))
        if (restorePreferences) {
            val settings = graph.settings
            val prefs = validated.preferences
            prefs["theme"]?.let { settings.setThemeMode(it) }
            prefs["aiMode"]?.let { settings.setAiProviderMode(it) }
            prefs["endpoint"]?.let { settings.setCloudBaseUrl(it) }
            prefs["model"]?.let { settings.setCloudModel(it) }
            prefs["gpu"]?.let { settings.setLocalUseGpu(it == "true") }
            prefs["streaming"]?.let { settings.setCloudStreaming(it == "true") }
            prefs["nativeTools"]?.let { settings.setNativeCloudTools(it == "true") }
        }
        return count
    }
}
