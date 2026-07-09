package org.mwolff.manban.auth.web;

import jakarta.validation.Valid;
import org.mwolff.manban.auth.application.RegisterUserService;
import org.mwolff.manban.auth.application.VerifyEmailService;
import org.mwolff.manban.auth.domain.AppUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Öffentliche Auth-Endpunkte für Registrierung und E-Mail-Verifikation. */
@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final RegisterUserService registerUser;
    private final VerifyEmailService verifyEmail;

    AuthController(RegisterUserService registerUser, VerifyEmailService verifyEmail) {
        this.registerUser = registerUser;
        this.verifyEmail = verifyEmail;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    RegisteredUserResponse register(@Valid @RequestBody RegisterRequest request) {
        AppUser user = registerUser.register(request.email(), request.password(), request.displayName());
        return new RegisteredUserResponse(user.id(), user.email(), user.emailVerified());
    }

    @GetMapping("/verify")
    @ResponseStatus(HttpStatus.OK)
    void verify(@RequestParam("token") String token) {
        verifyEmail.verify(token);
    }

    record RegisteredUserResponse(Long id, String email, boolean emailVerified) {
    }
}
