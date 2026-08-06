package com.parknova.parknovaapigateway.email;

/**
 * Publishes {@link EmailMessage}s to the shared email topic consumed by offstreet-service.
 * Fire-and-forget: implementations must never throw to the caller.
 */
public interface EmailPublisher {

    void publish(EmailMessage message);
}
