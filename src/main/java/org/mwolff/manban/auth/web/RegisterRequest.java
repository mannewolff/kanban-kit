package org.mwolff.manban.auth.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Registrierungs-Anfrage: nur E-Mail-Adresse und Anzeigename (Plan #1801). */
@Schema(description = "Daten eines neuen Kontos.")
public record RegisterRequest(
    @Schema(
            description = "E-Mail-Adresse, höchstens 320 Zeichen; dient als Anmeldename.",
            example = "ada@example.org")
        @NotBlank
        @Email
        @Size(max = 320)
        String email,
    @Schema(description = "Anzeigename, höchstens 120 Zeichen.", example = "Ada Lovelace")
        @NotBlank
        @Size(max = 120)
        String displayName) {}
