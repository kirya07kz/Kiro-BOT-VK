package com.vkbot.manager

import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.vkbot.manager.botbrain.BotBrain
import com.vkbot.manager.botbrain.BotMessage
import com.vkbot.manager.utils.BotNotificationHelper
import com.vkbot.manager.utils.MediaResponses
import com.vkbot.manager.utils.BotDataManager
import com.vkbot.manager.utils.BlacklistManager
import com.vkbot.manager.utils.SettingsManager
import com.vkbot.manager.utils.NetworkHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import androidx.core.content.ContextCompat
import androidx.core.content.edit

/**
 * Фоновый сервис управления ботами.
 * Рефакторинг v2.1.0: оптимизация уведомлений, чистка кода и стабильность Multi-bot.
 */
class BotService : Service() {
    
    companion object {
        const val ACTION_START = "START_BOT"
        const val ACTION_STOP = "STOP_BOT"
        const val ACTION_RELOAD = "RELOAD_DATABASE"
        const val ACTION_NOTIFICATION_DISMISSED = "NOTIFICATION_DISMISSED"
        const val NOTIFICATION_ID = 1
        private const val LOG_FILE_NAME = "bot_logs.txt"
        
        val logMutex = Mutex()
        
        suspend fun logToFile(context: Context, message: String) {
            logMutex.withLock {
                try {
                    val file = File(context.filesDir, LOG_FILE_NAME)
                    val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    val line = "[$timestamp] $message\n"
                    
                    file.appendText(line, Charsets.UTF_8)
                    
                    if (file.length() > 1024 * 1024) {
                        file.writeText("Log cleared due to size limit\n", Charsets.UTF_8)
                    }
                } catch (_: Exception) {}
            }
        }
    }
    
    private lateinit var sharedPrefs: SharedPreferences
    private var botJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    @Volatile private var isRunning = false
    private val vkBots = ConcurrentHashMap<Int, KirdevBot>()
    private var botBrain: BotBrain? = null
    
