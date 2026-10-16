package org.mwolff.manban.auth.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Einlösen des Links aus der Mail mit dem Anmeldecode (Plan #1801, A4, A9). */
@Schema(description = "Token aus dem Link der Mail mit dem Anmeldecode.")
public record LinkLoginRequest(
    @Schema(description = "Wert des Parameters token aus dem Link.") @NotBlank @Size(max = 200)
        String token) {}
