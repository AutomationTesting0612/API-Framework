package com.api.framework.testing.controller;


import com.api.framework.testing.model.ScenarioMain;
import com.api.framework.testing.producer.ProducerService;
import com.api.framework.testing.swagger.ScenarioGenerator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/generate")
@Tag(name = "Scenario Generator", description = "Generate test cases from Swagger + Application URL")
public class APIControllerClass {

    // Hold last JSON in memory for download
    private String lastGeneratedJson;
    private final ObjectMapper objectMapper;

    @Value("${kafka.source-topic}")
    private String sourceTopic;

    private final ProducerService producerService;

    private static KafkaTemplate<String, ScenarioMain> kafkaTemplate = null;

    private final ScenarioGenerator scenarioGenerator;

    public APIControllerClass(ObjectMapper objectMapper, ProducerService producerService, ScenarioGenerator scenarioGenerator) {
        this.objectMapper = objectMapper;
        this.producerService = producerService;
        this.scenarioGenerator = scenarioGenerator;
    }

    // ---------- ✅ Thymeleaf UI ----------
    @GetMapping("/form")
    @Operation(summary = "Load input form", description = "Thymeleaf page to enter Swagger URL and Application URL")
    public String showForm() {
        return "generate-form";
    }


   // Using Swagger with LLM
    @PostMapping("/scenario")
    @Operation(summary = "Generate scenarios (UI)", description = "Submit via Thymeleaf form and see result page")
    public String generateScenario(@RequestParam String swaggerUrl,
                                   @RequestParam String applicationUrl,
                                   Model model, HttpSession session) {
        try {
            Map<String, Object> scenarios = scenarioGenerator.generateFromSwaggerLLM(swaggerUrl, applicationUrl);
            ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
            String jsonOutput = mapper.writeValueAsString(scenarios);

            model.addAttribute("swaggerUrl", swaggerUrl);
            model.addAttribute("applicationUrl", applicationUrl);
            session.setAttribute("lastGeneratedJson", jsonOutput);
            model.addAttribute("jsonOutput", jsonOutput);
        } catch (Exception e) {
            model.addAttribute("error", "❌ Error generating scenarios: " + e.getMessage());
        }
        return "result";
    }

    // ---------- ✅ Using LLM ----------
    @PostMapping("/upload")
    public String handleUpload(@RequestParam("file") MultipartFile file, Model model, HttpSession session) {
        try {
            Map<String, Object> cases = scenarioGenerator.generateFromDocumentFile(file);

            // Ensure "data_list" exists
            List<Map<String, Object>> dataList = (List<Map<String, Object>>) cases.get("data_list");
            if (dataList == null) {
                dataList = new ArrayList<>();
                cases.put("data_list", dataList);
            }

            // Convert the full map to JSON string
            ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
            String jsonOutput = mapper.writeValueAsString(cases);
            // ✅ Store in session
            session.setAttribute("lastGeneratedJson", jsonOutput);
            model.addAttribute("jsonOutput", jsonOutput);

        } catch (Exception e) {
            model.addAttribute("error", "❌ Failed: " + e.getMessage());
        }

        return "result";
    }

    // ---------- ✅ Download Endpoint ----------
    @GetMapping("/download")
    public ResponseEntity<byte[]> downloadJson(HttpSession session) {
        String lastGeneratedJson = (String) session.getAttribute("lastGeneratedJson");

        if (lastGeneratedJson == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("❌ No JSON available. Please generate scenarios first.".getBytes(StandardCharsets.UTF_8));
        }

        byte[] jsonBytes = lastGeneratedJson.getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setContentDispositionFormData("attachment", "test-cases.json");
        return new ResponseEntity<>(jsonBytes, headers, HttpStatus.OK);
    }

    @PostMapping("/testcases")
    public String uploadTestCases(@RequestParam("file") MultipartFile file, Model model) {
        try {
            // Read file content as text (assuming JSON/CSV/Excel)
            String content = new String(file.getBytes());

            ScenarioMain scenario = objectMapper.readValue(content, ScenarioMain.class);
            // Publish message to Kafka

            ProducerService.sendMessage(sourceTopic, scenario);
            Thread.sleep(5000L);

            model.addAttribute("reportAvailable", true);

            return "generate-form";
        } catch (Exception e) {
            model.addAttribute("error", "❌ Failed to process file: " + e.getMessage());
            return "generate-form";
        }
    }

    @GetMapping("/report/download")
    public ResponseEntity<Resource> downloadReport() throws IOException {
        Path path = Paths.get("APIReport.html"); // adjust your path

        if (!Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }

        ByteArrayResource resource = new ByteArrayResource(Files.readAllBytes(path));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=extent-report.html")
                .contentType(MediaType.TEXT_HTML)
                .body(resource);
    }
}