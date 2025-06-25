package com.api.framework.testing.stepDef;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class APIStepDef {

    private String jsonContent;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Given("I have a test case {string}")
    public void i_have_a_test_case(String fileName) throws IOException {
        Path path = Paths.get("src/test/resources/jsonfiles", fileName);
        jsonContent = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    @When("I send the test case to Kafka")
    public void i_send_the_test_case_to_kafka() {
        kafkaTemplate.send("qa-source-topic", jsonContent);
        System.out.println("✅ Sent JSON to Kafka: " + jsonContent);
    }
}
