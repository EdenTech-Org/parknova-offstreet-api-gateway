package com.parknova.parknovaapigateway.auth.mail;

import com.parknova.parknovaapigateway.auth.otp.OtpPurpose;
import com.parknova.parknovaapigateway.config.ParknovaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final ParknovaProperties properties;

    public EmailService(JavaMailSender mailSender, ParknovaProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    public void sendOtp(String to, String otp, OtpPurpose purpose) {
        String subject = purpose == OtpPurpose.PASSWORD_RESET
                ? "ParkNova password reset code"
                : "ParkNova email verification code";
        String body = purpose == OtpPurpose.PASSWORD_RESET
                ? "Your ParkNova password reset code is: " + otp + "\n\nThis code expires in "
                + properties.otp().ttlMinutes() + " minutes."
                : "Your ParkNova email verification code is: " + otp + "\n\nThis code expires in "
                + properties.otp().ttlMinutes() + " minutes.";

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.mail().from());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
        log.info("OTP email sent to {} for purpose={}", to, purpose);
    }
}
