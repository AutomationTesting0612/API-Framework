package com.api.framework.testing.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ScenarioModel {

    private String name;

    private String description;

    private String code;

    private List<DataSet> datasets;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public List<DataSet> getDatasets() {
        return datasets;
    }

    public void setDatasets(List<DataSet> datasets) {
        this.datasets = datasets;
    }
}