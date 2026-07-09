package org.mwolff.manban.auth.web;

import org.mwolff.manban.auth.application.MeService;
import org.mwolff.manban.auth.application.MeService.MeView;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Selbstauskunft des angemeldeten Benutzers. */
@RestController
class MeController {

    private final MeService meService;

    MeController(MeService meService) {
        this.meService = meService;
    }

    @GetMapping("/api/me")
    MeView me(@AuthenticationPrincipal Long userId) {
        return meService.load(userId);
    }
}
