package org.mwolff.manban.auth.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Anfrage zum Setzen eines neuen Passworts per Reset-Token. */
public record ResetPasswordRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 8, max = 200) String newPassword) {
}
