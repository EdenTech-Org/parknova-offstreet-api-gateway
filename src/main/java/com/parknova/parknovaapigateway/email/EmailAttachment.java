package com.parknova.parknovaapigateway.email;

/** A single email attachment; {@code base64Content} is the Base64-encoded file bytes. */
public record EmailAttachment(
        String filename,
        String contentType,
        String base64Content
) {
}
