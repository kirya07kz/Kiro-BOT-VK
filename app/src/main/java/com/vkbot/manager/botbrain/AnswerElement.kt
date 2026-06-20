package com.vkbot.manager.botbrain

import java.io.Serializable
import java.util.Date

/**
 * Элемент базы ответов (Kotlin data class).
 */
data class AnswerElement(
    val id: Long,
    val questionText: String = "",
    val answerText: String = "",
    val answerAttachments: List<Attachment> = emptyList(),
    private val _createdDate: Date = Date(),
    var usageCount: Int = 0,
    val repetitionLimit: Int = 0,
    val requiredContext: String = "",
    val resultContext: String = ""
) : Serializable {

    val createdDate: Date
        get() = Date(_createdDate.time)

    fun incrementUsageCount() {
        usageCount++
    }

    override fun toString(): String {
        return "ID: $id | Q: $questionText"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AnswerElement) return false

        if (id != other.id) return false
        if (repetitionLimit != other.repetitionLimit) return false
        if (questionText != other.questionText) return false
        if (answerText != other.answerText) return false
        if (answerAttachments != other.answerAttachments) return false
        if (requiredContext != other.requiredContext) return false
        if (resultContext != other.resultContext) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + questionText.hashCode()
        result = 31 * result + answerText.hashCode()
        result = 31 * result + repetitionLimit
        result = 31 * result + answerAttachments.hashCode()
        result = 31 * result + requiredContext.hashCode()
        result = 31 * result + resultContext.hashCode()
        return result
    }

}
