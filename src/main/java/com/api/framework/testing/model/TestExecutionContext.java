package com.api.framework.testing.model;

import java.util.HashMap;
import java.util.Map;

public class TestExecutionContext {

    private static final ThreadLocal<Map<String, Object>> context = ThreadLocal.withInitial(HashMap::new);

    public static void put(String key, Object value) {
        context.get().put(key, value);
    }

    public static Object get(String key) {
        return context.get().get(key);
    }

    public static Map<String, Object> getAll() {
        return context.get();
    }

    public static void clear() {
        context.remove();
    }
}
