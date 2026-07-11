package org.mwolff.manban.auth.web;

import jakarta.validation.constraints.NotBlank;

/** Login-Anfrage. */
public record LoginRequest(@NotBlank String email, @NotBlank String password) {}
