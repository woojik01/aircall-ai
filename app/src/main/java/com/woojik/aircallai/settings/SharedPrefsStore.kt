package com.woojik.aircallai.settings

import android.content.Context

/** Android SharedPreferences-backed SettingsStore. */
class SharedPrefsStore(context: Context) : SettingsStore {
    private val prefs = context.getSharedPreferences("aircall_settings", Context.MODE_PRIVATE)

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
}
