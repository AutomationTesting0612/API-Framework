package com.api.framework.testing.runner;

import com.api.framework.testing.model.ScenarioMain;
import io.cucumber.spring.CucumberContextConfiguration;
import org.junit.platform.suite.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ContextConfiguration;

import static io.cucumber.junit.platform.engine.Constants.*;



@Suite
//@SpringBootTest
@SuppressWarnings("all")
@IncludeEngines("cucumber")
@SelectClasspathResource("feature")
//@CucumberContextConfiguration
@SelectPackages("com.api.framework.testing.stepDef")
@ContextConfiguration(classes = Object.class)
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@test")
public class TestRunner {



}
