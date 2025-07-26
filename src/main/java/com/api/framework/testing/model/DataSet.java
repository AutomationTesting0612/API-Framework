package com.api.framework.testing.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DataSet {

    private Map<String, Object> request_body;
    private JsonNode desired_outcome;
    private String desired_status;
    private Authorization authorization;
    private Map<String, Object> path_variable;
    private Map<String, Object> query_param;
    private DynamicKeyStore dynamic_keystore;

    public Map<String, Object> getQuery_param() {
        return query_param;
    }

    public void setQuery_param(Map<String, Object> query_param) {
        this.query_param = query_param;
    }

    public Map<String, Object> getPath_variable() {
        return path_variable;
    }

    public void setPath_variable(Map<String, Object> path_variable) {
        this.path_variable = path_variable;
    }

    public DynamicKeyStore getDynamic_keystore() {
        return dynamic_keystore;
    }

    public void setDynamic_keystore(DynamicKeyStore dynamic_keystore) {
        this.dynamic_keystore = dynamic_keystore;
    }


    public Map<String, Object> getRequest_body() {
        return request_body;
    }

    public void setRequest_body(Map<String, Object> request_body) {
        this.request_body = request_body;
    }


    public JsonNode getDesired_outcome() {
        return desired_outcome;
    }

    public void setDesired_outcome(JsonNode desired_outcome) {
        this.desired_outcome = desired_outcome;
    }

    public String getDesired_status() {
        return desired_status;
    }

    public void setDesired_status(String desired_status) {
        this.desired_status = desired_status;
    }

    public Authorization getAuthorization() {
        return authorization;
    }

    public void setAuthorization(Authorization authorization) {
        this.authorization = authorization;
    }

}
