package org.mwolff.manban.accesstoken.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.mwolff.manban.accesstoken.application.AccessTokenService;
import org.mwolff.manban.accesstoken.application.AccessTokenService.AccessTokenView;
import org.mwolff.manban.accesstoken.application.AccessTokenService.CreatedAccessToken;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Verwaltung persönlicher API-Zugriffstokens. Nur per Cookie-Login erreichbar (SecurityConfig
 * verlangt {@code AUTH_SESSION}); nicht per PAT selbst (Least Privilege).
 */
@RestController
@RequestMapping("/api/access-tokens")
class AccessTokenController {

  private final AccessTokenService accessTokens;

  AccessTokenController(AccessTokenService accessTokens) {
    this.accessTokens = accessTokens;
  }

  /** Legt ein Token an; der Klartext wird hier GENAU EINMAL zurückgegeben. */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  CreatedAccessToken create(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody CreateAccessTokenRequest request) {
    return accessTokens.create(userId, request.name(), request.projectId(), request.boardId());
  }

  @GetMapping
  List<AccessTokenView> list(@AuthenticationPrincipal Long userId) {
    return accessTokens.list(userId);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void revoke(@AuthenticationPrincipal Long userId, @PathVariable long id) {
    accessTokens.revoke(userId, id);
  }

  /**
   * {@code projectId}/{@code boardId} sind optional: sind beide gesetzt, wird das Token an dieses
   * Board gebunden (Kanban-Compat-API). Beide leer = ungebundenes Token.
   */
  record CreateAccessTokenRequest(
      @NotBlank @Size(max = 120) String name, Long projectId, Long boardId) {}
}
