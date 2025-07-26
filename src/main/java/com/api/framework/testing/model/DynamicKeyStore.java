package com.api.framework.testing.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DynamicKeyStore {

    private List<String> keys;

    // Optional: to store actual values at runtime
    private Map<String, Object> keyValueMap = new HashMap<>();

    public List<String> getKeys() {
        return keys;
    }

    public void setKeys(List<String> keys) {
        this.keys = keys;
    }

    public Map<String, Object> getKeyValueMap() {
        return keyValueMap;
    }

    public void addValue(String key, Object value) {
        this.keyValueMap.put(key, value);
    }

    public Object getValue(String key) {
        return this.keyValueMap.get(key);
    }

    @Override
    public String toString() {
        return "DynamicKeyStore{" +
                "keys=" + keys +
                ", keyValueMap=" + keyValueMap +
                '}';
    }
}
