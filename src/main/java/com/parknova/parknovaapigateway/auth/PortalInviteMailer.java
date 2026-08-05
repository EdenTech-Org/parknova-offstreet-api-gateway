package com.parknova.parknovaapigateway.auth;

import com.parknova.parknovaapigateway.config.ParknovaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class PortalInviteMailer {

    private static final Logger log = LoggerFactory.getLogger(PortalInviteMailer.class);

    private final ParknovaProperties properties;
    private final ObjectProvider<JavaMailSender> mailSender;

    public PortalInviteMailer(ParknovaProperties properties, ObjectProvider<JavaMailSender> mailSender) {
        this.properties = properties;
        this.mailSender = mailSender;
    }

    public void sendSetupInvite(String toEmail, String setupUrl) {
        String subject = "Your ParkNova garage portal access";
        String body = """
                Hello,

                Your ParkNova garage portal account has been set up.

                Username: %s
                Role: System Admin

                Use this one-time link to set your password (valid for %d hours):
                %s

                If you did not expect this email, contact support.

                — ParkNova
                """.formatted(
                toEmail,
                properties.portal().inviteTtlHours(),
                setupUrl
        );
        dispatch(toEmail, subject, body, setupUrl);
    }

    public void sendPasswordReset(String toEmail, String resetUrl) {
        String subject = "Reset your ParkNova garage portal password";
        String body = """
                Hello,

                A password reset was requested for your garage portal account (%s).

                Use this one-time link (valid for %d hours):
                %s

                If you did not request this, you can ignore this email.

                — ParkNova
                """.formatted(toEmail, properties.portal().inviteTtlHours(), resetUrl);
        dispatch(toEmail, subject, body, resetUrl);
    }

    private void dispatch(String toEmail, String subject, String body, String linkForLog) {
        JavaMailSender sender = mailSender.getIfAvailable();
        boolean mailEnabled = Boolean.TRUE.equals(properties.mail().enabled());
        if (!mailEnabled || sender == null) {
            log.info("Portal invite/reset email (mail disabled) to={} link={}", toEmail, linkForLog);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(properties.mail().from());
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(body);
            sender.send(message);
            log.info("Sent portal email to={}", toEmail);
        } catch (Exception ex) {
            log.error("Failed to send portal email to={}: {}", toEmail, ex.getMessage());
            log.info("Portal invite/reset fallback link to={} link={}", toEmail, linkForLog);
        }
    }
}
