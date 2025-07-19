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
    private Map<String,String> params;


    public Map<String, String> getParams() {
        return params;
    }

    public void setParams(Map<String, String> params) {
        this.params = params;
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
