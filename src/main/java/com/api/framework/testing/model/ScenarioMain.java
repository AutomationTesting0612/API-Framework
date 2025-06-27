package com.api.framework.testing.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "API Framework Open API Swagger")
public class ScenarioMain {


    @Schema(description = "HTML report string", example = SwaggerDocValue.REPORT)
    private String report;

    @Schema(description = "List of data inputs", example = SwaggerDocValue.DATALIST)
    private List<DataList> data_list;

    public List<DataList> getData_list() {
        return data_list;
    }

    public void setData_list(List<DataList> data_list) {
        this.data_list = data_list;
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
                ", report='" + report + '\'' +
                ", data_list=" + data_list +
                '}';
    }
}