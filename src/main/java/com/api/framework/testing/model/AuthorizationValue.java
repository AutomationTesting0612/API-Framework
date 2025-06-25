package com.api.framework.testing.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthorizationValue {
    public String getBasicToken() {
        return basicToken;
    }

    public void setBasicToken(String basicToken) {
        this.basicToken = basicToken;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    private String basicToken;
    private String username;
    private String password;

    // Getters and Setters
}