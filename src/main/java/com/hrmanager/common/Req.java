package com.hrmanager.common;

import java.util.Map;

public final class Req {

    private Req() {
    }

    public static String str(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? null : String.valueOf(value).trim();
    }

    public static String str(Map<String, Object> body, String key, String defaultValue) {
        String value = str(body, key);
        return value == null || value.isEmpty() ? defaultValue : value;
    }

    public static Long lng(Map<String, Object> body, String key) {
        String value = str(body, key);
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            throw new BusinessException(key + "格式错误");
        }
    }

    public static Integer integer(Map<String, Object> body, String key) {
        String value = str(body, key);
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ex) {
            throw new BusinessException(key + "格式错误");
        }
    }

    public static boolean bool(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return value != null && "true".equalsIgnoreCase(String.valueOf(value));
    }
}
