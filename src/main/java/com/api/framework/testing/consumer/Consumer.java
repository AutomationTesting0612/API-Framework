package com.api.framework.testing.consumer;


import com.api.framework.testing.model.DataList;
import com.api.framework.testing.model.DataSet;
import com.api.framework.testing.model.ScenarioMain;
import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.reporter.ExtentHtmlReporter;
import com.aventstack.extentreports.reporter.configuration.Theme;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class Consumer {

    private final List<ScenarioMain> receivedMessages = new ArrayList<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    private ExtentReports extent;
    private ExtentTest test;
    private ThreadLocal<ExtentTest> testThread = new ThreadLocal<>();

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
                allScenarios = objectMapper.readValue(trimmed, new TypeReference<List<ScenarioMain>>() {
                });
            } else {
                ScenarioMain single = objectMapper.readValue(trimmed, ScenarioMain.class);
                allScenarios = Collections.singletonList(single);
            }
//            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            for (ScenarioMain scenario : allScenarios) {
                if (scenario.getData_list() == null || scenario.getData_list().isEmpty()) {
                    continue;
                }

                doHttpCall(scenario);

            }
            ack.acknowledge();
            synchronized (this) {
                extent.flush();
            } // Flush only once after all scenarios
            generateAndSendReport(allScenarios); // Pass list of all scenarios


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

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
//                synchronized (this) {
//            test = extent.createTest("Feature: " + featureName)
//                    .assignCategory("API Testing")
//                    .assignAuthor("Automation Team");
//                }

            List<DataSet> datasets = dataList.getScenario().getDatasets();
            for (DataSet dataset : datasets) {
                ExtentTest test = extent.createTest("Feature: " + featureName + " [" + operationType + "]")
                        .assignCategory("API Testing")
                        .assignAuthor("Automation Team");
                testThread.set(test);

                try {
                    Map<String, Object> requestBody = dataset.getRequest_body() != null && !dataset.getRequest_body().isEmpty()
                            ? dataset.getRequest_body()
                            : null;
                    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
                    String expectedStatus = dataset.getDesired_status() != null ? dataset.getDesired_status() : "";
                    String endpoint = dataList.getBase_url() + dataList.getEndPoint();

                    if (dataset.getParams() != null) {
                        endpoint = buildUrlWithParams(endpoint, dataset.getParams());
                    }

                    test.info("📌 Endpoint: " + endpoint);
                    test.info("🔄 HTTP Method: " + method);
                    test.info("📦 Request Body: " + (requestBody != null ? requestBody : "NA"));

                    ResponseEntity<String> response = restTemplate.exchange(endpoint, method, entity, String.class);

                    JsonNode expectedRoot = dataset.getDesired_outcome();
                    JsonNode actualNode = objectMapper.readTree(response.getBody());

                    boolean isEqual = true;
                    if (expectedRoot.isObject()) {
                        isEqual = compareOnlyExpectedFields(expectedRoot, actualNode);
                    } else if (expectedRoot.isArray()) {
                        for (JsonNode expectedNode : expectedRoot) {
                            isEqual = compareOnlyExpectedFields(expectedNode, actualNode);
                            if (!isEqual) break;
                        }
                    }

                    if (String.valueOf(response.getStatusCodeValue()).equalsIgnoreCase(expectedStatus)) {
                        test.pass("✅ Status Code Matched: " + expectedStatus);
                    } else {
                        test.fail("❌ Status Code Mismatch. Expected: " + expectedStatus + ", Actual: " + response.getStatusCodeValue());
                    }

                    if (Boolean.TRUE.equals(isEqual)) {
                        test.pass("✅ Response Body Matched: " + response.getBody());
                    } else {
                        test.fail("❌ Response Body Mismatches: <br>" + mismatches);
                    }

                } catch (Exception e) {
                    test.fail("❌ API call failed: " + e.getMessage());
                }
            }
        }
    }


    private void generateAndSendReport(List<ScenarioMain> scenarios) {
        try {
            String htmlContent = new String(Files.readAllBytes(Paths.get("APIReport.html")), StandardCharsets.UTF_8);

            for (ScenarioMain scenario : scenarios) {
                scenario.setReport(htmlContent);  // Attach same report to each scenario
            }

        } catch (IOException e) {
            System.err.println("❌ Failed to read Extent Report: " + e.getMessage());
        }

        System.out.println("📁 Report will be generated at: " + new File("APIReport.html").getAbsolutePath());

    }

    private void setupExtentReports() {
        try {
            String reportPath = System.getProperty("user.dir") + "/APIReport.html";
            File reportFile = new File(reportPath);

            if (reportFile.exists()) {
                if (!reportFile.delete()) {
                    System.err.println("⚠️ Failed to delete existing report file.");
                }
            }
            ExtentHtmlReporter htmlReporter = new ExtentHtmlReporter(reportPath);

            // Beautify the report
            htmlReporter.config().setDocumentTitle("🚀 API Test Report");
            htmlReporter.config().setReportName("🧪 API Message Processing Suite");
            htmlReporter.config().setTheme(Theme.DARK); // Only STANDARD and DARK available in 3.1.3
            htmlReporter.config().setEncoding("utf-8");
            htmlReporter.config().setChartVisibilityOnOpen(true);
            htmlReporter.config().setTestViewChartLocation(
                    com.aventstack.extentreports.reporter.configuration.ChartLocation.TOP
            );

            extent = new ExtentReports();
            extent.attachReporter(htmlReporter);

            // System info
            extent.setSystemInfo("🌍 Environment", "Local");
            extent.setSystemInfo("👨‍💻 Tester", "Automation Team");
            extent.setSystemInfo("📅 Date", java.time.LocalDate.now().toString());
            extent.setSystemInfo("⏱ Time", java.time.LocalTime.now().toString());

            System.out.println("📄 Report path set to: " + reportPath);
           } catch (Exception e) {
            System.err.println("❌ Failed to initialize ExtentReports: " + e.getMessage());
            e.printStackTrace();
        }
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
        ExtentTest test = testThread.get();
        mismatches.forEach(m -> test.warning("Mismatch: " + m));
        return mismatches.isEmpty();
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

    @PostConstruct
    public void init() {
        setupExtentReports();
        customizeRestTemplate();


    }

    private void customizeRestTemplate() {
        restTemplate.setErrorHandler(new ResponseErrorHandler() {
            @Override public boolean hasError(ClientHttpResponse response) { return false; }
            @Override public void handleError(ClientHttpResponse response) {}
        });
    }

}
