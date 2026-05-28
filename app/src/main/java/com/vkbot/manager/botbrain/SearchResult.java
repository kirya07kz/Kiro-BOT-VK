package com.vkbot.manager.botbrain;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * Класс для хранения результатов поиска ответа (Java 21 Record).
 * Содержит найденный элемент базы и список захваченных групп (из Regex или масок).
 * Версия 2.2 - Record Optimized.
 */
@SuppressWarnings("unused")
public record SearchResult(
    AnswerElement answer,
    List<String> capturedGroups
) implements Serializable {
    
    @Serial
    private static final long serialVersionUID = 1L;

    public SearchResult {
        capturedGroups = capturedGroups != null ? List.copyOf(capturedGroups) : List.of();
    }

    /**
     * Конструктор для быстрого создания без групп.
     */
    public SearchResult(AnswerElement answer) {
        this(answer, List.of());
    }

    // Методы для обратной совместимости API
    public AnswerElement getAnswer() { return answer; }
    public List<String> getCapturedGroups() { return capturedGroups; }
    public boolean hasGroups() { return !capturedGroups.isEmpty(); }
}
