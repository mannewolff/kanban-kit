package org.mwolff.manban.config;

import java.util.List;
import org.springframework.util.AntPathMatcher;

/**
 * Geordnete Zugangs-Tabelle der API (Issue #1402, Plan #1400): Aus ihr baut {@link SecurityConfig}
 * die Regeln von {@code authorizeHttpRequests}, und dieselbe Tabelle nennt der API-Beschreibung
 * ({@link ApiBeschreibungsAnreicherung}) den Zugang je Pfad. So kann die Angabe „braucht Anmeldung
 * / geht mit Token“ nicht neben der echten Regel her veralten.
 *
 * <p>Die Reihenfolge ist Teil der Regel: Wie Spring Security gilt der <b>erste</b> Treffer — {@code
 * /api/kanban/**} steht deshalb vor der Auffangregel {@code /api/**}. Ein Pfad ohne Treffer ist
 * öffentlich; das entspricht dem abschließenden {@code anyRequest().permitAll()} für die statischen
 * Inhalte und die React-App.
 */
final class ApiZugang {

  /** Wer einen Pfad aufrufen darf. */
  enum Zugangsart {
    /** Ohne Anmeldung. */
    OEFFENTLICH("oeffentlich"),
    /** Nur mit dem Session-Cookie einer Anmeldung, nicht mit einem Zugriffs-Token. */
    NUR_SESSION("nur-session"),
    /** Nur mit einem Projekt-Token im Header {@code X-Kanban-Token}. */
    NUR_PROJEKT_TOKEN("nur-projekt-token"),
    /** Mit Session-Cookie oder einem ungebundenen Zugriffs-Token. */
    SESSION_ODER_UNGEBUNDENES_TOKEN("session-oder-ungebundenes-token");

    private final String wert;

    Zugangsart(String wert) {
      this.wert = wert;
    }

    /** Maschinenlesbare Kennung, wie sie in der API-Beschreibung steht. */
    String kennung() {
      return wert;
    }
  }

  /** Eine Gruppe von Ant-Mustern mit gemeinsamer Zugangsart. */
  record Pfadgruppe(Zugangsart zugang, List<String> muster) {}

  /** Die Tabelle, in der Reihenfolge, in der Spring Security die Regeln prüft. */
  static final List<Pfadgruppe> GRUPPEN =
      List.of(
          new Pfadgruppe(
              Zugangsart.OEFFENTLICH,
              List.of(
                  "/api/auth/register",
                  "/api/auth/verify",
                  "/api/auth/login",
                  "/api/auth/logout",
                  "/api/auth/forgot",
                  "/api/auth/reset")),
          // Token-Verwaltung nur per Cookie-Login, nicht per PAT (Least Privilege).
          new Pfadgruppe(Zugangsart.NUR_SESSION, List.of("/api/access-tokens/**")),
          // Admin-Bereich (inkl. Bootstrap) nur per Session-Login, nicht per PAT.
          // Die Admin-Autorisierung selbst erledigt der AdminService pro Endpunkt.
          new Pfadgruppe(Zugangsart.NUR_SESSION, List.of("/api/admin/**")),
          // Die API-Beschreibung (Plan #1400, E1): für jeden Angemeldeten, aber nur per Session —
          // der Fachplan unterscheidet die Anmeldung ausdrücklich vom Projekt-Token.
          new Pfadgruppe(Zugangsart.NUR_SESSION, List.of("/api/openapi", "/api/openapi.yaml")),
          // Kanban-Compat-API (tbx.mjs/board.mjs) ausschließlich per PAT. Seit Issue #947 liegt
          // hier auch POST /api/kanban/night-runs: Der Nachtlauf meldet sich mit demselben
          // projektgebundenen Token, ohne Sitzung. Keine Regel ändert sich dadurch — das Muster
          // deckt ihn ab.
          new Pfadgruppe(Zugangsart.NUR_PROJEKT_TOKEN, List.of("/api/kanban/**")),
          // Whitelist statt Blacklist (Issue #836): Die übrige API steht nur der Session und dem
          // UNGEBUNDENEN Token offen. Ein board-gebundenes Token trägt AUTH_PAT_UNBOUND nicht und
          // ist damit hier von sich aus gesperrt — auch bei einem Plattform-Admin als Ersteller.
          // Ein neuer Endpunkt unter /api/** ist für gebundene Token folglich ab dem ersten Tag
          // zu, und das ist die Absicht: Eine Prüfung je Endpunkt bliebe offen, bis jemand daran
          // denkt, sie nachzutragen. Abgewiesen wird mit 403 über den Default-AccessDeniedHandler
          // (das Token ist authentifiziert, nur nicht berechtigt), nicht mit 401.
          new Pfadgruppe(Zugangsart.SESSION_ODER_UNGEBUNDENES_TOKEN, List.of("/api/**")));

  private static final AntPathMatcher MATCHER = new AntPathMatcher();

  private ApiZugang() {}

  /** Zugangsart des ersten passenden Musters; ohne Treffer {@link Zugangsart#OEFFENTLICH}. */
  static Zugangsart zugangFuer(String pfad) {
    for (Pfadgruppe gruppe : GRUPPEN) {
      for (String muster : gruppe.muster()) {
        if (MATCHER.match(muster, pfad)) {
          return gruppe.zugang();
        }
      }
    }
    return Zugangsart.OEFFENTLICH;
  }
}
