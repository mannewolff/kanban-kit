package org.mwolff.manban.auth.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Eingabe eines abgetippten Anmeldecodes (Plan #1801, A4). */
@Schema(description = "Adresse und der sechsstellige Code aus der Mail.")
public record VerifyCodeRequest(
    @Schema(
            description = "E-Mail-Adresse, für die der Code angefordert wurde.",
            example = "ada@example.org")
        @NotBlank
        @Email
        @Size(max = 320)
        String email,
    @Schema(
            description = "Sechsstelliger Code aus der Mail, führende Nullen zählen mit.",
            example = "042817")
        @NotBlank
        @Pattern(regexp = "\\d{6}")
        String code) {}
