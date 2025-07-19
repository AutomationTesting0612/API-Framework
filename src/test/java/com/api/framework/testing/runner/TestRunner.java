package com.api.framework.testing.runner;

import org.junit.platform.suite.api.*;
import org.springframework.test.context.ContextConfiguration;

import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;



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
