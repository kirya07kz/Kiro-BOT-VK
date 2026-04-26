package com.vkbot.manager.botbrain;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Менеджер файлов для работы с базой данных.
 * Версия 2.1.8 - Fix Initialization & Locale Warnings.
 */
@SuppressWarnings("unused")
public class AndroidFileManager {

    private static final String TAG = "FileManager";
    private static final String FOLDER_NAME = "KirDev_BOT";
    private static final String FILE_NAME = "answer.bin";
    private static final String ASSET_NAME = "answer.bin";

    private final File databaseFile;
    private final Context context;

    public AndroidFileManager(@NonNull Context context) {
        this.context = context;
        
        // Инициализируем папку по умолчанию (приватная папка приложения), 
        // чтобы переменная всегда имела значение.
        File appDir = context.getExternalFilesDir(null);
        File folder = (appDir != null) ? new File(appDir, FOLDER_NAME) : new File(context.getFilesDir(), FOLDER_NAME);
        boolean accessible = false;

        // Попытка использовать общее хранилище (Legacy)
        try {
            File root = Environment.getExternalStorageDirectory();
            File legacyFolder = new File(root, FOLDER_NAME);
            if (legacyFolder.exists() ? legacyFolder.canWrite() : legacyFolder.mkdirs()) {
                folder = legacyFolder;
                accessible = true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Ошибка доступа к внешнему хранилищу (Legacy)", e);
        }

        // Если Legacy не сработал, проверяем/создаем нашу основную папку
        if (!accessible) {
            if (!folder.exists() && !folder.mkdirs()) {
                Log.e(TAG, "Критическая ошибка: не удалось создать папку " + folder.getAbsolutePath());
            }
        }
        
        this.databaseFile = new File(folder, FILE_NAME);

        // Копируем базу из assets только если файла НЕТ
        if (!databaseFile.exists()) {
            copyDatabaseFromAssets();
        }
    }

    private void copyDatabaseFromAssets() {
        try (InputStream in = context.getAssets().open(ASSET_NAME);
             FileOutputStream out = new FileOutputStream(databaseFile)) {
            
            byte[] buffer = new byte[8192];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
            Log.i(TAG, "✅ База успешно скопирована из assets");
            
        } catch (IOException e) {
            Log.e(TAG, "❌ Ошибка копирования базы", e);
        }
    }

    @Nullable
    public String exportDatabase() {
        if (!databaseFile.exists() || databaseFile.length() == 0) return null;

        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
            String timestamp = sdf.format(new Date());
            String exportFileName = "answer_backup_" + timestamp + ".bin";
            
            File parent = databaseFile.getParentFile();
            if (parent == null) return null;
            
            File exportFile = new File(parent, exportFileName);
            
            try (FileInputStream in = new FileInputStream(databaseFile);
                 FileOutputStream out = new FileOutputStream(exportFile)) {
                
                byte[] buffer = new byte[8192];
                int length;
                while ((length = in.read(buffer)) > 0) {
                    out.write(buffer, 0, length);
                }
                return exportFile.getAbsolutePath();
            }
            
        } catch (IOException e) {
            Log.e(TAG, "❌ Ошибка экспорта", e);
            return null;
        }
    }

    @NonNull
    public List<AnswerElement> loadAnswerDatabase() {
        List<AnswerElement> answers = new ArrayList<>();
        Set<String> uniqueEntries = new HashSet<>();

        if (!databaseFile.exists()) return answers;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(databaseFile), StandardCharsets.UTF_8))) {
            
            String line;
            long id = 1;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split("\\|", 7);
                if (parts.length >= 2) {
                    String question = unescapeText(parts[0].trim());
                    String answer = unescapeText(parts[1].trim());
                    String attachmentsStr = (parts.length >= 3) ? parts[2].trim() : "";
                    
                    int repetitionLimit = (parts.length >= 4) ? tryParseInt(parts[3]) : 0;
                    int usageCount = (parts.length >= 5) ? tryParseInt(parts[4]) : 0;
                    String reqCtx = (parts.length >= 6) ? parts[5].trim() : "";
                    String resCtx = (parts.length >= 7) ? parts[6].trim() : "";
                    
                    String uniqueKey = question.toLowerCase() + "|" + answer.toLowerCase() + "|" + attachmentsStr;
                    if (!uniqueEntries.add(uniqueKey)) continue;

                    List<Attachment> attachments = new ArrayList<>();
                    if (!attachmentsStr.isEmpty()) {
                        for (String attStr : attachmentsStr.split(",")) {
                            Attachment att = Attachment.parse(attStr.trim());
                            if (att != null) attachments.add(att);
                        }
                    }

                    answers.add(new AnswerElement(id++, question, answer, attachments, new Date(), usageCount, repetitionLimit, reqCtx, resCtx));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Ошибка чтения БД", e);
        }
        return answers;
    }

    public boolean saveAnswerDatabase(@NonNull List<AnswerElement> answers) {
        File parent = databaseFile.getParentFile();
        if (parent == null) return false;
        
        File tempFile = new File(parent, FILE_NAME + ".tmp");
        
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(tempFile), StandardCharsets.UTF_8))) {
            
            writer.write("# База ответов Kiro Bot Vk (UTF-8)");
            writer.newLine();
            writer.write("# Формат: ВОПРОС|ОТВЕТ|ВЛОЖЕНИЯ|ЛИМИТ|СЧЕТЧИК|REQ_CTX|RES_CTX");
            writer.newLine();
            
            for (AnswerElement element : answers) {
                String line = String.format(Locale.ROOT, "%s|%s|%s|%d|%d|%s|%s",
                        escapeText(element.getQuestionText()),
                        escapeText(element.getAnswerText()),
                        serializeAttachments(element.getAnswerAttachments()),
                        element.getRepetitionLimit(),
                        element.getUsageCount(),
                        element.getRequiredContext(),
                        element.getResultContext());
                writer.write(line);
                writer.newLine();
            }
            
            return atomicReplace(tempFile, databaseFile);

        } catch (Exception e) {
            Log.e(TAG, "❌ Критическая ошибка сохранения", e);
            return false;
        }
    }

    private boolean atomicReplace(File src, File dest) {
        if (src.renameTo(dest)) return true;
        if (dest.exists() && !dest.delete()) return false;
        return src.renameTo(dest);
    }

    private String serializeAttachments(List<Attachment> attachments) {
        if (attachments == null || attachments.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < attachments.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(attachments.get(i).toVkString());
        }
        return sb.toString();
    }

    private int tryParseInt(String val) {
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String escapeText(String text) {
        return (text == null) ? "" : text.replace("\n", "<br>").replace("|", "<pipe>");
    }

    private String unescapeText(String text) {
        return (text == null) ? "" : text.replace("<br>", "\n").replace("<pipe>", "|").replace("\\n", "\n");
    }

    @NonNull
    public String getAnswerFilePath() {
        return databaseFile.getAbsolutePath();
    }

    public boolean isFileExists() {
        return databaseFile.exists();
    }
}
