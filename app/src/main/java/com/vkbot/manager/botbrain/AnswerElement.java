package com.vkbot.manager.botbrain;

import androidx.annotation.NonNull;
import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Элемент базы ответов (Java 21 Record).
 * Версия 2.2 - Record Optimized & Zero-Warning.
 */
@SuppressWarnings("unused")
public record AnswerElement(
    long id,
    String questionText,
    String answerText,
    List<Attachment> answerAttachments,
    Date createdDate,
    AtomicInteger usageCount,
    int repetitionLimit,
    String requiredContext,
    String resultContext
) implements Serializable {
    
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Компактный конструктор для нормализации данных.
     */
    public AnswerElement {
        questionText = questionText != null ? questionText : "";
        answerText = answerText != null ? answerText : "";
        createdDate = createdDate != null ? new Date(createdDate.getTime()) : new Date();
        usageCount = usageCount != null ? usageCount : new AtomicInteger(0);
        answerAttachments = answerAttachments != null ? List.copyOf(answerAttachments) : List.of();
        requiredContext = requiredContext != null ? requiredContext : "";
        resultContext = resultContext != null ? resultContext : "";
    }

    /**
     * Конструктор для быстрого создания.
     */
    public AnswerElement(long id, String questionText, String answerText, List<Attachment> attachments) {
        this(id, questionText, answerText, attachments, new Date(), new AtomicInteger(0), 0, "", "");
    }
    
    /**
     * Конструктор для загрузки из файла.
     */
    public AnswerElement(long id, String questionText, String answerText, List<Attachment> attachments,
                         Date createdDate, int initialUsage, int repetitionLimit,
                         String requiredContext, String resultContext) {
        this(id, questionText, answerText, attachments, createdDate, new AtomicInteger(initialUsage), 
             repetitionLimit, requiredContext, resultContext);
    }

    // Методы для обратной совместимости API
    public long getId() { return id; }
    public String getQuestionText() { return questionText; }
    public String getAnswerText() { return answerText; }
    public Date getCreatedDate() { return new Date(createdDate.getTime()); }
    public int getUsageCount() { return usageCount.get(); }
    public int getRepetitionLimit() { return repetitionLimit; }
    public String getRequiredContext() { return requiredContext; }
    public String getResultContext() { return resultContext; }
    public List<Attachment> getAnswerAttachments() { return answerAttachments; }
    
    public void incrementUsageCount() {
        usageCount.incrementAndGet();
    }
    
    @NonNull
    @Override
    public String toString() {
        return "ID: " + id + " | Q: " + questionText;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AnswerElement that)) return false;
        return id == that.id && repetitionLimit == that.repetitionLimit && 
                Objects.equals(questionText, that.questionText) && 
                Objects.equals(answerText, that.answerText) && 
                Objects.equals(answerAttachments, that.answerAttachments) && 
                Objects.equals(requiredContext, that.requiredContext) && 
                Objects.equals(resultContext, that.resultContext);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, questionText, answerText, repetitionLimit, answerAttachments, requiredContext, resultContext);
    }
}