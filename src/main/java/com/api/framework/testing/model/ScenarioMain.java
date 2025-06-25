package com.api.framework.testing.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ScenarioMain {

    private String value;

    private String report;


    private List<DataList> data_list;

    public List<DataList> getData_list() {
        return data_list;
    }

    public void setData_list(List<DataList> data_list) {
        this.data_list = data_list;
    }


    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getReport() {
        return report;
    }

    public void setReport(String report) {
        this.report = report;
    }

    @Override
    public String toString() {
        return "ScenarioMain{" +
                ", value='" + value + '\'' +
                ", report='" + report + '\'' +
                ", data_list=" + data_list +
                '}';
    }
}