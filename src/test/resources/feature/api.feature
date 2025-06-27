@test
Feature: API Test Runner via Kafka

  Scenario Outline: Trigger API test via Kafka
    Given I have all test cases in folder
    When I send the test case to Kafka

    Examples:
      | fileName          |
      | crud1.json        |
      | crud2.json        |
      | crud3.json        |