package com.api.framework.testing.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Authorization {
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public AuthorizationValue getValue() {
        return value;
    }

    public void setValue(AuthorizationValue value) {
        this.value = value;
    }

    private String type;
    private AuthorizationValue value;

    // Getters and Setters
}