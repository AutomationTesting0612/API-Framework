package com.api.framework.testing.controller;

import com.api.framework.testing.model.ScenarioMain;
import com.api.framework.testing.swagger.ScenarioGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class APIControllerClass {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public APIControllerClass(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Autowired
    private ScenarioGenerator scenarioGenerator;

    @PostMapping("create/api")
    public ResponseEntity<String> save(@RequestBody ScenarioMain uiModel) {
        try {
            String message = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(uiModel);

            kafkaTemplate.send("qa-source-topic", message);

            System.out.println("Message sent: " + message);
            return ResponseEntity.ok(message);

        } catch (Exception e) {
            return ResponseEntity.status(500).body("Automation failed: " + e.getMessage());
        }
    }

    @GetMapping("/generate/scenario")
    public Map<String, Object> generateTestCasesFromSwagger(@PathVariable String swaggerUrl, @PathVariable String applicationUrl) throws Exception {
        return scenarioGenerator.generate(swaggerUrl,applicationUrl);
    }




}