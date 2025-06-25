package com.api.framework.testing.runner;


import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;

@CucumberContextConfiguration
@SpringBootTest
public class CucumberTestContextConfig {
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
}
