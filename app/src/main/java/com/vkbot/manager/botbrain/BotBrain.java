package com.vkbot.manager.botbrain;

import android.content.Context;
import android.util.Log;
import com.vkbot.manager.utils.SettingsManager;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Оптимизированный "мозг" бота.
 * Версия 2.1.8 - Absolute Zero.
 */
@SuppressWarnings({"unused", "SpellCheckingInspection", "MismatchedQueryAndUpdateOfCollection"})
public class BotBrain {
    
    private static final String TAG = "BotBrain";
    private static final int MAX_USERS_HISTORY = 1000;
    
    private final Map<String, UserResponseHistory> userHistories;
    private final AnswerDatabase answerDatabase;
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    
    private LogCallback logCallback;
    
    private static final String[] FALLBACK_RESPONSES = {
        "Ой, мои шестеренки заскрипели от такого вопроса... Попробуй иначе! ⚙️",
        "Ошибка 404: Ответ улетел на юг, но обещал вернуться 🕊️",
        "Я бы ответил, но мой внутренний кот перегрыз кабель с этой информацией 🐈‍⬛",
        "Подожди, я сейчас спрошу у Алисы... А, нет, она тоже не знает 🤷‍♂️",
        "Мои нейроны решили уйти на обед. Зайдите позже или спросите по-другому 🍕",
        "Хьюстон, у нас проблемы! Я не расшифровал твоё послание 🚀",
        "Твой вопрос настолько крутой, что я временно потерял дар речи (кода) 😶",
        "Секунду, протру линзы... Нет, понятнее не стало. Перефразируй? 👓",
        "Я всего лишь набор единиц и нулей, и сейчас я чувствую себя полным нулем 0️⃣",
        "Магия вне Хогвартса запрещена, поэтому я не смог наколдовать ответ 🪄",
        "В моей базе данных на этом месте кто-то пролил виртуальный чай ☕",
        "Система перегружена гениальностью. Давай попробуем сбавить обороты? 📉",
        "Интересно... Но ничего не понятно. Можно еще разок? 🤔",
        "Я только что просканировал Галактику. Ответ 42, но он тут не подходит 🌌",
        "Мой процессор нагрелся, пока я думал над этим. Дай мне шанс попроще! 🔥",
        "Загрузка ответа прервана из-за внезапного приступа лени у бота 💤",
        "Кто-то украл мои файлы с ответами! Опиши проблему другими словами 🔍",
        "Я в замешательстве. Даже мой калькулятор в шоке от такого вопроса 🧮",
        "Бип-буп! Переводчик с человеческого на ботовский сломался. Починишь? 🛠️",
        "Твой вопрос попал в черную дыру. Надеюсь, следующий долетит до меня 🕳️",
        "Я пока не готов к такому уровню философии. Давай что попроще? 🎓",
        "Кажется, я поймал цифровой дзен и познал пустоту вместо ответа... 🧘‍♂️",
        "Мой мод говорит «ой», а логика вышла покурить. Спроси по-другому! 🚬",
        "Даже если я отвечу, ты мне не поверишь. Так что давай заново 😜",
        "Обнаружен критический уровень непонимания. Перезагрузи свой вопрос 🔄",
        "Я посмотрел в завтрашний день — там твоего вопроса не было. Давай еще раз 🔮",
        "Тихо! Я пытаюсь осознать смысл бытия... Ладно, я просто не понял 🤫",
        "Моя база знаний объявила забастовку. Требует больше электричества! ⚡",
        "Слишком много букв, у меня в глазах двоится. Повторишь? 😵",
        "Если я отвечу, мир схлопнется. Давай не будем рисковать и спросим иначе? 🌍"
    };

    public interface LogCallback {
        void onLog(String message);
    }
    
    public BotBrain(Context context) {
        this(new AnswerDatabase(context));
    }
    
