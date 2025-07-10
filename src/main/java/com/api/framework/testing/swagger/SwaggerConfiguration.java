package com.api.framework.testing.swagger;

import com.api.framework.testing.model.DataList;
import com.api.framework.testing.model.DataSet;
import com.api.framework.testing.model.ScenarioMain;
import com.api.framework.testing.model.ScenarioModel;
import io.swagger.v3.oas.models.OpenAPI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SwaggerConfiguration {

    public List<ScenarioMain> generateScenarios(OpenAPI openAPI) {
        List<ScenarioMain> scenarios = new ArrayList<>();

        openAPI.getPaths().forEach((path, pathItem) -> {
            pathItem.readOperationsMap().forEach((httpMethod, operation) -> {

                ScenarioMain scenarioMain = new ScenarioMain();
                DataList dataList = new DataList();

                dataList.setBase_url("http://localhost:8096");
                dataList.setEndPoint(path.replaceFirst("/", ""));
                dataList.setMapping_type(httpMethod.name());

                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json");
                dataList.setHeader(headers);

                ScenarioModel scenario = new ScenarioModel();
                scenario.setName(operation.getSummary());
                scenario.setDescription(operation.getDescription());

                DataSet dataset = new DataSet();
                dataset.setDesired_status("200");
                dataset.setDesired_outcome("{\"message\": \"Success\"}");
                dataset.setRequest_body(Map.of("sample", "value"));

                scenario.setDatasets(List.of(dataset));
                dataList.setScenario(scenario);
                scenarioMain.setData_list(List.of(dataList));

                scenarios.add(scenarioMain);
            });
        });

        return scenarios;
    }
}
