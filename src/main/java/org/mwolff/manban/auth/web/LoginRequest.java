package org.mwolff.manban.auth.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** Login-Anfrage. */
@Schema(description = "Anmeldedaten.")
public record LoginRequest(
    @Schema(
            description = "E-Mail-Adresse des Kontos; Groß- und Kleinschreibung zählt nicht.",
            example = "ada@example.org")
        @NotBlank
        String email,
    @Schema(description = "Passwort des Kontos.", format = "password") @NotBlank String password) {}
