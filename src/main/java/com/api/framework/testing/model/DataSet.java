package com.api.framework.testing.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DataSet {

    private Map<String, Object> request_body;
    private String desired_outcome_format;
    private String desired_outcome;
    private String desired_status;

    private Authorization authorization;
    private List<AcceptanceCriteria> acceptance_criteria;
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

    public String getDesired_outcome_format() {
        return desired_outcome_format;
    }

    public void setDesired_outcome_format(String desired_outcome_format) {
        this.desired_outcome_format = desired_outcome_format;
    }

    public String getDesired_outcome() {
        return desired_outcome;
    }

    public void setDesired_outcome(String desired_outcome) {
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

    public List<AcceptanceCriteria> getAcceptance_criteria() {
        return acceptance_criteria;
    }

    public void setAcceptance_criteria(List<AcceptanceCriteria> acceptance_criteria) {
        this.acceptance_criteria = acceptance_criteria;
    }
}
