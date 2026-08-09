package com.parknova.parknovaapigateway.auth;

import com.parknova.parknovaapigateway.config.ParknovaProperties;
import com.parknova.parknovaapigateway.email.EmailMessage;
import com.parknova.parknovaapigateway.email.EmailPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Builds garage-portal invite/reset emails and publishes them to {@code parknova.email.send}
 * for offstreet-service {@code EmailMessageListener} to send via SMTP.
 */
@Component
public class PortalInviteMailer {

    private static final Logger log = LoggerFactory.getLogger(PortalInviteMailer.class);

    private final ParknovaProperties properties;
    private final EmailPublisher emailPublisher;

    public PortalInviteMailer(ParknovaProperties properties, EmailPublisher emailPublisher) {
        this.properties = properties;
        this.emailPublisher = emailPublisher;
    }

    public void sendSetupInvite(String username, String toEmail, String setupUrl) {
        String subject = "Your ParkNova garage portal access";
        String body = htmlBody(
                "Your ParkNova garage portal account has been set up.",
                username,
                "System Admin",
                "Set your password",
                setupUrl,
                "Use this one-time link to set your password (valid for "
                        + properties.portal().inviteTtlHours() + " hours)."
        );
        publish(toEmail, subject, body, setupUrl);
    }

    public void sendPasswordReset(String username, String toEmail, String resetUrl) {
        String subject = "Reset your ParkNova garage portal password";
        String body = htmlBody(
                "A password reset was requested for your garage portal account.",
                username,
                "System Admin",
                "Reset password",
                resetUrl,
                "Use this one-time link to choose a new password (valid for "
                        + properties.portal().inviteTtlHours() + " hours)."
        );
        publish(toEmail, subject, body, resetUrl);
    }

    private void publish(String toEmail, String subject, String body, String linkForLog) {
        emailPublisher.publish(new EmailMessage(toEmail, subject, body, null));
        log.info("Portal invite/reset email queued to={} link={}", toEmail, linkForLog);
    }

    private String htmlBody(
            String intro,
            String username,
            String role,
            String ctaLabel,
            String actionUrl,
            String linkHint
    ) {
        String portalBase = trimSlash(properties.portal().baseUrl());
        String token = extractToken(actionUrl);

        StringBuilder inner = new StringBuilder();
        inner.append(p("Hello,"));
        inner.append(p(esc(intro)));
        inner.append("<h3>Your Portal Access</h3>");
        inner.append("<table cellpadding=\"6\" style=\"border-collapse:collapse\">");
        inner.append(row("Portal URL", portalBase));
        inner.append(row("Username", username));
        inner.append(row("Account Role", role));
        if (token != null) {
            inner.append(row("Setup Token", token));
        }
        inner.append("</table>");
        inner.append(p(esc(linkHint)));
        if (actionUrl != null && !actionUrl.isBlank()) {
            inner.append("<p><a href=\"").append(esc(actionUrl))
                    .append("\" style=\"display:inline-block;padding:10px 18px;background:#0b5;")
                    .append("color:#fff;text-decoration:none;border-radius:4px\">")
                    .append(esc(ctaLabel))
                    .append("</a></p>");
            inner.append(p("Or open this link:<br><a href=\"" + esc(actionUrl) + "\">"
                    + esc(actionUrl) + "</a>"));
        }
        inner.append("<hr><p>If you did not expect this email, contact support.</p>");
        inner.append("<p>— ParkNova</p>");

        return "<div style=\"font-family:Arial,Helvetica,sans-serif;font-size:14px;color:#222\">"
                + inner + "</div>";
    }

    private static String extractToken(String url) {
        if (url == null) {
            return null;
        }
        int idx = url.indexOf("token=");
        if (idx < 0) {
            return null;
        }
        String token = url.substring(idx + "token=".length());
        int amp = token.indexOf('&');
        return amp >= 0 ? token.substring(0, amp) : token;
    }

    private static String trimSlash(String base) {
        if (base == null || base.isBlank()) {
            return "";
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private static String row(String label, String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return "<tr><td style=\"color:#555\"><strong>" + esc(label) + "</strong></td><td>"
                + esc(value) + "</td></tr>";
    }

    private static String p(String text) {
        return "<p>" + text + "</p>";
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
