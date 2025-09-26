package com.api.framework.testing.swagger;

import com.api.framework.testing.document.DocumentReader;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.fasterxml.jackson.databind.type.LogicalType.DateTime;

@Service
public class ScenarioGenerator {

    @Value("http://localhost:8095/v3/api-docs")
    private String swaggerUrl;


    @Value("http://localhost:8095/")
    private String applicationUrl;

    @Autowired(required = false)
    private OpenAiChatModel chatModel;

    @Value("${spring.ai.openai.apikey:}")
    private String apiKey;


    @Value("${spring.ai.openai.imageUrl:https://api.openai.com/v1}")
    private String baseUrl;

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;



    private final ObjectMapper mapper = new ObjectMapper();

    public Map<String, Object> generateFromSwagger(String swaggerUrl, String applicationUrl) throws Exception {
        // Parse the Swagger/OpenAPI definition from the given URL
        SwaggerParseResult parseResult = new OpenAPIV3Parser().readLocation(swaggerUrl, null, null);
        OpenAPI openAPI = parseResult.getOpenAPI();

        if (openAPI == null || openAPI.getPaths() == null) {
            throw new IllegalArgumentException("Swagger URL is invalid or contains no paths: " + swaggerUrl);
        }

        Map<String, List<Map<String, Object>>> tagToTestCases = new HashMap<>();
        OpenAPI finalOpenAPI = openAPI;

        openAPI.getPaths().forEach((path, pathItem) -> {
            Map<PathItem.HttpMethod, Operation> operations = pathItem.readOperationsMap();

            for (Map.Entry<PathItem.HttpMethod, Operation> entry : operations.entrySet()) {
                PathItem.HttpMethod method = entry.getKey();
                Operation operation = entry.getValue();

                String tag = (operation.getTags() != null && !operation.getTags().isEmpty())
                        ? operation.getTags().get(0)
                        : "default";

                Schema<?> schema = null;
                Map<String, Object> requestBody = new HashMap<>();

                if (operation.getRequestBody() != null) {
                    schema = extractSchema(operation.getRequestBody(), finalOpenAPI);
                    if (schema != null) {
                        requestBody = generateSampleRequest(schema, finalOpenAPI, new HashSet<>());
                    }
                }

                Map<String, String> queryParams = new HashMap<>();
                Map<String, String> headers = new HashMap<>();
                String endpointPath = path;

                if (operation.getParameters() != null) {
                    for (Parameter param : operation.getParameters()) {
                        String name = param.getName();
                        String exampleValue = "sample_" + name;

                        switch (param.getIn()) {
                            case "path" -> endpointPath = endpointPath.replace("{" + name + "}", exampleValue);
                            case "query" -> queryParams.put(name, exampleValue);
                            case "header" -> headers.put(name, exampleValue);
                        }
                    }
                }

                String fullEndpoint = endpointPath;
                if (!queryParams.isEmpty()) {
                    StringJoiner sj = new StringJoiner("&");
                    queryParams.forEach((k, v) -> sj.add(k + "=" + v));
                    fullEndpoint += "?" + sj;
                }

                List<Map<String, Object>> testCases = new ArrayList<>();

                // Positive Test Case
                Map<String, Object> positiveScenario = new HashMap<>();
                positiveScenario.put("name", Optional.ofNullable(operation.getSummary()).orElse("AutoGenerated Test"));
                positiveScenario.put("description", Optional.ofNullable(operation.getDescription()).orElse("Generated from Swagger"));
                positiveScenario.put("datasets", List.of(Map.of(
                        "request_body", requestBody,
                        "desired_status", getSuccessStatusCode(operation),
                        "desired_outcome", getResponseExample(operation, getSuccessStatusCode(operation), finalOpenAPI)
                )));

                Map<String, Object> positiveTestCase = new HashMap<>();
                positiveTestCase.put("baseUrl", applicationUrl);
                positiveTestCase.put("endpoint", fullEndpoint.replaceFirst("^/", ""));
                positiveTestCase.put("mappingType", method.name());
                headers.putIfAbsent("Content-Type", "application/json");
                positiveTestCase.put("header", headers);
                positiveTestCase.put("scenario", positiveScenario);

                testCases.add(positiveTestCase);

                // Negative Test Cases
                if (schema != null) {
                    List<Map<String, Object>> negativeDatasets = generateNegativeTestCases(schema, requestBody, finalOpenAPI, operation);
                    for (Map<String, Object> negativeDataset : negativeDatasets) {
                        Map<String, Object> negativeScenario = new HashMap<>();
                        negativeScenario.put("name", Optional.ofNullable(operation.getSummary()).orElse("AutoGenerated Test"));
                        negativeScenario.put("description", Optional.ofNullable(operation.getDescription()).orElse("Generated from Swagger"));
                        negativeScenario.put("datasets", List.of(negativeDataset));

                        Map<String, Object> negativeTestCase = new HashMap<>();
                        negativeTestCase.put("baseUrl", applicationUrl);
                        negativeTestCase.put("endpoint", fullEndpoint.replaceFirst("^/", ""));
                        negativeTestCase.put("mappingType", method.name());
                        headers.putIfAbsent("Content-Type", "application/json");
                        negativeTestCase.put("header", headers);
                        negativeTestCase.put("scenario", negativeScenario);

                        testCases.add(negativeTestCase);
                    }
                }

                tagToTestCases.computeIfAbsent(tag, k -> new ArrayList<>()).addAll(testCases);
            }
        });

        // Save generated test cases to JSON files
        ObjectMapper mapper = new ObjectMapper();
        for (Map.Entry<String, List<Map<String, Object>>> entry : tagToTestCases.entrySet()) {
            Map<String, Object> finalMap = new HashMap<>();
            finalMap.put("report", null);
            finalMap.put("data_list", entry.getValue());

            String fileName = "generated-testcases-" + entry.getKey().toLowerCase().replaceAll("\\s+", "_") +
                    "_" + System.currentTimeMillis() + ".json";
            mapper.writeValue(new File("src/test/resources/jsonfiles/" + fileName), finalMap);
            System.out.println("✅ Generated file: " + fileName);
        }

        return Map.of("status", "success", "filesGenerated", tagToTestCases.keySet());
    }

