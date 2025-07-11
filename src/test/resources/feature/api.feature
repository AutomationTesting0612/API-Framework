@test
Feature: API Test Runner via Kafka

  Scenario: Trigger API test via Kafka
    Given I have all test cases in folder
    When I send the test case to Kafka