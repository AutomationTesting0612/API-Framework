package com.api.framework.testing.consumer;


import com.api.framework.testing.model.*;
import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.reporter.ExtentHtmlReporter;
import com.aventstack.extentreports.reporter.configuration.Theme;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

            List<DataSet> datasets = dataList.getScenario().getDatasets();
            for (DataSet dataset : datasets) {
                ExtentTest test = extent.createTest("Feature: " + featureName + " [" + operationType + "]")
                        .assignCategory("API Testing")
                        .assignAuthor("Automation Team");
                testThread.set(test);

                try {
                    Map<String, Object> requestBody;
                    if (!operationType.equals("POST")) {

                         requestBody = dataset.getRequest_body() != null && !dataset.getRequest_body().isEmpty()
                                ? (Map<String, Object>) resolvePlaceholdersInObject(dataset.getRequest_body())
                                : null;
                    } else {
                         requestBody = dataset.getRequest_body() != null && !dataset.getRequest_body().isEmpty()
                                ? dataset.getRequest_body()
                                : null;
                    }
                    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
                    String expectedStatus = dataset.getDesired_status() != null ? dataset.getDesired_status() : "";
                    String endpoint = dataList.getBase_url() + dataList.getEndPoint();
                    if (!operationType.equals("POST")) {

                    if (dataset.getPath_variable() != null && dataset.getQuery_param() != null) {
                        endpoint = resolveEndpointWithQueryAndPathVariable(endpoint, dataset); // Original static params
                    } else if (dataset.getPath_variable() != null) {
                        endpoint = resolveEndpointWithPathVariable(endpoint, dataset);
                    } else if(dataset.getQuery_param() != null) {
                        endpoint = resolveEndpointWithQueryParam(endpoint, dataset);
                    }
                    }

                    test.info("📌 Endpoint: " + endpoint);
                    test.info("🔄 HTTP Method: " + method);
                    test.info("📦 Request Body: " + (requestBody != null ? requestBody : "NA"));

                    ResponseEntity<String> response = restTemplate.exchange(endpoint, method, entity, String.class);

                    JsonNode expectedRoot = dataset.getDesired_outcome();
                    JsonNode actualNode = objectMapper.readTree(response.getBody());
                    if (operationType.equals("POST")) {
                        populateDynamicKeysFromResponse(dataset, actualNode);
                    }
                    JsonNode finalExpectedResponse = extractActualData(actualNode, expectedRoot);
                    boolean isEqual = true;
                    if (expectedRoot.isObject()) {
                        isEqual = compareOnlyExpectedFields(finalExpectedResponse, actualNode);
                    } else if (expectedRoot.isArray()) {
                        for (JsonNode expectedNode : expectedRoot) {
                            isEqual = compareOnlyExpectedFields(finalExpectedResponse, actualNode);
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

    public String resolveEndpointWithQueryAndPathVariable(String baseEndpoint, DataSet dataSet) {
        // Step 1: Collect all parameters
        Map<String, Object> allParams = new HashMap<>();

        if (dataSet.getPath_variable() != null) {
            allParams.putAll(dataSet.getPath_variable()); // from YAML or static config
        }

        Map<String, Object> dynamicParams = TestExecutionContext.getAll();
        for (Map.Entry<String, Object> entry : dynamicParams.entrySet()) {
            if (entry.getValue() != null) {
                allParams.put(entry.getKey(), entry.getValue().toString());
            }
        }

        // Step 2: Replace path variables like {claim_id}
        String resolvedPath = replacePathVariables(baseEndpoint, allParams);

        // Step 3: Append remaining as query parameters
        return buildUrlWithParams(resolvedPath, allParams);
    }

    public String resolveEndpointWithPathVariable(String baseEndpoint, DataSet dataSet) {
        // Step 1: Collect all parameters
        Map<String, Object> allParams = new HashMap<>();

        if (dataSet.getPath_variable() != null) {
            allParams.putAll(dataSet.getPath_variable()); // from YAML or static config
        }

        Map<String, Object> dynamicParams = TestExecutionContext.getAll();
        for (Map.Entry<String, Object> entry : dynamicParams.entrySet()) {
            if (entry.getValue() != null) {
                allParams.put(entry.getKey(), entry.getValue().toString());
            }
        }

        // Step 2: Replace path variables like {claim_id}
        return replacePathVariables(baseEndpoint, allParams);

    }

    public String resolveEndpointWithQueryParam(String baseEndpoint, DataSet dataSet) {
        // Step 1: Collect all parameters
        Map<String, Object> allParams = new HashMap<>();

        if (dataSet.getQuery_param() != null) {
            allParams.putAll(dataSet.getQuery_param()); // from YAML or static config
        }

        Map<String, Object> dynamicParams = TestExecutionContext.getAll();
        for (Map.Entry<String, Object> entry : dynamicParams.entrySet()) {
            if (entry.getValue() != null) {
                allParams.put(entry.getKey(), entry.getValue().toString());
            }
        }

        // Step 2: Replace path variables like {claim_id}
        return buildUrlWithParams(baseEndpoint, allParams);

    }


    private String replacePathVariables(String url, Map<String, Object> params) {
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            if (url.contains(placeholder)) {
                url = url.replace(placeholder, URLEncoder.encode((String) entry.getValue(), StandardCharsets.UTF_8));
            }
        }
        return url;
    }

    public String buildUrlWithParams(String baseUrl, Map<String, Object> params) {
        StringBuilder urlBuilder = new StringBuilder(baseUrl);

        boolean firstParam = !baseUrl.contains("?");

        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value != null && !value.equals("empty")) {
                if (firstParam) {
                    urlBuilder.append("?");
                    firstParam = false;
                } else {
                    urlBuilder.append("&");
                }

                urlBuilder.append(URLEncoder.encode(key, StandardCharsets.UTF_8));
                urlBuilder.append("=");
                urlBuilder.append(URLEncoder.encode((String) value, StandardCharsets.UTF_8));
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

    public void populateDynamicKeysFromResponse(DataSet dataSet, JsonNode responseJson) {
        Map<String, Object> keyValueMap = new HashMap<>();

        DynamicKeyStore dynamicKeyStore = dataSet.getDynamic_keystore();
        if (dynamicKeyStore != null && dynamicKeyStore.getKeys() != null) {
            for (String key : dynamicKeyStore.getKeys()) {
                JsonNode valueNode = responseJson.at("/" + key); // Supports flat paths
                if (!valueNode.isMissingNode() && !valueNode.isNull()) {
                    Object value = extractValue(valueNode);
                    dynamicKeyStore.addValue(key, value);
                    keyValueMap.put(key, value);
                    TestExecutionContext.put(key, value);
                }
            }
        }
    }

    private Object extractValue(JsonNode node) {
        if (node.isTextual()) {
            return node.textValue();
        } else if (node.isNumber()) {
            return node.numberValue();
        } else if (node.isBoolean()) {
            return node.booleanValue();
        } else if (node.isArray() || node.isObject()) {
            return node; // return the JsonNode itself for complex types
        } else {
            return node.toString(); // fallback
        }
    }
    @SuppressWarnings("unchecked")
    public Object resolvePlaceholdersInObject(Object input) {
        if (input instanceof Map<?, ?> mapInput) {
            Map<String, Object> resolvedMap = new HashMap<>();
            for (Map.Entry<?, ?> entry : mapInput.entrySet()) {
                String key = entry.getKey().toString();
                Object value = resolvePlaceholdersInObject(entry.getValue());
                resolvedMap.put(key, value);
            }
            return resolvedMap;
        } else if (input instanceof List<?> listInput) {
            return listInput.stream()
                    .map(this::resolvePlaceholdersInObject)
                    .collect(Collectors.toList());
        } else if (input instanceof String strInput) {
            return resolveDynamicPlaceholder(strInput);
        }
        return input;
    }
    private String resolveDynamicPlaceholder(String value) {
        if (value.startsWith("{") && value.endsWith("}")) {
            String key = value.substring(1, value.length() - 1);
            Object dynamicValue = TestExecutionContext.get(key);
            return dynamicValue != null ? dynamicValue.toString() : value;
        }
        return value;
    }

    private JsonNode extractActualData(JsonNode actualNode, JsonNode desiredOutcome) {
        ObjectNode resultNode = objectMapper.createObjectNode();

        desiredOutcome.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            JsonNode expectedValue = entry.getValue();
            JsonNode actualValue = actualNode.get(key);

            if (expectedValue.isObject() && actualValue != null && actualValue.isObject()) {
                // Recurse into nested object
                resultNode.set(key, extractActualData(actualValue, expectedValue));
            } else if (expectedValue.isTextual() && expectedValue.asText().matches("^\\{.*}$")) {
                // Replace placeholder like "{id}" with actual value
                resultNode.set(key, actualValue != null ? actualValue : objectMapper.nullNode());
            } else {
                // Copy the value as-is from desiredOutcome (static expected value)
                resultNode.set(key, expectedValue);
            }
        });

        return resultNode;
    }



}
