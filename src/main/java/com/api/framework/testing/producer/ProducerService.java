package com.api.framework.testing.producer;

import com.api.framework.testing.model.ScenarioMain;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

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

