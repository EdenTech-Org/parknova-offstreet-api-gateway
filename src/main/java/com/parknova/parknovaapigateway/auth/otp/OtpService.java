package com.parknova.parknovaapigateway.auth.otp;

import com.parknova.parknovaapigateway.config.ParknovaProperties;
import com.parknova.parknovaapigateway.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OtpService {

    private final ParknovaProperties properties;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, OtpEntry> store = new ConcurrentHashMap<>();

    public OtpService(ParknovaProperties properties) {
        this.properties = properties;
    }

    public String generateAndStore(String email, OtpPurpose purpose) {
        String normalizedEmail = normalize(email);
        String code = generateCode(properties.otp().length());
        Instant expiresAt = Instant.now().plusSeconds(properties.otp().ttlMinutes() * 60L);
        store.put(key(normalizedEmail, purpose), new OtpEntry(code, expiresAt, 0));
        return code;
    }

    public void verify(String email, OtpPurpose purpose, String otp) {
        String normalizedEmail = normalize(email);
        String storeKey = key(normalizedEmail, purpose);
        OtpEntry entry = store.get(storeKey);

        if (entry == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "OTP not found or expired. Request a new code.");
        }
        if (Instant.now().isAfter(entry.expiresAt())) {
            store.remove(storeKey);
            throw new ApiException(HttpStatus.BAD_REQUEST, "OTP expired. Request a new code.");
        }
        if (entry.attempts() >= properties.otp().maxAttempts()) {
            store.remove(storeKey);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many invalid OTP attempts.");
        }
        if (!entry.code().equals(otp.trim())) {
            store.put(storeKey, new OtpEntry(entry.code(), entry.expiresAt(), entry.attempts() + 1));
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid OTP.");
        }

        store.remove(storeKey);
    }

    private String generateCode(int length) {
        int bound = (int) Math.pow(10, length);
        int min = (int) Math.pow(10, length - 1);
        int value = min + random.nextInt(bound - min);
        return String.valueOf(value);
    }

    private static String key(String email, OtpPurpose purpose) {
        return purpose.name() + ":" + email;
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase();
    }

    private record OtpEntry(String code, Instant expiresAt, int attempts) {
    }
}
