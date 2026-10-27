package org.mwolff.manban.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mwolff.manban.auth.application.InvalidLoginCodeException;
import org.mwolff.manban.auth.application.LoginCodeLimitExceededException;
import org.mwolff.manban.auth.application.LoginService;
import org.mwolff.manban.auth.application.MeService;
import org.mwolff.manban.auth.application.MeService.MeView;
import org.mwolff.manban.auth.application.RequestLoginCodeService;
import org.mwolff.manban.auth.application.SessionTokens;
import org.mwolff.manban.auth.application.UserDisabledException;
import org.mwolff.manban.auth.application.UserNotApprovedException;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.auth.web.security.SessionCookieManager;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Tests der Endpunkte der Code-Anmeldung (Plan #1801, A4, A5, A6, A9, E6), Dienste gemockt. Über
 * einen standalone MockMvc, damit Mapping, {@code @Valid} und die Statuscodes der Ausnahmen
 * tatsächlich greifen.
 */
class LoginCodeControllerTest {

  private static final String EMAIL = "ada@example.org";
  private static final String CODE = "012345";
  private static final String TOKEN = "link-token";

  private static ValidatorFactory validatorFactory;
  private static Validator validator;

  private RequestLoginCodeService requestCode;
  private LoginService loginService;
  private MeService meService;
  private SessionTokens tokens;
  private SessionCookieManager cookies;
  private MockMvc mvc;

  @BeforeAll
  static void createValidator() {
    validatorFactory = Validation.buildDefaultValidatorFactory();
    validator = validatorFactory.getValidator();
  }

  @AfterAll
  static void closeValidator() {
    validatorFactory.close();
  }

  @BeforeEach
  void setUp() {
    requestCode = mock(RequestLoginCodeService.class);
    loginService = mock(LoginService.class);
    meService = mock(MeService.class);
    tokens = mock(SessionTokens.class);
    cookies = mock(SessionCookieManager.class);
    mvc =
        MockMvcBuilders.standaloneSetup(
                new LoginCodeController(requestCode, loginService, meService, tokens, cookies))
            .build();
  }

  // --- POST /api/auth/code/request ---

  @Test
  void request_answers202WithoutBody() throws Exception {
    mvc.perform(json("/api/auth/code/request", "{\"email\":\"" + EMAIL + "\"}"))
        .andExpect(status().isAccepted())
        .andExpect(content().string(""))
        .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

    verify(requestCode).requestCode(EMAIL);
  }

  @Test
  void request_answers429_whenLimitExceeded() throws Exception {
    doThrow(new LoginCodeLimitExceededException()).when(requestCode).requestCode(EMAIL);

    mvc.perform(json("/api/auth/code/request", "{\"email\":\"" + EMAIL + "\"}"))
        .andExpect(status().isTooManyRequests());
  }

  @Test
  void request_rejectsInvalidEmail_beforeReachingService() throws Exception {
    mvc.perform(json("/api/auth/code/request", "{\"email\":\"keine-adresse\"}"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(requestCode);
  }

  // --- POST /api/auth/code/verify ---

  @Test
  void verify_setsSessionCookieAndReturnsMeView() throws Exception {
    givenSessionFor(loginWithCodeReturns());

    mvc.perform(json("/api/auth/code/verify", verifyBody(CODE)))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.SET_COOKIE, cookie().toString()))
        .andExpect(jsonPath("$.userId").value(7))
        .andExpect(jsonPath("$.email").value(EMAIL));
  }

  @Test
  void verify_answers401_forInvalidCode() throws Exception {
    when(loginService.loginWithCode(EMAIL, CODE)).thenThrow(new InvalidLoginCodeException());

    mvc.perform(json("/api/auth/code/verify", verifyBody(CODE)))
        .andExpect(status().isUnauthorized())
        .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    verifyNoInteractions(tokens);
  }

  @Test
  void verify_answers403_forDisabledAccount() throws Exception {
    when(loginService.loginWithCode(EMAIL, CODE)).thenThrow(new UserDisabledException());

    mvc.perform(json("/api/auth/code/verify", verifyBody(CODE)))
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    verifyNoInteractions(tokens);
  }

  @Test
  void verify_answers403_forPendingAccount() throws Exception {
    when(loginService.loginWithCode(EMAIL, CODE)).thenThrow(new UserNotApprovedException());

    mvc.perform(json("/api/auth/code/verify", verifyBody(CODE)))
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    verifyNoInteractions(tokens);
  }

  @Test
  void verify_rejectsMalformedCode_beforeReachingService() throws Exception {
    mvc.perform(json("/api/auth/code/verify", verifyBody("12345")))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(loginService);
  }

  // --- POST /api/auth/code/link ---

  @Test
  void link_setsSessionCookieAndReturnsMeView() throws Exception {
    AppUser user = user();
    when(loginService.loginWithLink(TOKEN)).thenReturn(user);
    givenSessionFor(user);

    mvc.perform(json("/api/auth/code/link", "{\"token\":\"" + TOKEN + "\"}"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.SET_COOKIE, cookie().toString()))
        .andExpect(jsonPath("$.userId").value(7));
  }

  @Test
  void link_answers401_forInvalidToken() throws Exception {
    when(loginService.loginWithLink(TOKEN)).thenThrow(new InvalidLoginCodeException());

    mvc.perform(json("/api/auth/code/link", "{\"token\":\"" + TOKEN + "\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
  }

  @Test
  void link_answers403_forDisabledOrPendingAccount() throws Exception {
    when(loginService.loginWithLink(TOKEN))
        .thenThrow(new UserDisabledException())
        .thenThrow(new UserNotApprovedException());

    mvc.perform(json("/api/auth/code/link", "{\"token\":\"" + TOKEN + "\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(json("/api/auth/code/link", "{\"token\":\"" + TOKEN + "\"}"))
        .andExpect(status().isForbidden());
    verifyNoInteractions(tokens);
  }

  @Test
  void link_rejectsBlankToken_beforeReachingService() throws Exception {
    mvc.perform(json("/api/auth/code/link", "{\"token\":\" \"}"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(loginService);
  }

  /** A9: Ein {@code GET} meldet nie an — weder Code noch Link haben einen GET-Handler. */
  @ParameterizedTest
  @ValueSource(strings = {"/api/auth/code/request", "/api/auth/code/verify", "/api/auth/code/link"})
  void get_isNotMapped(String path) throws Exception {
    mvc.perform(get(path).param("token", TOKEN)).andExpect(status().isMethodNotAllowed());

    verifyNoInteractions(requestCode, loginService, tokens);
  }

  // --- Validierung der Request-Records ---

  @Test
  void requestCodeRequest_requiresValidEmailOfAtMost320Characters() {
    assertThat(validator.validate(new RequestCodeRequest(EMAIL))).isEmpty();
    assertThat(validator.validate(new RequestCodeRequest(""))).isNotEmpty();
    assertThat(validator.validate(new RequestCodeRequest("keine-adresse"))).hasSize(1);
    assertThat(validator.validate(new RequestCodeRequest(longEmail()))).isNotEmpty();
  }

  @Test
  void verifyCodeRequest_requiresEmailAndSixDigits() {
    assertThat(validator.validate(new VerifyCodeRequest(EMAIL, "000000"))).isEmpty();
    assertThat(validator.validate(new VerifyCodeRequest(EMAIL, "999999"))).isEmpty();
    assertThat(validator.validate(new VerifyCodeRequest("", CODE))).isNotEmpty();
    assertThat(validator.validate(new VerifyCodeRequest("keine-adresse", CODE))).hasSize(1);
    assertThat(validator.validate(new VerifyCodeRequest(longEmail(), CODE))).isNotEmpty();
    assertThat(validator.validate(new VerifyCodeRequest(EMAIL, "12345"))).hasSize(1);
    assertThat(validator.validate(new VerifyCodeRequest(EMAIL, "1234567"))).hasSize(1);
    assertThat(validator.validate(new VerifyCodeRequest(EMAIL, "12a456"))).hasSize(1);
    assertThat(validator.validate(new VerifyCodeRequest(EMAIL, ""))).isNotEmpty();
  }

  @Test
  void linkLoginRequest_requiresTokenOfAtMost200Characters() {
    assertThat(validator.validate(new LinkLoginRequest(TOKEN))).isEmpty();
    assertThat(validator.validate(new LinkLoginRequest("x".repeat(200)))).isEmpty();
    assertThat(validator.validate(new LinkLoginRequest(" "))).hasSize(1);
    assertThat(validator.validate(new LinkLoginRequest("x".repeat(201)))).hasSize(1);
  }

  private AppUser loginWithCodeReturns() {
    AppUser user = user();
    when(loginService.loginWithCode(EMAIL, CODE)).thenReturn(user);
    return user;
  }

  private void givenSessionFor(AppUser user) {
    when(tokens.issue(user.requireId())).thenReturn("session-token");
    when(cookies.create("session-token")).thenReturn(cookie());
    when(meService.load(user.requireId()))
        .thenReturn(
            new MeView(
                7L,
                EMAIL,
                "Ada",
                PlatformRole.USER,
                List.of(),
                org.mwolff.manban.auth.domain.ColorScheme.SYSTEM,
                null));
  }

  private static AppUser user() {
    return new AppUser(7L, EMAIL, "Ada", true, PlatformRole.USER);
  }

  private static ResponseCookie cookie() {
    return ResponseCookie.from(SessionCookieManager.COOKIE_NAME, "session-token").build();
  }

  private static String verifyBody(String code) {
    return "{\"email\":\"" + EMAIL + "\",\"code\":\"" + code + "\"}";
  }

  private static String longEmail() {
    return "a".repeat(310) + "@example.org";
  }

  private static org.springframework.test.web.servlet.RequestBuilder json(
      String path, String body) {
    return post(path).contentType(MediaType.APPLICATION_JSON).content(body);
  }
}
