package org.mwolff.manban.deployment.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.mwolff.manban.deployment.domain.Auslieferungsversuch;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.domain.Permission;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use-Cases des Meldens von Auslieferungen (Issue #1833, Plan #1831 E2, E3, E4, E7).
 *
 * <p><b>Wer darf (E4):</b> Das Projekt kommt aus der Board-Bindung des Tokens; zusätzlich muss der
 * Inhaber des Tokens dort {@link Permission#TICKET_CREATE} haben — dieselbe Bedingung, unter der
 * das gebundene Token angelegt werden durfte. Mitglied ohne das Recht → 403, inzwischen entferntes
 * Mitglied → 404.
 *
 * <p><b>Doppelmeldung (E3):</b> Gleiche Kennung mit gleichem Inhalt ändert nichts, mit anderem
 * Inhalt ist sie ein Konflikt. Ersetzt wird nie. Treffen zwei Meldungen derselben neuen Kennung
 * gleichzeitig ein, fängt die Eindeutigkeit {@code (project_id, attempt_id)} die zweite ab; sie
 * endet als 409 über den {@code GlobalExceptionHandler}, und ihre Wiederholung ist {@code
 * UNCHANGED}.
 */
@Service
public class DeploymentService {

  /** Wie weit {@code finishedAt} nach der Serverzeit liegen darf (E7). */
  static final Duration VORLAUF = Duration.ofMinutes(5);

  private final DeploymentStore store;
  private final PermissionChecker permissions;
  private final Clock clock;

  public DeploymentService(DeploymentStore store, PermissionChecker permissions, Clock clock) {
    this.store = store;
    this.permissions = permissions;
    this.clock = clock;
  }

  /**
   * Nimmt die Meldung eines Versuchs an.
   *
   * @throws DeploymentInFutureException wenn {@code finishedAt} mehr als fünf Minuten nach der
   *     Serverzeit liegt
   * @throws DeploymentAttemptConflictException wenn die Kennung schon mit anderem Inhalt vorliegt
   */
  @Transactional
  public Meldeergebnis melden(long userId, long projectId, String tokenName, NeueMeldung meldung) {
    permissions.require(userId, projectId, Permission.TICKET_CREATE);
    Instant jetzt = clock.instant();
    if (meldung.finishedAt().isAfter(jetzt.plus(VORLAUF))) {
      throw new DeploymentInFutureException();
    }
    Optional<NeueMeldung> vorhanden = store.findeVersuch(projectId, meldung.attemptId());
    if (vorhanden.isPresent()) {
      if (!vorhanden.get().gleicherInhalt(meldung)) {
        throw new DeploymentAttemptConflictException();
      }
      return Meldeergebnis.UNCHANGED;
    }
    store.fuegeEin(projectId, tokenName, meldung, jetzt);
    return Meldeergebnis.CREATED;
  }

  /**
   * Der Versuch mit dem größten {@code finishedAt}, gleich welchen Ausgangs (E2): Die Commits eines
   * gescheiterten Versuchs sind schon gespeichert und müssen nicht erneut gemeldet werden.
   */
  @Transactional(readOnly = true)
  public Optional<Auslieferungsversuch> letzterStand(long userId, long projectId) {
    permissions.require(userId, projectId, Permission.TICKET_CREATE);
    return store.letzterVersuch(projectId);
  }
}
