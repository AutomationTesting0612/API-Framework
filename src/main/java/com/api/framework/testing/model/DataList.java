package com.api.framework.testing.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DataList {

    @JsonProperty("baseUrl")
    private String base_url;

    @JsonProperty("endpoint")
    private String end_point;

    @JsonProperty("mappingType")
    private String mapping_type;

    private Map<String, String> header;
    private ScenarioModel scenario;
    private List<DataModel> data_model;

    public String getBase_url() {
        return base_url;
    }

    public void setBase_url(String base_url) {
        this.base_url = base_url;
    }

    public String getMapping_type() {
        return mapping_type;
    }

    public void setMapping_type(String mapping_type) {
        this.mapping_type = mapping_type;
    }

    public String getEndPoint() {
        return end_point;
    }

    public void setEndPoint(String endPoint) {
        this.end_point = endPoint;
    }

    public Map<String, String> getHeader() {
        return header;
    }

    public void setHeader(Map<String, String> header) {
        this.header = header;
    }

    public ScenarioModel getScenario() {
        return scenario;
    }

    public void setScenario(ScenarioModel scenario) {
        this.scenario = scenario;
    }

    public List<DataModel> getData_model() {
        return data_model;
    }

    public void setData_model(List<DataModel> data_model) {
        this.data_model = data_model;
    }
}