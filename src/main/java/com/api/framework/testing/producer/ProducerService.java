package com.api.framework.testing.producer;

import com.api.framework.testing.model.ScenarioMain;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
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

//    @Bean
//    public ProducerFactory<String, ScenarioMain> producerFactory(ObjectMapper objectMapper) {
//        Map<String, Object> configProps = new HashMap<>();
//        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
//        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
//        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
//        return new DefaultKafkaProducerFactory<>(configProps, new StringSerializer(),
//                new JsonSerializer<>(objectMapper));
//    }
//
//    @Bean
//    public KafkaTemplate<String, ScenarioMain> kafkaTemplate(ObjectMapper objectMapper) {
//        return new KafkaTemplate<>(producerFactory(objectMapper));
//    }


}

