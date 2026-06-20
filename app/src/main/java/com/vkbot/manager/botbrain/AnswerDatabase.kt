package com.vkbot.manager.botbrain

import android.content.Context
import android.util.Log
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.min

/**
 * Оптимизированная база данных ответов с LRU-кэшем и безопасной инициализацией (переведено на Kotlin).
 */
class AnswerDatabase(context: Context?) {

    private val answers = mutableMapOf<Long, AnswerElement>()
    val fileManager: AndroidFileManager? = context?.let { AndroidFileManager(it) }

    // Индексы для быстрого поиска
    private val exactMatchIndex = mutableMapOf<String, MutableList<AnswerElement>>()
    private val keywordIndex = mutableMapOf<String, MutableList<AnswerElement>>()

    // Кэш значений для быстрого getRandomAnswer
    private var cachedValueList = listOf<AnswerElement>()

    // Индекс для регулярных выражений
    private val regexIndex = mutableMapOf<AnswerElement, Pattern>()

    init {
        Log.i(TAG, "📱 === AnswerDatabase v2.1.1 (Kotlin) ===")
        // Синхронная загрузка
        loadFromFile()
    }

    /**
     * Загрузка базы данных с построением индексов.
     */
    private fun loadFromFile() {
        val startTime = System.currentTimeMillis()

        val loadedAnswers = fileManager?.loadAnswerDatabase() ?: emptyList()

        synchronized(answers) {
            answers.clear()
            exactMatchIndex.clear()
            keywordIndex.clear()
            regexIndex.clear()

            for (answer in loadedAnswers) {
                answers[answer.id] = answer
                // Строим индексы для быстрого поиска
                buildIndexesForAnswer(answer)
            }
            // Обновляем кэш значений
            cachedValueList = answers.values.toList()
        }

        val loadTime = System.currentTimeMillis() - startTime
        Log.i(TAG, "⚡ База данных загружена (${answers.size} эл.) за ${loadTime}мс")
    }

    private fun buildIndexesForAnswer(answer: AnswerElement) {
        val questionLower = normalizeText(answer.questionText)

        // Поддержка масок со звездочкой
        if (questionLower.contains("*") && !questionLower.startsWith("regex:")) {
            val patternStr = questionLower
                .replace(".", "\\.")
                .replace("?", "\\?")
                .replace("+", "\\+")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("*", "(.*)")

            try {
                val pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE)
                regexIndex[answer] = pattern
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка компиляции маски: $questionLower", e)
            }
            return
        }