    public Map<String, Object> generateFromControllers() throws Exception {
        Map<String, List<Map<String, Object>>> controllerTestCases = new HashMap<>();

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : requestMappingHandlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo mappingInfo = entry.getKey();
            HandlerMethod handlerMethod = entry.getValue();

            Set<String> patterns = mappingInfo.getPatternsCondition().getPatterns();
            Set<RequestMethod> methods = mappingInfo.getMethodsCondition().getMethods();

            for (String pattern : patterns) {
                for (RequestMethod method : methods) {
                    Map<String, Object> testCase = new HashMap<>();
                    testCase.put("endpoint", pattern);
                    testCase.put("httpMethod", method.name());
                    testCase.put("controller", handlerMethod.getBeanType().getSimpleName());
                    testCase.put("handlerMethod", handlerMethod.getMethod().getName());

                    controllerTestCases
                            .computeIfAbsent(handlerMethod.getBeanType().getSimpleName(), k -> new ArrayList<>())
                            .add(testCase);
                }
            }
        }

        // 🔹 Save each controller’s test cases into JSON file
        for (Map.Entry<String, List<Map<String, Object>>> entry : controllerTestCases.entrySet()) {
            Map<String, Object> finalMap = new HashMap<>();
            finalMap.put("report", null);
            finalMap.put("data_list", entry.getValue());

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            String fileName = "generated-testcases-" + entry.getKey().toLowerCase() + "-" + timestamp + ".json";

            File outputFile = new File("src/test/resources/jsonfiles/" + fileName);
            mapper.writeValue(outputFile, finalMap);

            System.out.println("✅ Controller-based test cases written: " + outputFile.getAbsolutePath());
        }

