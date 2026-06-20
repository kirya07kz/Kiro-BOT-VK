package com.vkbot.manager.botbrain

import java.io.Serializable
import java.util.regex.Pattern

/**
 * Современная модель вложения (переведено на Kotlin).
 * Автоматически генерирует геттеры, equals, hashCode и toString.
 * Формат VK: type{owner_id}_{id}_{access_key}
 */
data class Attachment(
    val type: String = "",
    val id: String = "",
    val ownerId: String = "",
    val accessKey: String = "",
    val url: String = "",
    val title: String = ""
) : Serializable {

    fun toVkString(): String {
        val base = "${type}${ownerId}_$id"
        return if (accessKey.isEmpty()) base else "${base}_$accessKey"
    }

    override fun toString(): String {
        return toVkString()
    }

    companion object {
        private val VK_STRING_PATTERN = Pattern.compile("([a-z]+)(-?\\d+)_(\\d+)(?:_(\\w+))?")

        @JvmStatic
        fun parse(vkString: String?): Attachment? {
            if (vkString.isNullOrEmpty()) return null

            val matcher = VK_STRING_PATTERN.matcher(vkString.trim())
            if (matcher.find()) {
                return Attachment(
                    type = matcher.group(1) ?: "",
                    ownerId = matcher.group(2) ?: "",
                    id = matcher.group(3) ?: "",
                    accessKey = matcher.group(4) ?: "",
                    url = "",
                    title = ""
                )
            }
            return null
        }
    }
}
