package org.mwolff.manban.auth.web;

import jakarta.validation.Valid;
import org.mwolff.manban.auth.application.RequestPasswordResetService;
import org.mwolff.manban.auth.application.ResetPasswordService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Öffentliche Endpunkte für den Passwort-Reset. */
@RestController
@RequestMapping("/api/auth")
class PasswordResetController {

    private final RequestPasswordResetService requestReset;
    private final ResetPasswordService resetPassword;

    PasswordResetController(RequestPasswordResetService requestReset, ResetPasswordService resetPassword) {
        this.requestReset = requestReset;
        this.resetPassword = resetPassword;
    }

    /** Antwortet immer mit 200 — kein Rückschluss, ob die E-Mail existiert. */
    @PostMapping("/forgot")
    @ResponseStatus(HttpStatus.OK)
    void forgot(@Valid @RequestBody ForgotPasswordRequest request) {
        requestReset.requestReset(request.email());
    }

    @PostMapping("/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reset(@Valid @RequestBody ResetPasswordRequest request) {
        resetPassword.reset(request.token(), request.newPassword());
    }
}
