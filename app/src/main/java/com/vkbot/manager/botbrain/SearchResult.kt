package com.vkbot.manager.botbrain

import java.io.Serializable

/**
 * Класс для хранения результатов поиска ответа (Kotlin data class).
 * Содержит найденный элемент базы и список захваченных групп (из Regex или масок).
 */
data class SearchResult(
    val answer: AnswerElement,
    val capturedGroups: List<String> = emptyList()
) : Serializable {
    
    fun hasGroups(): Boolean = capturedGroups.isNotEmpty()

}
