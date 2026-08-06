package com.parknova.parknovaapigateway.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes email commands to Kafka for offstreet-service {@code EmailMessageListener}.
 * Active when {@code parknova.kafka.enabled=true} (default).
 */
@Component
@ConditionalOnProperty(name = "parknova.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaEmailPublisher implements EmailPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaEmailPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topic;

    public KafkaEmailPublisher(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${parknova.kafka.topic.email:parknova.email.send}") String topic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @Override
    public void publish(EmailMessage message) {
        try {
            kafkaTemplate.send(topic, message.to(), message);
            log.debug("Published email event to topic {} for {}", topic, message.to());
        } catch (Exception e) {
            log.warn("Failed to publish email event for {}: {}", message.to(), e.getMessage());
        }
    }
}
