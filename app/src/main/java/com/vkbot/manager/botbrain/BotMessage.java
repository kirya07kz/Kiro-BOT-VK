package com.vkbot.manager.botbrain;

import androidx.annotation.NonNull;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * Модель сообщения для мозга бота (Java 21 Record).
 * Версия 2.1.5 - Restored platform field for compatibility.
 */
@SuppressWarnings("unused")
public record BotMessage(
    String text,
    String authorId,
    String authorName,
    String platform,
    List<Attachment> attachments
) implements Serializable {
    
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Компактный конструктор для нормализации данных.
     */
    public BotMessage {
        text = text != null ? text : "";
        authorId = authorId != null ? authorId : "";
        authorName = authorName != null ? authorName : "";
        platform = platform != null ? platform : "vk";
        attachments = attachments != null ? List.copyOf(attachments) : List.of();
    }

    /**
     * Конструктор для совместимости с Kotlin слоем (без вложений).
     */
    public BotMessage(String text, String authorId, String authorName, String platform) {
        this(text, authorId, authorName, platform, List.of());
    }

    // Методы для обратной совместимости
    public String getText() { return text; }
    public String getAuthorId() { return authorId; }
    public String getAuthorName() { return authorName; }
    public String getPlatform() { return platform; }
    
    public boolean hasAttachments() {
        return !attachments.isEmpty();
    }
    
    @NonNull
    @Override
    public String toString() {
        return "[" + platform + "] Msg " + authorName + ": " + text + 
               (hasAttachments() ? " (+ " + attachments.size() + " att)" : "");
    }
}