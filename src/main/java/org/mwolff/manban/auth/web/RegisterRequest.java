package org.mwolff.manban.auth.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Registrierungs-Anfrage. */
@Schema(description = "Daten eines neuen Kontos.")
public record RegisterRequest(
    @Schema(
            description = "E-Mail-Adresse, höchstens 320 Zeichen; dient als Anmeldename.",
            example = "ada@example.org")
        @NotBlank
        @Email
        @Size(max = 320)
        String email,
    @Schema(description = "Passwort, 8 bis 200 Zeichen.", format = "password")
        @NotBlank
        @Size(min = 8, max = 200)
        String password,
    @Schema(description = "Anzeigename, höchstens 120 Zeichen.", example = "Ada Lovelace")
        @NotBlank
        @Size(max = 120)
        String displayName) {}
