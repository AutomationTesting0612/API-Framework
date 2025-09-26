package com.api.framework.testing.controller;


import com.api.framework.testing.swagger.ScenarioGenerator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/generate")
@Tag(name = "Scenario Generator", description = "Generate test cases from Swagger + Application URL")
public class APIControllerClass {

    private final ScenarioGenerator scenarioGenerator;

    public APIControllerClass(ScenarioGenerator scenarioGenerator) {
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
                                   Model model) {
        try {
            Map<String, Object> scenarios = scenarioGenerator.generateFromSwaggerLLM(swaggerUrl, applicationUrl);
            model.addAttribute("swaggerUrl", swaggerUrl);
            model.addAttribute("applicationUrl", applicationUrl);
            model.addAttribute("scenarios", scenarios);
        } catch (Exception e) {
            model.addAttribute("error", "❌ Error generating scenarios: " + e.getMessage());
        }
        return "result";
    }

    // ---------- ✅ Using LLM ----------
    @PostMapping("/upload")
    public String handleUpload(@RequestParam("file") MultipartFile file, Model model) {
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

            model.addAttribute("jsonOutput", jsonOutput);

        } catch (Exception e) {
            model.addAttribute("error", "❌ Failed: " + e.getMessage());
        }

        return "result";
    }
}