        return Map.of(
                "status", "success",
                "source", "controllers",
                "filesGenerated", controllerTestCases.keySet()
        );
    }

    private String getSuccessStatusCode(Operation operation) {
        return operation.getResponses().keySet().stream()
                .filter(code -> code.matches("\\d{3}") && code.startsWith("2"))
                .findFirst()
                .orElse("200");
    }

    private String getErrorStatusCode(io.swagger.v3.oas.models.Operation operation) {
        return operation.getResponses().keySet().stream()
                .filter(code -> code.matches("\\d{3}") && code.startsWith("4"))
                .findFirst()
                .orElse("400");
    }

    private Schema<?> extractSchema(RequestBody requestBody, OpenAPI openAPI) {
        if (requestBody.getContent() != null &&
                requestBody.getContent().get("application/json") != null &&
                requestBody.getContent().get("application/json").getSchema() != null) {
            Schema<?> schema = requestBody.getContent().get("application/json").getSchema();
            if (schema.get$ref() != null) {
                String refName = schema.get$ref().replace("#/components/schemas/", "");
                return openAPI.getComponents().getSchemas().get(refName);
            }
            return schema;
        }
        return null;
    }

    private Map<String, Object> generateSampleRequest(Schema<?> schema, OpenAPI openAPI, Set<String> visitedRefs) {
        Map<String, Object> sample = new HashMap<>();
        schema = resolveSchema(schema, openAPI);

        if (schema.get$ref() != null) {
            String refName = schema.get$ref().replace("#/components/schemas/", "");
            if (visitedRefs.contains(refName)) {
                return Map.of("ref", "circular_" + refName); // avoid loop
            }
            visitedRefs.add(refName);
        }

        if (schema.getExample() != null) {
            try {
                return mapper.convertValue(schema.getExample(), new TypeReference<Map<String, Object>>() {});
            } catch (IllegalArgumentException e) {
                // fallback
            }
        }

        if (schema.getProperties() != null) {
            schema.getProperties().forEach((key, value) -> {
                Schema<?> prop = resolveSchema((Schema<?>) value, openAPI);
                Object val = generateValueForSchema(prop, openAPI, visitedRefs);
                sample.put(key, val);
            });
        }

        return sample;
    }

    // generateValueForSchema
    private Object generateValueForSchema(Schema<?> schema, OpenAPI openAPI, Set<String> visitedRefs) {
        schema = resolveSchemaRecursive(schema, openAPI, visitedRefs);
        if (schema == null) return "unknown";

        if (schema.getExample() != null) return schema.getExample();

        if (schema.getEnum() != null && !schema.getEnum().isEmpty()) return schema.getEnum().get(0);

        String type = schema.getType();
        if (type == null) {
            if (schema.getProperties() != null) {
                return generateSampleRequest(schema, openAPI, visitedRefs);
            } else if (schema.getItems() != null) {
                Schema<?> itemSchema = resolveSchemaRecursive(schema.getItems(), openAPI, visitedRefs);
                Object item = generateValueForSchema(itemSchema, openAPI, visitedRefs);
                return List.of(item);
            } else {
                return "unknown";
            }
        }

        return switch (type) {
            case "string" -> "sample_string";
            case "integer" -> 123;
            case "boolean" -> true;
            case "number" -> 99.99;
            case "object" -> generateSampleRequest(schema, openAPI, visitedRefs);
            case "array" -> {
                Schema<?> itemSchema = resolveSchemaRecursive(schema.getItems(), openAPI, visitedRefs);
                Object item = generateValueForSchema(itemSchema, openAPI, visitedRefs);
                yield List.of(item);
            }
            default -> "unknown";
        };
    }



    private Schema<?> resolveSchema(Schema<?> schema, OpenAPI openAPI) {
        Set<String> visitedRefs = new HashSet<>();
        return resolveSchemaRecursive(schema, openAPI, visitedRefs);
    }

    private Schema<?> resolveSchemaRecursive(Schema<?> schema, OpenAPI openAPI, Set<String> visitedRefs) {
        if (schema == null) return null;
        if (schema.getAnyOf() != null && !schema.getAnyOf().isEmpty()) {
            schema = (Schema<?>) schema.getAnyOf().get(0);
        } else if (schema.getOneOf() != null && !schema.getOneOf().isEmpty()) {
            schema = (Schema<?>) schema.getOneOf().get(0);
        } else if (schema.getAllOf() != null && !schema.getAllOf().isEmpty()) {
            schema = (Schema<?>) schema.getAllOf().get(0);
        }

        while (true) {
            // Handle $ref
            if (schema.get$ref() != null) {
                String refName = schema.get$ref().replace("#/components/schemas/", "");
                if (visitedRefs.contains(refName)) return schema; // prevent circular refs
                visitedRefs.add(refName);
                schema = openAPI.getComponents().getSchemas().get(refName);
                if (schema == null) return null;
                continue;
            }

            // Handle anyOf
            if (schema.getAnyOf() != null && !schema.getAnyOf().isEmpty()) {
                schema = resolveSchemaRecursive((Schema<?>) schema.getAnyOf().get(0), openAPI, visitedRefs);
                continue;
            }

            // Handle oneOf
            if (schema.getOneOf() != null && !schema.getOneOf().isEmpty()) {
                schema = resolveSchemaRecursive((Schema<?>) schema.getOneOf().get(0), openAPI, visitedRefs);
                continue;
            }

            // Handle allOf
            if (schema.getAllOf() != null && !schema.getAllOf().isEmpty()) {
                schema = resolveSchemaRecursive((Schema<?>) schema.getAllOf().get(0), openAPI, visitedRefs);
                continue;
            }

            break;
        }

        return schema;
    }

    private List<Map<String, Object>> generateNegativeTestCases(
            Schema<?> schema, Map<String, Object> originalRequest, OpenAPI openAPI, Operation operation) {

        List<Map<String, Object>> negativeCases = new ArrayList<>();

        if (schema.get$ref() != null) {
            String refName = schema.get$ref().replace("#/components/schemas/", "");
            schema = openAPI.getComponents().getSchemas().get(refName);
        }

        if (schema.getRequired() != null) {
            for (String requiredField : schema.getRequired()) {
                Map<String, Object> copy = new HashMap<>(originalRequest);
                copy.remove(requiredField);

                String errorStatus = "400";
                Map<String, Object> errorResponse = getResponseExample(operation, errorStatus, openAPI );

                Map<String, Object> testCase = new HashMap<>();
                testCase.put("request_body", copy);
                testCase.put("desired_status", getErrorStatusCode(operation));
                testCase.put("desired_outcome", errorResponse.isEmpty()
                        ? Map.of("error", "Missing field: " + requiredField)
                        : errorResponse);

                negativeCases.add(testCase);
            }
        }

        return negativeCases;
    }

    private Map<String, Object> getResponseExample(Operation operation, String statusCode, OpenAPI openAPI) {
        Set<String> visitedRefs = new HashSet<>();
        if (operation.getResponses() == null || !operation.getResponses().containsKey(statusCode)) {
            System.out.println("❌ No response for status: " + statusCode);
            return Map.of();
        }

        var response = operation.getResponses().get(statusCode);
        if (response.getContent() == null || response.getContent().isEmpty()) {
            System.out.println("❌ No content in response");
            return Map.of();
        }

        var contentMap = response.getContent();

// Prefer application/json, else fallback to any available content
        var mediaType = contentMap.get("application/json");
        if (mediaType == null) {
            mediaType = contentMap.values().iterator().next();
            System.out.println("⚠️ Falling back to non-standard content type");
        }

        if (mediaType.getExample() != null) {
            try {
                return mapper.convertValue(mediaType.getExample(), new TypeReference<>() {});
            } catch (IllegalArgumentException e) {
                System.err.println("⚠️ Direct example not usable: " + e.getMessage());
            }
        }

        if (mediaType.getExamples() != null && !mediaType.getExamples().isEmpty()) {
            var entry = mediaType.getExamples().entrySet().iterator().next();
            if (entry.getValue() != null && entry.getValue().getValue() != null) {
                try {
                    return mapper.convertValue(entry.getValue().getValue(), new TypeReference<>() {});
                } catch (IllegalArgumentException e) {
                    System.err.println("⚠️ Named example not parsable: " + e.getMessage());
                }
            }
        }

        var schema = mediaType.getSchema();
        if (schema != null) {
            Schema<?> resolvedSchema = resolveSchemaRecursive(schema, openAPI, visitedRefs);
            if (resolvedSchema != null && resolvedSchema.getExample() != null) {
                try {
                    return mapper.convertValue(resolvedSchema.getExample(), new TypeReference<>() {});
                } catch (IllegalArgumentException e) {
                    System.err.println("⚠️ Schema example not usable: " + e.getMessage());
                }
            }

            // fallback to generating response
            Map<String, Object> fallback = generateSampleResponseFromSchema(resolvedSchema, openAPI,visitedRefs);
            System.out.println("🧩 Generated fallback desired_outcome: " + fallback);
            return fallback;
        }

        System.out.println("❌ No usable response example or schema for: " + statusCode);
        return Map.of();
    }

    private Map<String, Object> generateSampleResponseFromSchema(Schema<?> schema, OpenAPI openAPI, Set<String> visitedRefs) {
        schema = resolveSchemaRecursive(schema, openAPI, visitedRefs);
        if (schema == null) {
            return Map.of("message", "No schema available");
        }

        if (schema.getExample() != null) {
            try {
                Object example = schema.getExample();
                return Map.of("value", example);
            } catch (IllegalArgumentException e) {
                System.err.println("⚠️ Failed to convert schema example: " + e.getMessage());
            }
        }

        if ("object".equals(schema.getType()) && schema.getProperties() != null) {
            Map<String, Object> response = new HashMap<>();
            for (Map.Entry<String, Schema> entry : (Set<Map.Entry<String, Schema>>) schema.getProperties().entrySet()) {
                Schema<?> propSchema = resolveSchemaRecursive(entry.getValue(), openAPI, visitedRefs);
                Object sampleValue = generateValueForSchema(propSchema, openAPI, visitedRefs);
                response.put(entry.getKey(), sampleValue);
            }
            return response;
        }

        if ("array".equals(schema.getType()) && schema.getItems() != null) {
            Schema<?> itemSchema = resolveSchemaRecursive(schema.getItems(), openAPI, visitedRefs);
            Object item = generateValueForSchema(itemSchema, openAPI, visitedRefs);
            return Map.of("value", List.of(item));
        }

        if (schema.getType() != null) {
            Object value = generateValueForSchema(schema, openAPI, visitedRefs);
            return Map.of("value", value);
        }

        return Map.of("message", "No example or properties defined in schema");
    }

    public Map<String, Object> generateFromDocumentLLM(String documentText) throws Exception {
        if (chatModel == null) {
            throw new IllegalStateException("LLM model not configured. Please set spring.ai.openai.api-key");
        }

        String promptText = """
    You are a Test Case Generator AI.
    
    Based on the following document, generate **only valid JSON** test cases.
    
    ⚠️ Output Format (mandatory):
    {
      "data_list": [
        {
          "baseUrl": "<string>",          // dynamically from document or defaults
          "endpoint": "<string>",         // API endpoint
          "mappingType": "<string>",      // GET, POST, PUT, DELETE
          "header": {
            "content-Type": "application/json"
          },
          "scenario": {
            "name": "<string>",           // scenario title
            "description": "<string>",    // scenario description
            "datasets": [
              {
                "request_body": { ... },  // dynamic request
                "desired_status": "<int>", 
                "desired_outcome": { ... }
              }
            ]
          }
        }
      ],
      "report": null
    }

    Document Content:
    %s

    Rules:
    - Never add text before or after the JSON.
    - Always wrap everything in "data_list" and add "report": null.
    - Derive values (endpoint, request_body, outcomes, etc.) from the document.
    - mappingType should reflect operation type (GET/POST/PUT/DELETE).
    """.formatted(documentText);

        // Call OpenAI
        ChatResponse response = chatModel.call(new Prompt(promptText));
        String llmOutput = response.getResult().getOutput().getContent();

        // Extract JSON part only
        int firstIndex = llmOutput.indexOf('{');
        int lastIndex = llmOutput.lastIndexOf('}') + 1;
        if (firstIndex == -1 || lastIndex == -1) {
            throw new IllegalArgumentException("No JSON object found in LLM output");
        }

        String jsonOnly = llmOutput.substring(firstIndex, lastIndex);

        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        return mapper.readValue(jsonOnly, new TypeReference<>() {});
    }

    public Map<String, Object> generateFromDocumentFile(MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            throw new RuntimeException("Uploaded file is empty");
        }

        String filename = file.getOriginalFilename();
        List<String> documentRows = new ArrayList<>();

        // Save MultipartFile to a temporary file
        File tempFile = File.createTempFile("upload", ".tmp");
        file.transferTo(tempFile);

        // Read file based on extension
        if (filename.endsWith(".xlsx") || filename.endsWith(".xls")) {
            List<Map<String, String>> excelRows = DocumentReader.readExcel(tempFile.getAbsolutePath());
            for (Map<String, String> row : excelRows) {
                documentRows.add(row.toString());
            }
        } else if (filename.endsWith(".csv")) {
            List<Map<String, String>> csvRows = DocumentReader.readCSV(tempFile.getAbsolutePath());
            for (Map<String, String> row : csvRows) {
                documentRows.add(row.toString());
            }
        } else if (filename.endsWith(".docx")) {
            List<Map<String, String>> docxRows = DocumentReader.readDocx(tempFile.getAbsolutePath());
            for (Map<String, String> row : docxRows) {
                documentRows.add(row.toString());
            }
        } else {
            throw new IllegalArgumentException("Unsupported file type: " + filename);
        }

        if (documentRows.isEmpty()) {
            throw new RuntimeException("Uploaded document is empty or has no readable lines.");
        }

        // Convert rows into plain text for LLM
        StringBuilder docBuilder = new StringBuilder("Business Requirement Sheet:\n");
        for (String row : documentRows) {
            docBuilder.append(row).append("\n");
        }

        // Call your LLM-based test case generator
        return generateFromDocumentLLM(docBuilder.toString());
    }


    public Map<String, Object> generateFromSwaggerLLM(String swaggerSpec, String applicationUrl) throws Exception {
        if (chatModel == null) {
            throw new IllegalStateException("LLM model not configured. Please set spring.ai.openai.api-key");
        }

        String promptText = """
    You are a Test Case Generator AI.
    
    Based on the following Swagger/OpenAPI specification and application URL, generate **only valid JSON** test cases.
    
    ⚠️ Output Format (mandatory):
    {
      "data_list": [
        {
          "baseUrl": "<string>",          // use applicationUrl as base
          "endpoint": "<string>",         // API endpoint from Swagger
          "mappingType": "<string>",      // GET, POST, PUT, DELETE
          "header": {
            "content-Type": "application/json"
          },
          "scenario": {
            "name": "<string>",           // scenario title
            "description": "<string>",    // scenario description
            "datasets": [
              {
                "request_body": { ... },  // request schema from Swagger
                "desired_status": "<int>", 
                "desired_outcome": { ... }
              }
            ]
          }
        }
      ],
      "report": null
    }

    Application Base URL: %s

    Swagger Specification:
    %s

    Rules:
    - Never add text before or after the JSON.
    - Always wrap everything in "data_list" and add "report": null.
    - Derive endpoints, methods, request bodies, and outcomes directly from Swagger spec.
    - For each operation (GET/POST/PUT/DELETE), generate at least one positive (200) and one negative (400/404) dataset.
    """.formatted(applicationUrl, swaggerSpec);

        // Call LLM
        ChatResponse response = chatModel.call(new Prompt(promptText));
        String llmOutput = response.getResult().getOutput().getContent();

        // Extract JSON part only
        int firstIndex = llmOutput.indexOf('{');
        int lastIndex = llmOutput.lastIndexOf('}') + 1;
        if (firstIndex == -1 || lastIndex == -1) {
            throw new IllegalArgumentException("No JSON object found in LLM output");
        }

        String jsonOnly = llmOutput.substring(firstIndex, lastIndex);

        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        return mapper.readValue(jsonOnly, new TypeReference<>() {});
    }

    public void trainModelWithCases(Map<String, Object> generatedCases) {
        if (chatModel == null) {
            System.out.println("⚠️ LLM not configured; skipping training.");
            return;
        }

        try {
            String casesJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(generatedCases);

            String trainingPrompt = """
            You are a Test Case Generator AI.
            Train on the following JSON test cases to improve future generations:
            %s
            """.formatted(casesJson);

            // Call model for "training" (can be a fine-tuning or simple feedback)
            chatModel.call(new Prompt(trainingPrompt));
            System.out.println("✅ Model updated with latest generated test cases.");

        } catch (Exception e) {
            System.err.println("❌ Failed to train model: " + e.getMessage());
        }
    }





}