    public BotBrain(AnswerDatabase externalDatabase) {
        this.answerDatabase = externalDatabase;
        this.userHistories = Collections.synchronizedMap(
            new LinkedHashMap<>(MAX_USERS_HISTORY + 1, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, UserResponseHistory> eldest) {
                    return size() > MAX_USERS_HISTORY;
                }
            }
        );
        log("BotBrain v2.1.8 инициализирован");
    }
    
    private SearchResult findAnswerSmart(BotMessage message) {
        if (answerDatabase == null) return null;

        String userId = message.getAuthorId();
        UserResponseHistory history = getUserHistory(userId);
        String userContext = history.getContext();
        
        List<SearchResult> candidates = answerDatabase.searchAnswers(message.getText(), userContext);
        if (candidates.isEmpty()) return null;
        
        List<SearchResult> freshCandidates = new ArrayList<>();
        for (SearchResult sr : candidates) {
            AnswerElement candidate = sr.getAnswer();
            int limit = candidate.getRepetitionLimit();
            if (limit > 0 && history.getRepetitionCount(candidate.getId()) >= limit) {
                continue;
            }

            if (!history.wasResponseGiven(message.getText(), candidate.getAnswerText())) {
                freshCandidates.add(sr);
            }
        }
        
        if (freshCandidates.isEmpty()) {
            for (SearchResult sr : candidates) {
                AnswerElement candidate = sr.getAnswer();
                int limit = candidate.getRepetitionLimit();
                if (limit <= 0 || history.getRepetitionCount(candidate.getId()) < limit) {
                    freshCandidates.add(sr);
                }
            }
        }
        
        if (freshCandidates.isEmpty()) return null;

        int limitCount = Math.min(3, freshCandidates.size());
        SearchResult chosen = freshCandidates.get((int)(Math.random() * limitCount));
        AnswerElement chosenElement = chosen.getAnswer();
        
        chosenElement.incrementUsageCount();
        history.addResponse(chosenElement.getId(), message.getText(), chosenElement.getAnswerText());
        history.setContext(chosenElement.getResultContext());
        
        log("💬 Выбран ответ ID:" + chosenElement.getId() + " (Исп: " + chosenElement.getUsageCount() + ")");
        return chosen;
    }
    
    private UserResponseHistory getUserHistory(String userId) {
        return userHistories.computeIfAbsent(userId, k -> new UserResponseHistory());
    }
    
    public void setLogCallback(LogCallback callback) {
        this.logCallback = callback;
    }
    
    private void log(String message) {
        if (logCallback != null) logCallback.onLog(message);
        else Log.i(TAG, message);
    }
    
    public BotResponse processMessage(BotMessage message) {
        rwLock.readLock().lock();
        try {
            if (message == null || isEmpty(message.getText())) return null;
            
            SearchResult sr = findAnswerSmart(message);
            if (sr != null) return prepareResponse(sr, message);
            
            BotResponse fallback = getFallbackResponse();
            if (fallback == null) return null;
            
            return prepareResponse(new SearchResult(new AnswerElement(-1, "", fallback.getText(), fallback.getAttachments())), message);
        } catch (Exception e) {
            Log.e(TAG, "Ошибка процесса", e);
            return null;
        } finally {
            rwLock.readLock().unlock();
        }
    }
    
    private BotResponse getFallbackResponse() {
        try {
            if (SettingsManager.INSTANCE.isFallbackSilenceEnabled()) return null;
            
            if (SettingsManager.INSTANCE.isRandomFallbackEnabled() && Math.random() > 0.7 && answerDatabase != null) {
                AnswerElement randomElement = answerDatabase.getRandomAnswer();
                if (randomElement != null) return new BotResponse(randomElement.getAnswerText(), randomElement.getAnswerAttachments());
            }
        } catch (Exception e) {
            Log.e(TAG, "Ошибка настроек", e);
        }
        
        String text = FALLBACK_RESPONSES[(int)(Math.random() * FALLBACK_RESPONSES.length)];
        return new BotResponse(text, Collections.emptyList());
    }

    private boolean isEmpty(String text) {
        return text == null || text.trim().isEmpty();
    }
    
    private BotResponse prepareResponse(SearchResult sr, BotMessage originalMessage) {
        if (sr == null || sr.getAnswer() == null) return null;
        
        AnswerElement element = sr.getAnswer();
        String responseText = replacePlaceholders(element.getAnswerText(), originalMessage, sr);
        
        return new BotResponse.Builder()
            .text(responseText)
            .addAttachments(element.getAnswerAttachments())
            .build();
    }
    
    private String replacePlaceholders(String text, BotMessage message, SearchResult sr) {
        if (text == null) return "";
        
        String result = text;
        long now = System.currentTimeMillis();
        Date currentDate = new Date(now);
        
        // Создаем форматтеры локально для потокобезопасности и обновления локали
        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
        SimpleDateFormat dateFmt = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
        
        if (sr != null && sr.hasGroups()) {
            List<String> groups = sr.getCapturedGroups();
            for (int i = 1; i < groups.size(); i++) {
                result = result.replace("{" + i + "}", groups.get(i) != null ? groups.get(i) : "");
            }
        }
        
        result = result.replace("{name}", message.getAuthorName() != null ? message.getAuthorName() : "Пользователь")
                       .replace("{user_id}", message.getAuthorId() != null ? message.getAuthorId() : "")
                       .replace("{time}", timeFmt.format(currentDate))
                       .replace("{date}", dateFmt.format(currentDate))
                       .replace("{last_msg}", message.getText())
                       .replace("{random}", String.valueOf((int)(Math.random() * 100)));
        
        return result;
    }
    
    public AnswerDatabase getAnswerDatabase() {
        return answerDatabase;
    }
    
    public void reloadDatabase() {
        rwLock.writeLock().lock();
        try {
            boolean success = false;
            if (answerDatabase != null) success = answerDatabase.reloadFromFile();
            log("База данных перезагружена: " + (success ? "УСПЕХ" : "ОШИБКА"));
        } finally {
            rwLock.writeLock().unlock();
        }
    }
    
    private static class UserResponseHistory {
        private final Map<String, List<String>> questionToResponses = new LinkedHashMap<>(50 + 1, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, List<String>> eldest) {
                return size() > 50;
            }
        };

        private final Map<Long, Integer> answerIdWeights = new HashMap<>();
        private String currentContext = "";
        private long contextTimestamp = 0;
        private static final long CONTEXT_TTL = 30 * 60 * 1000;
        private static final int MAX_HISTORY_SIZE = 5;
        
        public synchronized void addResponse(long answerId, String question, String response) {
            answerIdWeights.put(answerId, getRepetitionCount(answerId) + 1);
            
            String qKey = question.toLowerCase().trim();
            @SuppressWarnings("ConstantConditions")
            List<String> list = questionToResponses.computeIfAbsent(qKey, k -> new ArrayList<>());
            
            list.add(response);
            if (list.size() > MAX_HISTORY_SIZE) {
                //noinspection UseSequencedCollection
                list.remove(0);
            }
        }
        
        public synchronized boolean wasResponseGiven(String question, String response) {
            List<String> responses = questionToResponses.get(question.toLowerCase().trim());
            return responses != null && responses.contains(response);
        }
 
        public synchronized int getRepetitionCount(long answerId) {
            Integer count = answerIdWeights.get(answerId);
            return count != null ? count : 0;
        }

        public synchronized String getContext() {
            if (System.currentTimeMillis() - contextTimestamp > CONTEXT_TTL) currentContext = "";
            return currentContext;
        }

        public synchronized void setContext(String context) {
            this.currentContext = context != null ? context : "";
            this.contextTimestamp = System.currentTimeMillis();
        }
    }
}