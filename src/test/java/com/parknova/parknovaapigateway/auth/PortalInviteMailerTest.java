package com.parknova.parknovaapigateway.auth;

import com.parknova.parknovaapigateway.config.ParknovaProperties;
import com.parknova.parknovaapigateway.email.EmailMessage;
import com.parknova.parknovaapigateway.email.EmailPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PortalInviteMailerTest {

    @Mock
    private EmailPublisher emailPublisher;

    private PortalInviteMailer mailer;

    @BeforeEach
    void setUp() {
        ParknovaProperties properties = new ParknovaProperties(
                new ParknovaProperties.Keycloak("http://localhost", "parknova", "client", "secret", true),
                null,
                new ParknovaProperties.Portal("http://localhost:3000", 72),
                new ParknovaProperties.Mail(false, "noreply@test"),
                "test-secret",
                "http://localhost:8091"
        );
        mailer = new PortalInviteMailer(properties, emailPublisher);
    }

    @Test
    void sendSetupInvite_publishesEmailWithPortalUrlAndToken() {
        String setupUrl = "http://localhost:3000/setup?token=abc-123";

        mailer.sendSetupInvite("admin@acme.example", setupUrl);

        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailPublisher).publish(captor.capture());
        EmailMessage message = captor.getValue();

        assertThat(message.to()).isEqualTo("admin@acme.example");
        assertThat(message.subject()).contains("garage portal");
        assertThat(message.body()).contains("http://localhost:3000");
        assertThat(message.body()).contains("abc-123");
        assertThat(message.body()).contains(setupUrl);
        assertThat(message.body()).contains("admin@acme.example");
        assertThat(message.attachments()).isNull();
    }

    @Test
    void sendPasswordReset_publishesEmailWithResetLink() {
        String resetUrl = "http://localhost:3000/reset-password?token=reset-tok";

        mailer.sendPasswordReset("admin@acme.example", resetUrl);

        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailPublisher).publish(captor.capture());
        EmailMessage message = captor.getValue();

        assertThat(message.to()).isEqualTo("admin@acme.example");
        assertThat(message.body()).contains("reset-tok");
        assertThat(message.body()).contains(resetUrl);
    }
}
