package org.mwolff.manban.auth.web;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.mwolff.manban.auth.application.LoginService;
import org.mwolff.manban.auth.application.MeService;
import org.mwolff.manban.auth.application.MeService.MeView;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.infrastructure.security.SessionCookieManager;
import org.mwolff.manban.auth.infrastructure.security.SignedSessionTokens;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Login/Logout: setzt bzw. löscht das signierte Session-Cookie. */
@RestController
@RequestMapping("/api/auth")
class SessionController {

    private final LoginService loginService;
    private final MeService meService;
    private final SignedSessionTokens tokens;
    private final SessionCookieManager cookies;

    SessionController(LoginService loginService, MeService meService,
                      SignedSessionTokens tokens, SessionCookieManager cookies) {
        this.loginService = loginService;
        this.meService = meService;
        this.tokens = tokens;
        this.cookies = cookies;
    }

    @PostMapping("/login")
    MeView login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AppUser user = loginService.login(request.email(), request.password());
        String token = tokens.issue(user.id());
        response.addHeader(HttpHeaders.SET_COOKIE, cookies.create(token).toString());
        return meService.load(user.id());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookies.clear().toString());
    }
}
