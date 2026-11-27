package org.mwolff.manban.codeanalyse.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.mwolff.manban.codeanalyse.domain.Messung;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.domain.Permission;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use-Case des Meldens einer Messung der Codeanalyse (Issue #2275, Plan #2272 E3, E5, E8).
 *
 * <p><b>Wer darf (E5):</b> Das Projekt kommt aus der Board-Bindung des Zugangs; zusätzlich muss die
 * Person hinter dem Zugang dort {@link Permission#TICKET_CREATE} haben — dieselbe Bedingung wie
 * beim Melden einer Auslieferung. Die Ausnahme des {@link PermissionChecker} geht unverändert
 * durch.
 *
 * <p><b>Wiederholung (E3):</b> Es gilt die erste Meldung je Projekt und {@code publishedCommit};
 * jede weitere ändert nichts, auch mit anderem Inhalt, und bekommt die erste zurück. Das ist kein
 * Konflikt.
 */
@Service
public class MessungService {

  /** Wie weit {@code publishedAt} nach der Serverzeit liegen darf (E8). */
  static final Duration VORLAUF = Duration.ofMinutes(5);

  private final MessungStore store;
  private final PermissionChecker permissions;
  private final Clock clock;

  public MessungService(MessungStore store, PermissionChecker permissions, Clock clock) {
    this.store = store;
    this.permissions = permissions;
    this.clock = clock;
  }

  /**
   * Nimmt die Meldung einer Messung an und gibt die gespeicherte erste Messung zurück.
   *
   * @throws MessungInZukunftException wenn {@code publishedAt} mehr als fünf Minuten nach der
   *     Serverzeit liegt; gespeichert wird dann nichts
   */
  @Transactional
  public Messung melden(long userId, long projectId, String tokenName, GemeldeteMessung messung) {
    permissions.require(userId, projectId, Permission.TICKET_CREATE);
    Instant jetzt = clock.instant();
    if (messung.publishedAt().isAfter(jetzt.plus(VORLAUF))) {
      throw new MessungInZukunftException();
    }
    return store.speichereErste(projectId, tokenName, messung.alsMessung(), jetzt);
  }
}
