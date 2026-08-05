package com.parknova.parknovaapigateway.auth;

import com.parknova.parknovaapigateway.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class PasswordPolicyValidator {

    private static final Pattern UPPER = Pattern.compile("[A-Z]");
    private static final Pattern LOWER = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL = Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{}|;:'\",.<>/?`~\\\\]");

    public void validate(String password) {
        if (password == null || password.length() < 8) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Password must be at least 8 characters and include upper, lower, digit, and special characters");
        }
        if (!UPPER.matcher(password).find()
                || !LOWER.matcher(password).find()
                || !DIGIT.matcher(password).find()
                || !SPECIAL.matcher(password).find()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Password must be at least 8 characters and include upper, lower, digit, and special characters");
        }
    }

    public void validateConfirm(String password, String confirmPassword) {
        validate(password);
        if (confirmPassword == null || !password.equals(confirmPassword)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Password confirmation does not match");
        }
    }
}
