package com.vkbot.manager.botbrain

import android.content.Context
import android.util.Log
import com.vkbot.manager.utils.SettingsManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * Оптимизированный "мозг" бота (переведен на Kotlin).
 */
class BotBrain(val answerDatabase: AnswerDatabase?) {

    fun interface LogCallback {
        fun onLog(message: String)
    }

    private val userHistories = object : LinkedHashMap<String, UserResponseHistory>(MAX_USERS_HISTORY + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, UserResponseHistory>): Boolean {
            return size > MAX_USERS_HISTORY
        }
    }

    private val rwLock = ReentrantReadWriteLock()
    private var logCallback: LogCallback? = null

    constructor(context: Context?) : this(AnswerDatabase(context))

    private var fallbackResponses: List<String> = emptyList()

    init {
        log("BotBrain v2.1.8 (Kotlin) инициализирован")
        loadFallbacks()
    }

    private fun loadFallbacks() {
        if (answerDatabase?.fileManager != null) {
            fallbackResponses = answerDatabase.fileManager.loadTxtList("fallback.txt")
        }
    }

    fun setLogCallback(callback: LogCallback?) {
        this.logCallback = callback
    }

    private fun log(message: String) {
        logCallback?.onLog(message) ?: Log.i(TAG, message)
    }

    fun processMessage(message: BotMessage?): BotResponse? {
        rwLock.readLock().lock()
        try {
            if (message == null || message.text.isBlank()) return null

            val sr = findAnswerSmart(message)
            if (sr != null) return prepareResponse(sr, message)

            val fallback = getFallbackResponse() ?: return null

            return prepareResponse(SearchResult(AnswerElement(id = -1, answerText = fallback.text, answerAttachments = fallback.attachments)), message)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка процесса", e)
            return null
        } finally {
            rwLock.readLock().unlock()
        }
    }

    private fun findAnswerSmart(message: BotMessage): SearchResult? {
        if (answerDatabase == null) return null

        val userId = message.authorId
        val history = getUserHistory(userId)
        val userContext = history.getContext()

        val candidates = answerDatabase.searchAnswers(message.text, userContext)
        if (candidates.isEmpty()) return null

        val freshCandidates = mutableListOf<SearchResult>()
        for (sr in candidates) {
            val candidate = sr.answer
            val limit = candidate.repetitionLimit
            if (limit > 0 && history.getRepetitionCount(candidate.id) >= limit) {
                continue
            }

            if (!history.wasResponseGiven(message.text, candidate.answerText)) {
                freshCandidates.add(sr)
            }
        }

        if (freshCandidates.isEmpty()) {
            for (sr in candidates) {
                val candidate = sr.answer
                val limit = candidate.repetitionLimit
                if (limit <= 0 || history.getRepetitionCount(candidate.id) < limit) {
                    freshCandidates.add(sr)
                }
            }
        }

        if (freshCandidates.isEmpty()) return null

        val limitCount = minOf(3, freshCandidates.size)
        val chosen = freshCandidates[(0 until limitCount).random()]
        val chosenElement = chosen.answer

        chosenElement.incrementUsageCount()
        history.addResponse(chosenElement.id, message.text, chosenElement.answerText)
        history.setContext(chosenElement.resultContext)

        log("💬 Выбран ответ ID:${chosenElement.id} (Исп: ${chosenElement.usageCount})")
        return chosen
    }

    private fun getUserHistory(userId: String): UserResponseHistory {
        synchronized(userHistories) {
            var history = userHistories[userId]
            if (history == null) {
                history = UserResponseHistory()
                userHistories[userId] = history
            }
            return history
        }
    }

    private fun getFallbackResponse(): BotResponse? {
        try {
            if (SettingsManager.isFallbackSilenceEnabled) return null

            if (SettingsManager.isRandomFallbackEnabled && kotlin.random.Random.nextDouble() > 0.7 && answerDatabase != null) {
                val randomElement = answerDatabase.getRandomAnswer()
                if (randomElement != null) return BotResponse(randomElement.answerText, randomElement.answerAttachments)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка настроек", e)
        }

        val list = fallbackResponses
        if (list.isEmpty()) {
            return BotResponse("Извините, я не знаю, что ответить. Файл fallback.txt пуст или не найден.", emptyList())
        }
        val text = list.random()
        return BotResponse(text, emptyList())
    }

    private fun prepareResponse(sr: SearchResult?, originalMessage: BotMessage): BotResponse? {
        if (sr?.answer == null) return null

        val element = sr.answer
        val responseText = replacePlaceholders(element.answerText, originalMessage, sr)

        return BotResponse.Builder()
            .text(responseText)
            .addAttachments(element.answerAttachments)
            .build()
    }

    private fun replacePlaceholders(text: String?, message: BotMessage, sr: SearchResult?): String {
        var result = text ?: ""
        val now = System.currentTimeMillis()
        val currentDate = Date(now)

        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFmt = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

        if (sr != null && sr.hasGroups()) {
            val groups = sr.capturedGroups
            for (i in 1 until groups.size) {
                result = result.replace("{$i}", groups[i])
            }
        }

        result = result.replace("{name}", message.authorName.ifEmpty { "Пользователь" })
            .replace("{user_id}", message.authorId)
            .replace("{time}", timeFmt.format(currentDate))
            .replace("{date}", dateFmt.format(currentDate))
            .replace("{last_msg}", message.text)
            .replace("{random}", (0..100).random().toString())

        return result
    }

    fun reloadDatabase() {
        rwLock.writeLock().lock()
        try {
            var success = false
            if (answerDatabase != null) {
                success = answerDatabase.reloadFromFile()
                loadFallbacks()
            }
            log("База данных перезагружена: " + if (success) "УСПЕХ" else "ОШИБКА")
        } finally {
            rwLock.writeLock().unlock()
        }
    }

    private class UserResponseHistory {
        private val questionToResponses = object : LinkedHashMap<String, MutableList<String>>(50 + 1, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, MutableList<String>>): Boolean {
                return size > 50
            }
        }

        private val answerIdWeights = mutableMapOf<Long, Int>()
        private var currentContext = ""
        private var contextTimestamp: Long = 0

        @Synchronized
        fun addResponse(answerId: Long, question: String, response: String) {
            answerIdWeights[answerId] = getRepetitionCount(answerId) + 1

            val qKey = question.lowercase(Locale.getDefault()).trim()
            val list = questionToResponses.getOrPut(qKey) { mutableListOf() }

            list.add(response)
            if (list.size > MAX_HISTORY_SIZE) {
                list.removeAt(0)
            }
        }

        @Synchronized
        fun wasResponseGiven(question: String, response: String): Boolean {
            val responses = questionToResponses[question.lowercase(Locale.getDefault()).trim()]
            return responses?.contains(response) == true
        }

        @Synchronized
        fun getRepetitionCount(answerId: Long): Int {
            return answerIdWeights[answerId] ?: 0
        }

        @Synchronized
        fun getContext(): String {
            if (System.currentTimeMillis() - contextTimestamp > CONTEXT_TTL) currentContext = ""
            return currentContext
        }

        @Synchronized
        fun setContext(context: String?) {
            this.currentContext = context ?: ""
            this.contextTimestamp = System.currentTimeMillis()
        }

        companion object {
            private const val CONTEXT_TTL = 30 * 60 * 1000L
            private const val MAX_HISTORY_SIZE = 5
        }
    }

    companion object {
        private const val TAG = "BotBrain"
        private const val MAX_USERS_HISTORY = 1000
    }
}
