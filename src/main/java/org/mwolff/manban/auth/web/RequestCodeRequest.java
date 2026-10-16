package org.mwolff.manban.auth.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Anforderung eines Anmeldecodes (Plan #1801, A4). */
@Schema(description = "Adresse, an die Code und Link gehen sollen.")
public record RequestCodeRequest(
    @Schema(
            description = "E-Mail-Adresse des Kontos; Groß- und Kleinschreibung zählt nicht.",
            example = "ada@example.org")
        @NotBlank
        @Email
        @Size(max = 320)
        String email) {}
