package com.vkbot.manager.botbrain

import java.io.Serializable

/**
 * Модель сообщения для мозга бота (Kotlin data class).
 */
data class BotMessage(
    val text: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val platform: String = "vk",
    val attachments: List<Attachment> = emptyList()
) : Serializable {

    fun hasAttachments(): Boolean = attachments.isNotEmpty()

    override fun toString(): String {
        return "[$platform] Msg $authorName: $text" +
               if (hasAttachments()) " (+ ${attachments.size} att)" else ""
    }

}
