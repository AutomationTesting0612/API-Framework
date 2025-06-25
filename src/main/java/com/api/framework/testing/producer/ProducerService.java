package com.api.framework.testing.producer;

import com.api.framework.testing.model.ScenarioMain;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ser.std.StringSerializer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class ProducerService {
    private static KafkaTemplate<String, ScenarioMain> kafkaTemplate = null;

    @Autowired
    public ProducerService(KafkaTemplate<String, ScenarioMain> kafkaTemplate) {
        ProducerService.kafkaTemplate = kafkaTemplate;
    }


    public static void sendMessage(String topic, ScenarioMain model) {

        kafkaTemplate.send(topic, model);

    }


}

