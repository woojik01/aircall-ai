package com.woojik.aircallai.settings

import android.content.Context

/** Android SharedPreferences-backed SettingsStore. */
class SharedPrefsStore(context: Context) : SettingsStore {
    private val prefs = context.getSharedPreferences("aircall_settings", Context.MODE_PRIVATE)

    override fun getString(key: String): String? = when (val value = prefs.all[key]) {
        is String -> value
        is Boolean, is Number -> value.toString()
        else -> null
    }

    override fun putString(key: String, value: String) {
        // Complete the disk write before reporting a saved setting. APK replacement
        // terminates the old process; an in-memory value alone is not a save.
        check(prefs.edit().putString(key, value).commit()) { "설정을 저장하지 못했습니다. 저장 공간을 확인해 주세요." }
    }
}
