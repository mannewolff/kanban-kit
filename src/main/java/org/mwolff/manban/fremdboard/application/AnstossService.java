package org.mwolff.manban.fremdboard.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.mwolff.manban.fremdboard.domain.FremdAbrufException;
import org.mwolff.manban.fremdboard.domain.Verbindung;
import org.mwolff.manban.project.application.PermissionChecker;
import org.springframework.stereotype.Service;

/**
 * Das Kit stößt nach einem eigenen Schritt an, und kanban-kit liest genau diese Karte sofort aus
 * dem fremden System (Issue #2167, Plan #2152 A6). Damit gilt immer der Stand dort; die Meldung
 * selbst trägt keinen Zustand.
 *
 * <p>Höchstens ein Abruf je Karte in {@link #SPERRZEIT}: Weitere Anstöße derselben Karte in dieser
 * Zeit lesen nichts. Die Sperre zählt ab dem Abruf, auch wenn er scheitert, und liegt im
 * Arbeitsspeicher — nach einem Neustart darf der erste Anstoß sofort lesen. Vor allem anderen
 * greift die Durchsatzbremse der Strecke {@code /api/kanban}.
 */
@Service
public class AnstossService {

  /** Kürzester Abstand zwischen zwei Abrufen derselben Karte. */
  public static final Duration SPERRZEIT = Duration.ofSeconds(10);

  private final PermissionChecker permissions;
  private final VerbindungRepository verbindungen;
  private final AbgleichService abgleich;
  private final Clock clock;
  private final Map<Karte, Instant> letzterAbruf = new ConcurrentHashMap<>();

  public AnstossService(
      PermissionChecker permissions,
      VerbindungRepository verbindungen,
      AbgleichService abgleich,
      Clock clock) {
    this.permissions = permissions;
    this.verbindungen = verbindungen;
    this.abgleich = abgleich;
    this.clock = clock;
  }

  /**
   * Liest die Karte aus dem fremden System, sofern sie in der Sperrzeit nicht schon gelesen wurde.
   *
   * @return {@code true}, wenn gelesen und abgeglichen wurde; {@code false}, wenn die Sperrzeit den
   *     Abruf verhinderte
   * @throws org.mwolff.manban.project.application.ProjectAccessDeniedException wenn der Aufrufer
   *     kein Mitglied des Projekts ist
   * @throws KeinFremdesBoardException wenn das Board des Projekts in kanban-kit liegt (409)
   * @throws FremdBoardNichtErreichbarException wenn das fremde System nicht antwortet (502)
   */
  public boolean anstossen(long userId, long projectId, int nummer) {
    permissions.requireMembership(userId, projectId);
    Verbindung verbindung =
        verbindungen.finde(projectId).orElseThrow(KeinFremdesBoardException::new);
    if (!abrufFrei(projectId, nummer)) {
      return false;
    }
    try {
      abgleich.karteAbgleichen(verbindung, nummer);
    } catch (FremdAbrufException e) {
      throw new FremdBoardNichtErreichbarException(String.valueOf(e.getMessage()), e);
    }
    return true;
  }

  /** Belegt die Sperrzeit der Karte, sofern sie frei ist; räumt abgelaufene Sperren ab. */
  private boolean abrufFrei(long projectId, int nummer) {
    Instant jetzt = clock.instant();
    letzterAbruf.values().removeIf(abruf -> !jetzt.isBefore(abruf.plus(SPERRZEIT)));
    return letzterAbruf.putIfAbsent(new Karte(projectId, nummer), jetzt) == null;
  }

  private record Karte(long projectId, int nummer) {}
}
