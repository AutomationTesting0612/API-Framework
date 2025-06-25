package com.api.framework.testing.controller;

import com.api.framework.testing.model.ScenarioMain;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class APIControllerClass {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public APIControllerClass(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @PostMapping("create/api")
    public ResponseEntity<String> save(@RequestBody ScenarioMain uiModel) {
        try {
            String message = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(uiModel);

            kafkaTemplate.send("qa-source-topic", message);

            System.out.println("Message sent: " + message);
            return ResponseEntity.ok("Automation request sent successfully!");

        } catch (Exception e) {
            return ResponseEntity.status(500).body("Automation failed: " + e.getMessage());
        }
    }


}