        // Проверка на Regex
        if (questionLower.startsWith("regex:")) {
            try {
                val patternStr = questionLower.substring(6).trim()
                val pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE)
                regexIndex[answer] = pattern
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка компиляции regex: $questionLower", e)
            }
            return
        }

        // Индекс точного совпадения
        exactMatchIndex.getOrPut(questionLower) { mutableListOf() }.add(answer)

        // Индекс по ключевым словам
        val cleanText = questionLower.replace(Regex("[^a-zA-Zа-яА-Я0-9 ]"), " ")
        val words = cleanText.split("\\s+".toRegex())

        for (word in words) {
            if (word.length >= 2) {
                keywordIndex.getOrPut(word) { mutableListOf() }.add(answer)
            }
        }
    }

    private fun normalizeText(text: String?): String {
        return text?.lowercase(Locale.getDefault())?.trim() ?: ""
    }

    /**
     * Интеллектуальный поиск ответов с учетом контекста и Regex.
     */
    fun searchAnswers(query: String?, userContext: String?): List<SearchResult> {
        if (query.isNullOrBlank()) {
            return emptyList()
        }

        val queryLower = query.lowercase(Locale.getDefault()).trim()
        val queryNormalized = queryLower.replace(Regex("[^a-zа-я0-9 ]"), " ").trim()
        val queryWords = queryNormalized.split("\\s+".toRegex())

        // Временные списки для разных уровней уверенности
        val exactContextual = mutableListOf<SearchResult>()
        val exactGeneral = mutableListOf<SearchResult>()
        val candidateSet = mutableSetOf<AnswerElement>()
        val regexContextual = mutableListOf<SearchResult>()
        val regexGeneral = mutableListOf<SearchResult>()

        val userCtx = userContext ?: ""

        synchronized(answers) {
            // 1. Быстрый поиск точного совпадения (O(1))
            val exacts = exactMatchIndex[queryLower]
            if (exacts != null) {
                for (e in exacts) {
                    val sr = SearchResult(e)
                    if (e.requiredContext.equals(userCtx, ignoreCase = true) && e.requiredContext.isNotEmpty()) {
                        exactContextual.add(sr)
                    } else if (e.requiredContext.isEmpty()) {
                        exactGeneral.add(sr)
                    }
                }
            }

            // 2. Сбор кандидатов по словам
            for (word in queryWords) {
                if (word.length >= 2) {
                    val matches = keywordIndex[word]
                    if (matches != null) {
                        candidateSet.addAll(matches)
                    }
                }
            }

            // 3. Regex поиск
            if (regexIndex.isNotEmpty()) {
                for ((e, pattern) in regexIndex) {
                    val matcher = pattern.matcher(query)
                    if (matcher.find()) {
                        val groups = mutableListOf<String>()
                        for (i in 0..matcher.groupCount()) {
                            groups.add(matcher.group(i) ?: "")
                        }

                        val sr = SearchResult(e, groups)
                        if (e.requiredContext.equals(userCtx, ignoreCase = true) && e.requiredContext.isNotEmpty()) {
                            regexContextual.add(sr)
                        } else if (e.requiredContext.isEmpty()) {
                            regexGeneral.add(sr)
                        }
                    }
                }
            }

            // Fallback: полный скан для маленьких баз
            if (exactContextual.isEmpty() && exactGeneral.isEmpty() && candidateSet.isEmpty() && answers.size < 2000) {
                candidateSet.addAll(answers.values)
            }
        }

        // 4. Фильтрация и ранжирование
        val highConfidenceContextual = mutableListOf<SearchResult>()
        val mediumConfidenceContextual = mutableListOf<SearchResult>()
        val highConfidenceGeneral = mutableListOf<SearchResult>()
        val mediumConfidenceGeneral = mutableListOf<SearchResult>()

        for (element in candidateSet) {
            if (isAlreadyInResults(element, exactContextual, exactGeneral, regexContextual, regexGeneral)) continue

            val questionNorm = element.questionText.lowercase(Locale.getDefault())
                .replace(Regex("[^a-zа-я0-9 ]"), " ").trim()
            val questionWords = questionNorm.split("\\s+".toRegex())

            val validQWords = questionWords.count { it.isNotEmpty() }
            if (validQWords == 0) continue

            var matchCount = 0
            for (qWord in questionWords) {
                if (qWord.isEmpty()) continue
                for (uWord in queryWords) {
                    if (uWord.isEmpty()) continue
                    if (qWord == uWord || isFuzzyWordMatch(qWord, uWord)) {
                        matchCount++
                        break
                    }
                }
            }

            val matchRatio = matchCount.toFloat() / validQWords
            val isContextMatch = element.requiredContext.equals(userCtx, ignoreCase = true) && element.requiredContext.isNotEmpty()
            val isNoContext = element.requiredContext.isEmpty()

            if (isContextMatch || isNoContext) {
                val sr = SearchResult(element)
                if (matchRatio >= 0.95f) {
                    if (isContextMatch) highConfidenceContextual.add(sr)
                    else highConfidenceGeneral.add(sr)
                } else if (matchRatio >= 0.65f) {
                    if (isContextMatch) mediumConfidenceContextual.add(sr)
                    else mediumConfidenceGeneral.add(sr)
                }
            }
        }

        // Ранжирование по популярности
        val usageComparator = Comparator<SearchResult> { a, b ->
            b.answer.usageCount.compareTo(a.answer.usageCount)
        }

        exactContextual.sortWith(usageComparator)
        exactGeneral.sortWith(usageComparator)
        regexContextual.sortWith(usageComparator)
        regexGeneral.sortWith(usageComparator)
        highConfidenceContextual.sortWith(usageComparator)
        highConfidenceGeneral.sortWith(usageComparator)
        mediumConfidenceContextual.sortWith(usageComparator)
        mediumConfidenceGeneral.sortWith(usageComparator)

        val finalResults = mutableListOf<SearchResult>()
        finalResults.addAll(exactContextual)
        finalResults.addAll(exactGeneral)
        finalResults.addAll(regexContextual)
        finalResults.addAll(regexGeneral)
        finalResults.addAll(highConfidenceGeneral)
        finalResults.addAll(highConfidenceContextual)
        finalResults.addAll(mediumConfidenceContextual)
        finalResults.addAll(mediumConfidenceGeneral)

        return finalResults
    }

    private fun isAlreadyInResults(e: AnswerElement, vararg lists: List<SearchResult>): Boolean {
        for (list in lists) {
            for (sr in list) {
                if (sr.answer.id == e.id) return true
            }
        }
        return false
    }

    val answersCount: Int
        get() {
            synchronized(answers) {
                return answers.size
            }
        }

    fun getRandomAnswer(): AnswerElement? {
        synchronized(answers) {
            if (cachedValueList.isEmpty()) return null
            return cachedValueList.randomOrNull()
        }
    }

    fun reloadFromFile(): Boolean {
        return try {
            loadFromFile()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка перезагрузки базы данных", e)
            false
        }
    }

    companion object {
        private const val TAG = "AnswerDatabase"

        private fun isFuzzyWordMatch(w1: String, w2: String): Boolean {
            if (kotlin.math.abs(w1.length - w2.length) > 3) return false
            if (w1.length <= 3 || w2.length <= 3) return w1 == w2

            val minLen = min(w1.length, w2.length)
            var commonPrefix = 0
            for (i in 0 until minLen) {
                if (w1[i] == w2[i]) commonPrefix++
                else break
            }

            if (commonPrefix >= 4 && commonPrefix >= minLen - 2) return true

            val dist = calculateLevenshteinDistance(w1, w2)
            return dist <= (minLen / 4) + 1
        }

        private fun calculateLevenshteinDistance(s1: String, s2: String): Int {
            val m = s1.length
            val n = s2.length
            val d = Array(m + 1) { IntArray(n + 1) }
            for (i in 0..m) d[i][0] = i
            for (j in 0..n) d[0][j] = j
            for (i in 1..m) {
                for (j in 1..n) {
                    val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                    d[i][j] = min(min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost)
                }
            }
            return d[m][n]
        }
    }
}
