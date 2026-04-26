package com.vkbot.manager.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Менеджер для сохранения и загрузки глобальных настроек бота.
 */
object SettingsManager {

    private const val PREFS_NAME = "vkbot_global_settings"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var isTypingEnabled: Boolean
        get() = prefs.getBoolean("pref_typing_enabled", true)
        set(value) = prefs.edit().putBoolean("pref_typing_enabled", value).apply()

    var isAntiSpamEnabled: Boolean
        get() = prefs.getBoolean("pref_antispam_enabled", true)
        set(value) = prefs.edit().putBoolean("pref_antispam_enabled", value).apply()

    var isAutoBanEnabled: Boolean
        get() = prefs.getBoolean("pref_autoban_enabled", true)
        set(value) = prefs.edit().putBoolean("pref_autoban_enabled", value).apply()

    var isRandomFallbackEnabled: Boolean
        get() = prefs.getBoolean("pref_random_fallback_enabled", true)
        set(value) = prefs.edit().putBoolean("pref_random_fallback_enabled", value).apply()

    var isFallbackSilenceEnabled: Boolean
        get() = prefs.getBoolean("pref_fallback_silence_enabled", false)
        set(value) = prefs.edit().putBoolean("pref_fallback_silence_enabled", value).apply()

    var isMediaResponsesEnabled: Boolean
        get() = prefs.getBoolean("pref_media_responses_enabled", true)
        set(value) = prefs.edit().putBoolean("pref_media_responses_enabled", value).apply()
        
    var isMarkAsReadEnabled: Boolean
        get() = prefs.getBoolean("pref_mark_as_read_enabled", false)
        set(value) = prefs.edit().putBoolean("pref_mark_as_read_enabled", value).apply()

    var isChatsEnabled: Boolean
        get() = prefs.getBoolean("pref_chats_enabled", false)
        set(value) = prefs.edit().putBoolean("pref_chats_enabled", value).apply()

    var chatPrefix: String
        get() = prefs.getString("pref_chat_prefix", "Бот,") ?: "Бот,"
        set(value) = prefs.edit().putString("pref_chat_prefix", value).apply()
        
    var spamLimit: Int
        get() = prefs.getInt("pref_spam_limit", 10)
        set(value) = prefs.edit().putInt("pref_spam_limit", value).apply()
}
