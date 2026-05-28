package com.vkbot.manager.botbrain;

import android.content.Context;
import android.util.Log;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Оптимизированная база данных ответов с LRU-кэшем и безопасной инициализацией.
 * Версия 2.1.1 - No Warnings & Production Ready.
 */
public class AnswerDatabase {
    
    private static final String TAG = "AnswerDatabase";
    
    private final Map<Long, AnswerElement> answers = new HashMap<>();
    private final AndroidFileManager fileManager;
    
    // Индексы для быстрого поиска
    private final Map<String, List<AnswerElement>> exactMatchIndex = new HashMap<>();
    private final Map<String, List<AnswerElement>> keywordIndex = new HashMap<>();
    
    // Кэш значений для быстрого getRandomAnswer
    private List<AnswerElement> cachedValueList = new ArrayList<>();
    
    // Индекс для регулярных выражений
    private final Map<AnswerElement, Pattern> regexIndex = new HashMap<>();
    
    public AnswerDatabase(Context context) {
        this.fileManager = (context != null) ? new AndroidFileManager(context) : null;
        
        Log.i(TAG, "📱 === AnswerDatabase v2.1.1 ===");
        
        // Синхронная загрузка
        loadFromFile();
    }
    
    /**
     * Загрузка базы данных с построением индексов.
     */
    private void loadFromFile() {
        long startTime = System.currentTimeMillis();
        
        List<AnswerElement> loadedAnswers = (fileManager != null)
                ? fileManager.loadAnswerDatabase()
                : Collections.emptyList();
        
        synchronized (answers) {
            answers.clear();
            exactMatchIndex.clear();
            keywordIndex.clear();
            regexIndex.clear();
            
            for (AnswerElement answer : loadedAnswers) {
                answers.put(answer.getId(), answer);
                
                // Строим индексы для быстрого поиска
                buildIndexesForAnswer(answer);
            }
            // Обновляем кэш значений
            cachedValueList = new ArrayList<>(answers.values());
        }
        
        long loadTime = System.currentTimeMillis() - startTime;
        Log.i(TAG, "⚡ База данных загружена (" + answers.size() + " эл.) за " + loadTime + "мс");
    }
    
    private void buildIndexesForAnswer(AnswerElement answer) {
        String questionLower = normalizeText(answer.getQuestionText());
        
        // Поддержка масок со звездочкой
        if (questionLower.contains("*") && !questionLower.startsWith("regex:")) {
            String patternStr = questionLower
                .replace(".", "\\.")
                .replace("?", "\\?")
                .replace("+", "\\+")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("*", "(.*)");
            
            try {
                Pattern pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                regexIndex.put(answer, pattern);
            } catch (Exception e) {
                Log.e(TAG, "Ошибка компиляции маски: " + questionLower, e);
            }
            return;
        }

        // Проверка на Regex
        if (questionLower.startsWith("regex:")) {
            try {
                String patternStr = questionLower.substring(6).trim();
                Pattern pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
                regexIndex.put(answer, pattern);
            } catch (Exception e) {
                Log.e(TAG, "Ошибка компиляции regex: " + questionLower, e);
            }
            return;
        }
        
        // Индекс точного совпадения
        exactMatchIndex.computeIfAbsent(questionLower, k -> new ArrayList<>()).add(answer);

        // Индекс по ключевым словам
        String cleanText = questionLower.replaceAll("[^a-zA-Zа-яА-Я0-9 ]", " ");
        String[] words = cleanText.split("\\s+");
        
        for (String word : words) {
            if (word.length() >= 2) {
                keywordIndex.computeIfAbsent(word, k -> new ArrayList<>()).add(answer);
            }
        }
    }
    
    private static String normalizeText(String text) {
        return text != null ? text.toLowerCase().trim() : "";
    }
    
    /**
     * Интеллектуальный поиск ответов с учетом контекста и Regex.
     */
    public List<SearchResult> searchAnswers(String query, String userContext) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        String queryLower = query.toLowerCase().trim();
        String queryNormalized = queryLower.replaceAll("[^a-zа-я0-9 ]", " ").trim();
        String[] queryWords = queryNormalized.split("\\s+");
        
        // Временные списки для разных уровней уверенности
        List<SearchResult> exactContextual = new ArrayList<>();
        List<SearchResult> exactGeneral = new ArrayList<>();
        Set<AnswerElement> candidateSet = new HashSet<>();
        List<SearchResult> regexContextual = new ArrayList<>();
        List<SearchResult> regexGeneral = new ArrayList<>();

        synchronized (answers) {
            // 1. Быстрый поиск точного совпадения (O(1))
            List<AnswerElement> exacts = exactMatchIndex.get(queryLower);
            if (exacts != null) {
                for (AnswerElement e : exacts) {
                    SearchResult sr = new SearchResult(e);
                    if (e.getRequiredContext().equalsIgnoreCase(userContext) && !e.getRequiredContext().isEmpty()) {
                        exactContextual.add(sr);
                    } else if (e.getRequiredContext().isEmpty()) {
                        exactGeneral.add(sr);
                    }
                }
            }

            // 2. Сбор кандидатов по словам
            for (String word : queryWords) {
                if (word.length() >= 2) {
                    List<AnswerElement> matches = keywordIndex.get(word);
                    if (matches != null) {
                        candidateSet.addAll(matches);
                    }
                }
            }

            // 3. Regex поиск
            if (!regexIndex.isEmpty()) {
                for (Map.Entry<AnswerElement, Pattern> entry : regexIndex.entrySet()) {
                    Matcher matcher = entry.getValue().matcher(query);
                    if (matcher.find()) {
                        AnswerElement e = entry.getKey();
                        List<String> groups = new ArrayList<>();
                        for (int i = 0; i <= matcher.groupCount(); i++) {
                            groups.add(matcher.group(i));
                        }
                        
                        SearchResult sr = new SearchResult(e, groups);
                        if (e.getRequiredContext().equalsIgnoreCase(userContext) && !e.getRequiredContext().isEmpty()) {
                            regexContextual.add(sr);
                        } else if (e.getRequiredContext().isEmpty()) {
                            regexGeneral.add(sr);
                        }
                    }
                }
            }

            // Fallback: полный скан для маленьких баз
            if (exactContextual.isEmpty() && exactGeneral.isEmpty() && candidateSet.isEmpty() && answers.size() < 2000) {
                candidateSet.addAll(answers.values());
            }
        }

