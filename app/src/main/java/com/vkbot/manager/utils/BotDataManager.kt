package com.vkbot.manager.utils

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Модель данных бота.
 */
data class Bot(
    val id: Int,
    var name: String,
    var token: String,
    var isRunning: Boolean = false,
    var processedMessages: Long = 0,
    var answeredMessages: Long = 0,
    var startTime: Long = 0
)

/**
 * Единый менеджер управления данными ботов.
 * Решает проблему разрозненного хранения и ошибок доступа к файлам.
 */
object BotDataManager {
    private const val TAG = "BotDataManager"
    private const val BOTS_FILE_NAME = "bots.json"
    private const val PREFS_NAME = "vk_bot_settings"

    /**
     * Загружает список ботов, выполняя миграцию при необходимости.
     */
    fun loadBots(context: Context): List<Bot> {
        val list = mutableListOf<Bot>()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        try {
            // 1. Пытаемся прочитать из внутреннего хранилища (новый стандарт)
            val file = File(context.filesDir, BOTS_FILE_NAME)
            if (file.exists()) {
                val jsonString = file.readText(Charsets.UTF_8)
                if (jsonString.isNotEmpty()) {
                    val arr = JSONArray(jsonString)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val id = obj.optInt("id", i + 1)
                        val name = obj.optString("name", "")
                        val token = obj.optString("token", "")
                        
                        if (name.isNotEmpty() && token.isNotEmpty()) {
                            list.add(Bot(
                                id = id,
                                name = name,
                                token = token,
                                isRunning = prefs.getBoolean("bot_${id}_running", false),
                                processedMessages = prefs.getLong("bot_${id}_processed", 0),
                                answeredMessages = prefs.getLong("bot_${id}_answered", 0),
                                startTime = prefs.getLong("bot_${id}_start_time", 0)
                            ))
                        }
                    }
                }
            }
            
            // 2. Если во внутренней памяти пусто, пробуем миграцию из внешнего хранилища (старый путь)
            if (list.isEmpty()) {
                migrateFromExternal(context, list)
            }
            
            // 3. Последний шанс: восстановление из SharedPreferences
            if (list.isEmpty()) {
                restoreFromPrefs(context, list)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bots", e)
        }
        
        return list
    }

    /**
     * Сохраняет список ботов во внутреннюю память и SharedPreferences.
     */
    fun saveBots(context: Context, bots: List<Bot>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // 1. Сохраняем в SharedPreferences для быстрого доступа сервиса
        prefs.edit {
            val activeIds = bots.map { it.id }.joinToString(",")
            putString("active_bot_ids", activeIds)

            bots.forEach { bot ->
                putString("bot_${bot.id}_name", bot.name)
                putString("bot_${bot.id}_token", bot.token)
                putBoolean("bot_${bot.id}_running", bot.isRunning)
            }
            apply()
        }
        
        // 2. Сохраняем в JSON файл (внутренняя память)
        try {
            val file = File(context.filesDir, BOTS_FILE_NAME)
            val jsonArray = JSONArray()
            bots.forEach { bot ->
                jsonArray.put(JSONObject().apply {
                    put("id", bot.id)
                    put("name", bot.name)
                    put("token", bot.token)
                })
            }
            file.writeText(jsonArray.toString(4), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving bots to file", e)
        }
    }

    private fun migrateFromExternal(context: Context, list: MutableList<Bot>) {
        try {
            @Suppress("DEPRECATION")
            val oldDir = File(android.os.Environment.getExternalStorageDirectory(), "kirdev_base")
            val oldFile = File(oldDir, BOTS_FILE_NAME)
            
            if (oldFile.exists()) {
                Log.d(TAG, "Migrating from external storage...")
                val jsonString = oldFile.readText(Charsets.UTF_8)
                val arr = JSONArray(jsonString)
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val id = obj.optInt("id", i + 1)
                    val name = obj.optString("name", "")
                    val token = obj.optString("token", "")
                    
                    if (name.isNotEmpty() && token.isNotEmpty()) {
                        list.add(Bot(
                            id = id,
                            name = name,
                            token = token,
                            isRunning = prefs.getBoolean("bot_${id}_running", false)
                        ))
                    }
                }
                
                // Сразу сохраняем во внутреннюю память
                if (list.isNotEmpty()) {
                    saveBots(context, list)
                    Log.d(TAG, "Successfully migrated ${list.size} bots")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "External migration error", e)
        }
    }

    private fun restoreFromPrefs(context: Context, list: MutableList<Bot>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val activeIdsStr = prefs.getString("active_bot_ids", "") ?: ""
        
        val idList = if (activeIdsStr.isNotEmpty()) {
            activeIdsStr.split(",").mapNotNull { it.trim().toIntOrNull() }
        } else {
            (1..5).toList()
        }

        idList.forEach { id ->
            val name = prefs.getString("bot_${id}_name", null)
            val token = prefs.getString("bot_${id}_token", null)
            if (name != null && token != null) {
                list.add(Bot(
                    id = id,
                    name = name,
                    token = token,
                    isRunning = prefs.getBoolean("bot_${id}_running", false),
                    processedMessages = prefs.getLong("bot_${id}_processed", 0),
                    answeredMessages = prefs.getLong("bot_${id}_answered", 0)
                ))
            }
        }
        
        if (list.isNotEmpty()) {
            Log.d(TAG, "Restored ${list.size} bots from SharedPreferences")
            saveBots(context, list) // Закрепляем в файле
        }
    }
}
