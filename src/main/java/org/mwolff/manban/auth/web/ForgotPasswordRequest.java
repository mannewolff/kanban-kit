package org.mwolff.manban.auth.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Anfrage zum Anstoßen eines Passwort-Resets. */
@Schema(description = "Adresse, an die der Link zum Zurücksetzen gehen soll.")
public record ForgotPasswordRequest(
    @Schema(description = "E-Mail-Adresse des Kontos.", example = "ada@example.org")
        @NotBlank
        @Email
        @Size(max = 320)
        String email) {}
