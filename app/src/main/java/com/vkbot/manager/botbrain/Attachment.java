package com.vkbot.manager.botbrain;

import androidx.annotation.NonNull;
import java.io.Serial;
import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Современная модель вложения (Java 21 Record).
 * Автоматически генерирует геттеры, equals, hashCode и toString.
 * Формат VK: type{owner_id}_{id}_{access_key}
 * Версия 2.1.3 - Record Migration & JVM 21.
 */
public record Attachment(
    String type,
    String id,
    String ownerId,
    String accessKey,
    String url,
    String title
) implements Serializable {
    
    @Serial
    private static final long serialVersionUID = 3L;
    
    private static final Pattern VK_STRING_PATTERN = Pattern.compile("([a-z]+)(-?\\d+)_(\\d+)(?:_(\\w+))?");

    public Attachment {
        type = type != null ? type : "";
        id = id != null ? id : "";
        ownerId = ownerId != null ? ownerId : "";
        accessKey = accessKey != null ? accessKey : "";
        url = url != null ? url : "";
        title = title != null ? title : "";
    }

    public String toVkString() {
        String base = type + ownerId + "_" + id;
        return accessKey.isEmpty() ? base : base + "_" + accessKey;
    }
    
    public static Attachment parse(String vkString) {
        if (vkString == null || vkString.isEmpty()) return null;
        
        Matcher matcher = VK_STRING_PATTERN.matcher(vkString.trim());
        if (matcher.find()) {
            return new Attachment(
                matcher.group(1), // type
                matcher.group(3), // id
                matcher.group(2), // ownerId
                matcher.group(4), // accessKey
                "", // url
                ""  // title
            );
        }
        return null;
    }
    
    @NonNull
    @Override
    public String toString() {
        return toVkString();
    }
}