package com.parknova.parknovaapigateway.auth;

import com.parknova.parknovaapigateway.exception.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyValidatorTest {

    private final PasswordPolicyValidator validator = new PasswordPolicyValidator();

    @Test
    void acceptsComplexPassword() {
        assertThatCode(() -> validator.validateConfirm("SecurePass1!", "SecurePass1!"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsWeakPassword() {
        assertThatThrownBy(() -> validator.validate("password"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsMismatchedConfirm() {
        assertThatThrownBy(() -> validator.validateConfirm("SecurePass1!", "SecurePass2!"))
                .isInstanceOf(ApiException.class);
    }
}
