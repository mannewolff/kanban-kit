package org.mwolff.manban.auth.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Anfrage zum Setzen eines neuen Passworts per Reset-Token. */
@Schema(description = "Reset-Token und neues Passwort.")
public record ResetPasswordRequest(
    @Schema(description = "Token aus dem Link der Reset-Mail.") @NotBlank String token,
    @Schema(description = "Neues Passwort, 8 bis 200 Zeichen.", format = "password")
        @NotBlank
        @Size(min = 8, max = 200)
        String newPassword) {}
