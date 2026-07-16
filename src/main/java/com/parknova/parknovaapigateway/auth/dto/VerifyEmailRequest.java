package com.parknova.parknovaapigateway.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerifyEmailRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 4, max = 10) String otp
) {
}