        // 4. Фильтрация и ранжирование
        List<SearchResult> highConfidenceContextual = new ArrayList<>();
        List<SearchResult> mediumConfidenceContextual = new ArrayList<>();
        List<SearchResult> highConfidenceGeneral = new ArrayList<>();
        List<SearchResult> mediumConfidenceGeneral = new ArrayList<>();

        for (AnswerElement element : candidateSet) {
            if (isAlreadyInResults(element, exactContextual, exactGeneral, regexContextual, regexGeneral)) continue;

            String questionNorm = element.getQuestionText().toLowerCase().replaceAll("[^a-zа-я0-9 ]", " ").trim();
            String[] questionWords = questionNorm.split("\\s+");
            
            int validQWords = 0;
            for (String w : questionWords) if (!w.isEmpty()) validQWords++;
            if (validQWords == 0) continue;

            int matchCount = 0;
            for (String qWord : questionWords) {
                if (qWord.isEmpty()) continue;
                for (String uWord : queryWords) {
                    if (uWord.isEmpty()) continue;
                    if (qWord.equals(uWord) || isFuzzyWordMatch(qWord, uWord)) {
                        matchCount++;
                        break;
                    }
                }
            }

            float matchRatio = (float) matchCount / validQWords;
            boolean isContextMatch = element.getRequiredContext().equalsIgnoreCase(userContext) && !element.getRequiredContext().isEmpty();
            boolean isNoContext = element.getRequiredContext().isEmpty();

            if (isContextMatch || isNoContext) {
                SearchResult sr = new SearchResult(element);
                if (matchRatio >= 0.95f) {
                    if (isContextMatch) highConfidenceContextual.add(sr);
                    else highConfidenceGeneral.add(sr);
                } else if (matchRatio >= 0.65f) {
                    if (isContextMatch) mediumConfidenceContextual.add(sr);
                    else mediumConfidenceGeneral.add(sr);
                }
            }
        }

        // Ранжирование по популярности
        Comparator<SearchResult> usageComparator = (a, b) -> Integer.compare(b.getAnswer().getUsageCount(), a.getAnswer().getUsageCount());
        exactContextual.sort(usageComparator);
        exactGeneral.sort(usageComparator);
        regexContextual.sort(usageComparator);
        regexGeneral.sort(usageComparator);
        highConfidenceContextual.sort(usageComparator);
        highConfidenceGeneral.sort(usageComparator);
        mediumConfidenceContextual.sort(usageComparator);
        mediumConfidenceGeneral.sort(usageComparator);

        List<SearchResult> finalResults = new ArrayList<>();
        finalResults.addAll(exactContextual);
        finalResults.addAll(exactGeneral);
        finalResults.addAll(regexContextual);
        finalResults.addAll(regexGeneral);
        finalResults.addAll(highConfidenceGeneral);
        finalResults.addAll(highConfidenceContextual);
        finalResults.addAll(mediumConfidenceContextual);
        finalResults.addAll(mediumConfidenceGeneral);
        
        return finalResults;
    }
    
    @SafeVarargs
    private boolean isAlreadyInResults(AnswerElement e, List<SearchResult>... lists) {
        for (List<SearchResult> list : lists) {
            for (SearchResult sr : list) {
                if (sr.getAnswer().getId() == e.getId()) return true;
            }
        }
        return false;
    }

    private static boolean isFuzzyWordMatch(String w1, String w2) {
        if (Math.abs(w1.length() - w2.length()) > 3) return false;
        if (w1.length() <= 3 || w2.length() <= 3) return w1.equals(w2);
        
        int minLen = Math.min(w1.length(), w2.length());
        int commonPrefix = 0;
        for (int i = 0; i < minLen; i++) {
            if (w1.charAt(i) == w2.charAt(i)) commonPrefix++;
            else break;
        }
        
        if (commonPrefix >= 4 && commonPrefix >= minLen - 2) return true;
        
        int dist = calculateLevenshteinDistance(w1, w2);
        return dist <= (minLen / 4) + 1;
    }

    private static int calculateLevenshteinDistance(String s1, String s2) {
        int m = s1.length(), n = s2.length();
        int[][] d = new int[m + 1][n + 1];
        for (int i = 0; i <= m; i++) d[i][0] = i;
        for (int j = 0; j <= n; j++) d[0][j] = j;
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
            }
        }
        return d[m][n];
    }

    public int getAnswersCount() {
        synchronized (answers) {
            return answers.size();
        }
    }
    
    public AnswerElement getRandomAnswer() {
        synchronized (answers) {
            if (cachedValueList.isEmpty()) return null;
            return cachedValueList.get(new Random().nextInt(cachedValueList.size()));
        }
    }
    
    public boolean reloadFromFile() {
        try {
            loadFromFile();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Ошибка перезагрузки базы данных", e);
            return false;
        }
    }
}