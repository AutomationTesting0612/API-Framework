package com.api.framework.testing.stepDef;

import com.api.framework.testing.model.ScenarioMain;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class APIStepDef {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    private final List<ScenarioMain> batchedScenarios = new ArrayList<>();

    /**
     * This step loads all JSON files inside `src/test/resources/jsonfiles/` folder
     */
    @Given("I have all test cases in folder")
    public void i_have_all_test_cases_in_folder() throws IOException {
        ObjectMapper mapper = new ObjectMapper();

        Path folderPath = Paths.get("src/test/resources/jsonfiles");

        if (!Files.exists(folderPath) || !Files.isDirectory(folderPath)) {
            throw new IOException("❌ Folder not found: " + folderPath.toAbsolutePath());
        }

        try (Stream<Path> fileStream = Files.list(folderPath)) {
            List<Path> jsonFiles = fileStream
                    .filter(path -> path.toString().endsWith(".json"))
                    .collect(Collectors.toList());

            for (Path file : jsonFiles) {
                String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                ScenarioMain scenario = mapper.readValue(content, ScenarioMain.class);
                batchedScenarios.add(scenario);
                System.out.println("📄 Loaded scenario from file: " + file.getFileName());
            }
        }
    }

    @When("I send the test case to Kafka")
    public void i_send_the_test_case_to_kafka() throws IOException {
        if (batchedScenarios.isEmpty()) {
            System.out.println("⚠️ No test cases to send.");
            return;
        }

        ObjectMapper mapper = new ObjectMapper();
        String batchedJson = mapper.writeValueAsString(batchedScenarios);

        kafkaTemplate.send("qa-source-topic", batchedJson);
        System.out.println("✅ Sent batched JSON to Kafka: " + batchedJson);
    }
}
