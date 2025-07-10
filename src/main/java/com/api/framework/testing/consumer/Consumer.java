package com.api.framework.testing.consumer;


import com.api.framework.testing.model.DataList;
import com.api.framework.testing.model.DataSet;
import com.api.framework.testing.model.RecStatus;
import com.api.framework.testing.model.ScenarioMain;
import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.reporter.ExtentHtmlReporter;
import com.aventstack.extentreports.reporter.configuration.Theme;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class Consumer {

    private final List<ScenarioMain> receivedMessages = new ArrayList<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    private ExtentReports extent;
    private ExtentTest test;

    private List<ScenarioMain> scenario;
    private String featureName = "Unknown Feature";
    private String operationType = null;
    private Map<String, String> header;
    List<String> mismatches;


    @Value("${kafka.source-topic}")
    private String sourceTopic;

    @Value("${kafka.destination-topic}")
    private String destinationTopic;

    @Value("${kafka.group.id}")
    private String groupId;

    @Value("${thread.count}")
    private int threadCount;

    @KafkaListener(topics = "${kafka.source-topic}", groupId = "${kafka.group.id}", containerFactory = "manualAckListenerContainerFactory")
    public void consumeMessage(List<String> message, Acknowledgment ack) throws JsonProcessingException {
        System.out.println("🔹 Received message from Kafka: " + message);
        List<ScenarioMain> allScenarios;

        try {
            String trimmed = message.stream()
                    .collect(Collectors.joining())
                    .trim();
            if (trimmed.startsWith("[")) {
                allScenarios = objectMapper.readValue(trimmed, new TypeReference<List<ScenarioMain>>() {});
            } else {
                ScenarioMain single = objectMapper.readValue(trimmed, ScenarioMain.class);
                allScenarios = Collections.singletonList(single);
            }

            setupExtentReports(); // Initialize only once
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            for (ScenarioMain scenario : allScenarios) {
                if (scenario.getData_list() == null || scenario.getData_list().isEmpty()) {
                    continue;
                }
                executor.submit(() -> {
                    try {
                        doHttpCall(scenario);
                    } catch (JsonProcessingException e) {
                        e.printStackTrace();
                    }
                });
            }
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.MINUTES);
            extent.flush(); // Flush only once after all scenarios
            generateAndSendReport(allScenarios); // Pass list of all scenarios

            ack.acknowledge();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


//    @KafkaListener(topics = "${kafka.source-topic}", groupId = "${kafka.group.id}", containerFactory = "manualAckListenerContainerFactory")
//    public void consumeMessage(String message, Acknowledgment ack) throws JsonProcessingException {
//        System.out.println("🔹 Received message from Kafka: " + message);
//
//        try {
//            String trimmed = message.trim();
//            if (trimmed.startsWith("[")) {
//                scenario = objectMapper.readValue(
//                        trimmed,
//                        new TypeReference<List<ScenarioMain>>() {}
//                );
//            } else {
//                ScenarioMain single = objectMapper.readValue(trimmed, ScenarioMain.class);
//                scenario = Collections.singletonList(single);
//            }
//            ack.acknowledge();
////            receivedMessages.add(scenario);
//            setupExtentReports();
//            for (ScenarioMain scenario : scenario) {
//                if (scenario.getData_list() == null || scenario.getData_list().isEmpty()) {
//                    continue;  // skip empty payloads
//                }
//
//                doHttpCall(scenario);
//            }
//            ack.acknowledge();
//
//        }catch (Exception e) {
//            e.printStackTrace();
//        }
//    }

    private void doHttpCall(ScenarioMain scenario) throws JsonProcessingException {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "PostmanRuntime/7.43.3");


            for (DataList dataList : scenario.getData_list()) {

                HttpMethod method = HttpMethod.valueOf(dataList.getMapping_type());
                featureName = dataList.getScenario().getName();
                operationType = dataList.getMapping_type();
                header = dataList.getHeader();

                if (header != null) {
                    header.forEach(headers::set);
                }
                synchronized (this) {
                    test = extent.createTest("Feature: " + featureName)
                            .assignCategory("API Testing")
                            .assignAuthor("Automation Team");
                }

                List<DataSet> datasets = dataList.getScenario().getDatasets();
                for (DataSet dataset : datasets) {

                    Map<String, Object> requestBody = dataset.getRequest_body() != null && !dataset.getRequest_body().isEmpty()
                            ? dataset.getRequest_body()
                            : null;
                    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
                    String expectedStatus = dataset.getDesired_status() != null ? dataset.getDesired_status() : "";
                    String endpoint = dataList.getBase_url() + dataList.getEndPoint();
                    if (dataset.getParams() != null) {
                        endpoint = buildUrlWithParams(endpoint, dataset.getParams());
                    }
                    synchronized (this) {
                        test.info("📌 Endpoint: " + endpoint);
                        test.info("🔄 HTTP Method: " + method);
                        if (requestBody != null) {
                            test.info("The Request Body is " + requestBody);
                        } else {
                            test.info("The Request Body is NA");
                        }
                    }
                    try {
                        System.out.println("entity headers = " + entity.getHeaders());
                        ResponseEntity<String> response = restTemplate.exchange(endpoint, method, entity, String.class);

                        System.out.println(response);
                        JsonNode expectedRoot = objectMapper.readTree(dataset.getDesired_outcome());
                        JsonNode actualNode = objectMapper.readTree(response.getBody());

                        boolean isEqual = true;
                        // If it's a JSON object
                        if (expectedRoot.isObject()) {
                            isEqual = compareOnlyExpectedFields(expectedRoot, actualNode);
                        }
                        // If it's a JSON array
                        else if (expectedRoot.isArray()) {
                            for (JsonNode expectedNode : expectedRoot) {
                                isEqual = compareOnlyExpectedFields(expectedNode, actualNode);
                                if (!isEqual) break; // Optionally break on first failure
                            }
                        }
                        synchronized (this) {
                            String expectedStatusCode = dataset.getDesired_status();
                            expectedStatusCode = expectedStatusCode.replaceAll("\\s+", "");
                            System.out.println(expectedStatusCode);
                            if (String.valueOf(response.getStatusCodeValue()).equalsIgnoreCase(expectedStatusCode)) {
                                test.pass("✅ The Expected Response code is : " + expectedStatusCode + "<br>The actual response code is " + response.getStatusCodeValue());

                            } else {
                                test.fail("❌ Response Body / Status Code Mismatched " + expectedStatus + "<br>but got " + response.getStatusCodeValue());
                            }
                            if (Boolean.TRUE.equals(isEqual)) {
                                test.pass("✅ The Expected Response Body is : " + dataset.getDesired_outcome() + "<br>The actual response Body is " + response.getBody());
                            } else {
                                test.fail("❌  The Mismatched field's value in Expected and Actual Response Body are : <br>" + mismatches);
                            }
                        }
                    } catch (Exception e) {
                        test.fail("❌ API call failed for request: " + requestBody + " | Error: " + e.getMessage());
                    }
                }
            }

//        extent.flush();
//        generateAndSendReport(scenario);

    }


    private void generateAndSendReport(List<ScenarioMain> scenarios) {
        try {
            String htmlContent = new String(Files.readAllBytes(Paths.get("APIReport.html")), StandardCharsets.UTF_8);

            for (ScenarioMain scenario : scenarios) {
                scenario.setReport(htmlContent);  // Attach same report to each scenario
            }

            // Optional: if you want to send all scenarios together as JSON to Kafka
            // String finalMessage = objectMapper.writeValueAsString(scenarios);
            // kafkaTemplate.send(destinationTopic, finalMessage);

        } catch (IOException e) {
            System.err.println("❌ Failed to read Extent Report: " + e.getMessage());
        }

        System.out.println("📁 Report will be generated at: " + new File("APIReport.html").getAbsolutePath());

        File file = new File("APIReport.html");
//        if (!file.delete()) {
//            System.err.println("⚠️ Failed to delete APIReport.html");
//        }
    }

    private void setupExtentReports() {
        ExtentHtmlReporter htmlReporter = new ExtentHtmlReporter(new File("APIReport.html"));
        htmlReporter.config().setDocumentTitle("API Test Report");
        htmlReporter.config().setReportName("API Message Processing");
        htmlReporter.config().setTheme(Theme.STANDARD);

        extent = new ExtentReports();
        extent.attachReporter(htmlReporter);
        extent.setSystemInfo("Environment", "Local");
        extent.setSystemInfo("Tester", "Automation Team");
    }
    public String buildUrlWithParams(String baseUrl, Map<String, String> params) {
        StringBuilder urlBuilder = new StringBuilder(baseUrl);

        boolean firstParam = !baseUrl.contains("?");

        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (value != null && !value.isEmpty()) {
                if (firstParam) {
                    urlBuilder.append("?");
                    firstParam = false;
                } else {
                    urlBuilder.append("&");
                }

                urlBuilder.append(URLEncoder.encode(key, StandardCharsets.UTF_8));
                urlBuilder.append("=");
                urlBuilder.append(URLEncoder.encode(value, StandardCharsets.UTF_8));
            }
        }

        return urlBuilder.toString();
    }

    private boolean compareOnlyExpectedFields(JsonNode expectedNode, JsonNode actualNode) {
        mismatches = compareExpectedFieldsDeeply(expectedNode, actualNode, "");
        return mismatches.isEmpty(); // Return true only if no mismatches
    }

    private List<String> compareExpectedFieldsDeeply(JsonNode expected, JsonNode actual, String path) {
        mismatches = new ArrayList<>();

        Iterator<Map.Entry<String, JsonNode>> expectedFields = expected.fields();
        while (expectedFields.hasNext()) {
            Map.Entry<String, JsonNode> field = expectedFields.next();
            String fieldName = field.getKey();
            JsonNode expectedValue = field.getValue();
            JsonNode actualValue = actual.get(fieldName);

            String currentPath = path.isEmpty() ? fieldName : path + "." + fieldName;

            if (actualValue == null) {
                mismatches.add("Missing field at: " + currentPath);
            } else if (expectedValue.isObject() && actualValue.isObject()) {
                // Recursive check for nested objects
                mismatches.addAll(compareExpectedFieldsDeeply(expectedValue, actualValue, currentPath));
            } else if (expectedValue.isArray() && actualValue.isArray()) {
                int size = Math.min(expectedValue.size(), actualValue.size());
                for (int i = 0; i < size; i++) {
                    JsonNode expectedElement = expectedValue.get(i);
                    JsonNode actualElement = actualValue.get(i);
                    mismatches.addAll(compareExpectedFieldsDeeply(expectedElement, actualElement, currentPath + "[" + i + "]"));
                }
                if (expectedValue.size() != actualValue.size()) {
                    mismatches.add("Array size mismatch at " + currentPath + ": expected=" +
                            expectedValue.size() + ", actual=" + actualValue.size());
                }
            } else if (!expectedValue.equals(actualValue)) {
                mismatches.add("Mismatch at " + currentPath + ": expected=" + expectedValue + ", actual=" + actualValue);
            }
        }

        return mismatches;
    }


}
