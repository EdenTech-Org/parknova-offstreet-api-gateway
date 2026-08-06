package com.parknova.parknovaapigateway.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Fallback when Kafka is disabled ({@code parknova.kafka.enabled=false}, e.g. tests) —
 * drops the message so the app runs without a broker.
 */
@Component
@ConditionalOnProperty(name = "parknova.kafka.enabled", havingValue = "false")
public class NoOpEmailPublisher implements EmailPublisher {

    private static final Logger log = LoggerFactory.getLogger(NoOpEmailPublisher.class);

    @Override
    public void publish(EmailMessage message) {
        log.debug("Kafka disabled; dropping email event for {}", message.to());
    }
}
