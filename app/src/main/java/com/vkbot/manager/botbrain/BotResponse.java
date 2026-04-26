package com.vkbot.manager.botbrain;

import androidx.annotation.NonNull;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Модель ответа бота.
 * Реализация на Java 21 Record.
 * Версия 2.1.7 - Zero-Warning & No Compilation Errors.
 */
@SuppressWarnings("unused")
public record BotResponse(
    String text,
    List<Attachment> attachments
) implements Serializable {
    
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Компактный конструктор для валидации и нормализации данных.
     * Он автоматически объединяется с каноническим конструктором.
     */
    public BotResponse {
        text = text != null ? text : "";
        attachments = attachments != null ? List.copyOf(attachments) : List.of();
    }

    // Методы для обратной совместимости
    public String getText() { return text; }
    public List<Attachment> getAttachments() { return attachments; }
    public boolean isEmpty() { return text.isEmpty() && attachments.isEmpty(); }
    
    @NonNull
    @Override
    public String toString() {
        return "BotResponse { text='" + text + "', atts=" + attachments.size() + " }";
    }
    
    public static class Builder {
        private String text = "";
        private final List<Attachment> attachments = new ArrayList<>();
        
        public Builder text(String text) {
            this.text = text != null ? text : "";
            return this;
        }
        
        public Builder addAttachments(List<Attachment> attachments) {
            if (attachments != null) this.attachments.addAll(attachments);
            return this;
        }
        
        public BotResponse build() {
            return new BotResponse(text, attachments);
        }
    }
}