package org.mwolff.manban;

import jakarta.servlet.http.Cookie;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.application.SessionTokens;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.auth.web.security.SessionCookieManager;
import org.springframework.boot.test.context.TestComponent;

/**
 * Meldet ein Testkonto ohne Anmeldeablauf an (Plan #1801, A15): legt das Konto an, falls es fehlt,
 * und stellt das Sitzungs-Cookie direkt über {@link SessionTokens} aus. Den echten Code-Ablauf
 * prüfen allein die Auth-Integrationstests; alle übrigen Tests brauchen nur eine gültige Sitzung.
 *
 * <p>Bewusst eine Bean, die {@link AbstractIntegrationTest} für alle Subklassen importiert: Alle
 * Kontexte bekommen denselben Import, das Context-Caching bleibt unberührt (Issue #900).
 */
// PMD.TestClassWithoutTestCases: Namensmuster-Fehlalarm — die Klasse ist eine Testhilfe ohne
// eigene Testfälle; den Namen legt Plan #1801 fest, geprüft wird sie in TestAnmeldungIT.
@SuppressWarnings("PMD.TestClassWithoutTestCases")
@TestComponent
public class TestAnmeldung {

  /**
   * Platzhalter für den Passwort-Hash, solange {@link AppUser} einen verlangt. Kein gültiger Hash —
   * eine Passwort-Anmeldung mit diesem Konto schlägt fehl. Entfällt mit dem Passwort-Hash (#1817).
   */
  private static final String KEIN_PASSWORT = "kein-passwort";

  private final AppUserRepository users;
  private final SessionTokens sessionTokens;

  TestAnmeldung(AppUserRepository users, SessionTokens sessionTokens) {
    this.users = users;
    this.sessionTokens = sessionTokens;
  }

  /**
   * Legt das Konto bestätigt und freigegeben mit der Plattform-Rolle an, falls es zur Adresse noch
   * keines gibt, und liefert es. Ein bestehendes Konto bleibt unverändert.
   */
  public AppUser konto(String email, PlatformRole rolle) {
    return users
        .findByEmail(email)
        .orElseGet(
            () -> users.save(new AppUser(null, email, KEIN_PASSWORT, "Person", true, rolle)));
  }

  /** Wie {@link #konto}, liefert dazu das Cookie einer gültigen Sitzung dieses Kontos. */
  public Cookie anmelden(String email, PlatformRole rolle) {
    return new Cookie(
        SessionCookieManager.COOKIE_NAME, sessionTokens.issue(konto(email, rolle).requireId()));
  }
}