    private val notificationDismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_NOTIFICATION_DISMISSED && isRunning) {
                addLog("⚠️ Уведомление закрыто! Восстанавливаем...")
                updateNotification(null)
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        sharedPrefs = getSharedPreferences("vk_bot_settings", MODE_PRIVATE)
        BotNotificationHelper.createChannel(this)
        BlacklistManager.init(this)
        SettingsManager.init(this)
        MediaResponses.init(this)

        val filter = IntentFilter(ACTION_NOTIFICATION_DISMISSED)
        ContextCompat.registerReceiver(
            this, notificationDismissReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            if (sharedPrefs.getBoolean("bot_running", false)) {
                addLog("🔄 Система перезапустила сервис. Восстанавливаем работу...")
                startBot()
            } else {
                stopSelf()
            }
            return START_STICKY
        }

        when (intent.action) {
            ACTION_START -> {
                if (isRunning) {
                    serviceScope.launch { syncActiveBots() }
                } else {
                    startBot()
                }
            }
            ACTION_STOP -> stopBot()
            ACTION_RELOAD -> reloadDatabase()
            else -> if (isRunning) updateNotification(null)
        }
        return START_STICKY
    }
    
    private fun startBot() {
        if (isRunning) return

        val notification = BotNotificationHelper.createForegroundNotification(this, "Запуск VK Bot...")
        try {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } catch (_: Exception) {
            stopSelf()
            return
        }
        
        clearLogs()
        sharedPrefs.edit { putBoolean("bot_running", true) }
        
        botJob?.cancel()
        botJob = serviceScope.launch {
            try {
                isRunning = true
                addLog("✅ Инициализация BotBrain...")
                botBrain = BotBrain(this@BotService).apply {
                    setLogCallback { message ->
                         if (!message.contains("Поиск") && !message.contains("score") && !message.contains("Индексация")) {
                             val clean = message.replace(Regex("\\[.*?]"), "")
                                                .replace(Regex("[🧠📊📁✅🔍📝🎯🌐🔗📬📩📎❌💥🤖]"), "")
                                                .trim()
                             serviceScope.launch { addLog(clean) }
                         }
                    }
                }
                
                val count = botBrain?.answerDatabase?.answersCount ?: 0
                addLog("📚 База загружена: $count ответов")
                sharedPrefs.edit { putInt("stat_total_answers", count) }
                
                startBackgroundTasks()
                syncActiveBots()
                
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    updateNotification("Ошибка: ${e.message}")
                    addLog("❌ Критическая ошибка: ${e.message}")
                    stopBot()
                }
            }
        }
    }
    
    private suspend fun syncActiveBots() = withContext(Dispatchers.IO) {
        val bots = BotDataManager.loadBots(this@BotService)
        val idList = bots.map { it.id }

        for (bot in bots) {
            launch {
                val token = bot.token
                val shouldRun = bot.isRunning
                val name = bot.name
                val id = bot.id
                
                if (shouldRun && token.isNotEmpty() && !vkBots.containsKey(id)) {
                    addLog("🚀 Запуск ядра для $name...")
                    
                    val newBot = KirdevBot(
                        token = token,
                        onLog = { logMsg ->
                                 if (!logMsg.contains("poll") && !logMsg.contains("ts") && !logMsg.contains("check")) {
                                    val clean = logMsg.replace(Regex("\\[.*?]"), "")
                                                      .replace(Regex("[🌐🔗📬✅🔍📩🧠📎📝❌💥🤖📊📁🎯]"), "")
                                                      .trim()
                                    addLog("[$name] $clean")
                                }
                        },
                        onStatusUpdate = { text -> if (isRunning) updateNotification(text) },
                        onWaitForNetwork = { NetworkHelper.waitForNetwork(this@BotService) }
                    )
                    
                    newBot.setMessageProcessor { message -> processSmartMessage(message, id) }
                    vkBots[id] = newBot
                    sharedPrefs.edit { putLong("bot_${id}_start_time", System.currentTimeMillis()) }
                    launch { newBot.start() }
                    addLog("🟢 $name успешно запущен")
                } 
                else if (!shouldRun && vkBots.containsKey(id)) {
                    vkBots.remove(id)?.stop()
                    sharedPrefs.edit { putLong("bot_${id}_start_time", 0) }
                    addLog("⏹ $name остановлен")
                }
            }
        }
        
        // Остановка ботов, которых больше нет в списке ID
        val runningIds = vkBots.keys.toList()
        for (rid in runningIds) {
            if (!idList.contains(rid)) {
                addLog("🗑 Выгрузка удаленного бота ID $rid")
                vkBots.remove(rid)?.stop()
            }
        }
        
        delay(200)
        updateStatusNotification()
    }

    private fun processSmartMessage(message: Map<String, Any>, botId: Int): Map<String, Any>? {
        return try {
            val text = message["text"] as? String ?: ""
            val fromId = (message["from_id"] as? Number)?.toString() ?: return null
            val authorName = (message["first_name"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: "Друг"
            @Suppress("UNCHECKED_CAST")
            val attachmentTypes = (message["attachment_types"] as? List<String>) ?: emptyList()

            val currentProcessed = sharedPrefs.getLong("bot_${botId}_processed", 0)
            sharedPrefs.edit { putLong("bot_${botId}_processed", currentProcessed + 1) }

            if (SettingsManager.isMediaResponsesEnabled) {
                attachmentTypes.forEach { type ->
                    MediaResponses.getRandomResponse(type)?.let { response ->
                        val currentAnswered = sharedPrefs.getLong("bot_${botId}_answered", 0)
                        sharedPrefs.edit { putLong("bot_${botId}_answered", currentAnswered + 1) }
                        return mapOf("text" to response)
                    }
                }
            }

            if (text.isBlank()) return null

            val response = botBrain?.processMessage(BotMessage(text, fromId, authorName, "vk"))
            if (response != null && !response.isEmpty) {
                val currentAnswered = sharedPrefs.getLong("bot_${botId}_answered", 0)
                sharedPrefs.edit { putLong("bot_${botId}_answered", currentAnswered + 1) }
                
                return mapOf(
                    "text" to response.text,
                    "attachments" to response.attachments
                )
            }
            null
        } catch (e: Exception) {
            addLog("⚠ Ошибка обработки: ${e.message}")
            null
        }
    }
    
    private fun addLog(message: String) {
        serviceScope.launch(Dispatchers.IO) {
            logToFile(applicationContext, message)
        }
    }
    
    private fun clearLogs() {
        serviceScope.launch(Dispatchers.IO) {
            logMutex.withLock {
                try {
                    File(filesDir, LOG_FILE_NAME).writeText("", Charsets.UTF_8)
                } catch (_: Exception) {}
            }
        }
    }

    private fun startBackgroundTasks() {
        serviceScope.launch {
            launch {
                while (isActive && isRunning) {
                    delay(10000L)
                    syncActiveBots()
                }
            }
            
            while (isActive && isRunning) {
                delay(30 * 60 * 1000L)
                vkBots.values.forEach { it.clearUserCache() }
                updateNotification(null)
            }
        }
    }


    private fun updateStatusNotification() {
        val totalActive = vkBots.size
        val text = if (totalActive > 0) {
            val botWord = if (totalActive == 1) "бот" else if (totalActive in 2..4) "бота" else "ботов"
            "В работе $totalActive $botWord"
        } else "Все боты выключены"
        updateNotification(text)
    }

    private fun updateNotification(text: String?) {
        if (!isRunning) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        
        val notificationText = text ?: run {
            val total = vkBots.size
            if (total > 0) {
                val word = if (total == 1) "бот" else if (total in 2..4) "бота" else "ботов"
                "В работе $total $word"
            } else "Все боты выключены"
        }

        try {
            val notification = BotNotificationHelper.createForegroundNotification(this, notificationText)
            nm.notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) {}
    }

    private fun reloadDatabase() {
        if (isRunning) {
            serviceScope.launch(Dispatchers.IO) {
                botBrain?.let { brain ->
                    brain.reloadDatabase()
                    val count = brain.answerDatabase?.answersCount ?: 0
                    addLog("🔄 База данных обновлена: $count ответов")
                }
                MediaResponses.loadAll()
            }
        }
    }

    private fun stopBot() {
        isRunning = false
        vkBots.values.forEach { it.stop() }
        vkBots.clear()
        botJob?.cancel()
        sharedPrefs.edit { putBoolean("bot_running", false) }
        addLog("⏹ Бот остановлен")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        addLog("⚙️ Приложение закрыто, бот продолжает работу в фоне")
    }
    
    override fun onDestroy() {
        isRunning = false
        vkBots.values.forEach { it.stop() }
        vkBots.clear()
        serviceScope.cancel()
        try {
            unregisterReceiver(notificationDismissReceiver)
        } catch (_: Exception) {}
        sharedPrefs.edit { putBoolean("bot_running", false) }
        super.onDestroy()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
