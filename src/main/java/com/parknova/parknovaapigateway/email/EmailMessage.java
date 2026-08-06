package com.parknova.parknovaapigateway.email;

import java.util.List;

/**
 * Generic email command published to {@code parknova.email.send} and sent by offstreet-service
 * {@code EmailMessageListener}. {@code body} is HTML; {@code attachments} is nullable/empty.
 * Field names must stay aligned with {@code com.parknova.offstreet.email.EmailMessage}.
 */
public record EmailMessage(
        String to,
        String subject,
        String body,
        List<EmailAttachment> attachments
) {
}
