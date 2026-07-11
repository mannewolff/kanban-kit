package org.mwolff.manban.auth.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Anfrage zum Anstoßen eines Passwort-Resets. */
public record ForgotPasswordRequest(@NotBlank @Email @Size(max = 320) String email) {}
