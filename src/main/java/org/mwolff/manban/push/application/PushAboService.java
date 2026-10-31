package org.mwolff.manban.push.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.mwolff.manban.auth.application.SitzungsEnde;
import org.mwolff.manban.board.application.BoardNotFoundException;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.BoardView;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectService;
import org.mwolff.manban.project.application.ProjectService.AccessibleProject;
import org.mwolff.manban.push.domain.PushAbo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Push-Abos je Gerät (Issue #1997, Plan #1989 E13, E14; fachliche Quelle #1961 AK 12, 14): anlegen
 * oder auffrischen, beenden und Boards schalten.
 *
 * <p><b>Nur das eigene Abo.</b> Ein Abo wird über seinen Endpunkt angesprochen. Gehört er einem
 * anderen Konto oder ist das Abo abgelaufen, gilt er wie unbekannt ({@link
 * PushAboNichtGefundenException}); nur das Anlegen sagt, dass der Endpunkt vergeben ist ({@link
 * PushAboVergebenException}), damit das Gerät ein neues Abo holt, statt ein fremdes zu übernehmen.
 *
 * <p><b>Schaltbare Boards.</b> Geschaltet werden die nicht archivierten Boards der Projekte, deren
 * Läufe das Konto sehen darf ({@link PermissionChecker#hatNachtlaufZugriff}, Issue #1994) — nur zu
 * diesen Läufen kann eine Nachricht kommen. Ein anderes Board gilt wie unbekannt ({@link
 * BoardNotFoundException}, kein Existenz-Leak).
 *
 * <p>Die Vorschau „Ansicht als“ erreicht die Schreibaufrufe nicht: Sie weist der {@code
 * VorschauSchreibsperreFilter} vorher ab, und ein Abo gehört zum Gerät des Admins (E14).
 */
@Service
public class PushAboService {

  private final PushAboRepository abos;
  private final PermissionChecker permissions;
  private final ProjectService projects;
  private final BoardService boards;
  private final Clock clock;

  public PushAboService(
      PushAboRepository abos,
      PermissionChecker permissions,
      ProjectService projects,
      BoardService boards,
      Clock clock) {
    this.abos = abos;
    this.permissions = permissions;
    this.projects = projects;
    this.boards = boards;
    this.clock = clock;
  }

  /**
   * Ein schaltbares Board und sein Zustand auf diesem Gerät.
   *
   * @param boardId Kennung des Boards
   * @param boardName Name des Boards
   * @param projectId Kennung seines Projekts
   * @param projectName Name seines Projekts
   * @param an wahr, wenn Nachrichten zu Läufen dieses Boards kommen
   */
  public record BoardSchalter(
      long boardId, String boardName, long projectId, String projectName, boolean an) {}

  /**
   * Legt das Abo dieses Geräts an oder frischt es auf: neue Schlüssel, {@code gilt_bis} aus dem
   * Sitzungsende (E14). Ein abgelaufenes eigenes Abo beginnt dabei neu.
   *
   * @throws PushAboVergebenException wenn der Endpunkt zum laufenden Abo eines anderen Kontos
   *     gehört
   */
  @Transactional
  public void abonniere(
      long userId, String endpoint, String p256dh, String auth, SitzungsEnde sitzungsEnde) {
    Instant jetzt = clock.instant();
    Instant giltBis = sitzungsEnde.expiresAt();
    PushAbo vorhanden = abos.findByEndpoint(endpoint).orElse(null);
    if (vorhanden == null) {
      abos.save(PushAbo.neu(userId, endpoint, p256dh, auth, jetzt, giltBis));
      return;
    }
    if (vorhanden.userId() == userId) {
      abos.save(vorhanden.aufgefrischt(p256dh, auth, jetzt, giltBis));
      return;
    }
    if (!vorhanden.istAbgelaufen(jetzt)) {
      throw new PushAboVergebenException();
    }
    // Das abgelaufene Abo eines anderen Kontos ist beendet (E14); der Endpunkt ist wieder frei.
    abos.save(vorhanden.neuBegonnen(userId, p256dh, auth, jetzt, giltBis));
  }

  /**
   * Beendet das Abo dieses Geräts — beim Abmelden ruft es der Client vor dem Logout (E14).
   *
   * @throws PushAboNichtGefundenException wenn es zum Endpunkt kein eigenes laufendes Abo gibt
   */
  @Transactional
  public void beende(long userId, String endpoint) {
    abos.deleteById(eigenesAbo(userId, endpoint).requireId());
  }

  /**
   * Die schaltbaren Boards mit ihrem Zustand auf diesem Gerät, nach Projekt und Anlage geordnet.
   *
   * @throws PushAboNichtGefundenException wenn es zum Endpunkt kein eigenes laufendes Abo gibt
   */
  @Transactional(readOnly = true)
  public List<BoardSchalter> schaltbareBoards(long userId, String endpoint) {
    PushAbo abo = eigenesAbo(userId, endpoint);
    List<BoardSchalter> schalter = new ArrayList<>();
    for (AccessibleProject projekt : projects.listAccessible(userId)) {
      if (permissions.hatNachtlaufZugriff(userId, projekt.id())) {
        for (BoardView board : boards.listBoards(userId, projekt.id())) {
          schalter.add(
              new BoardSchalter(
                  board.id(), board.name(), projekt.id(), projekt.name(), abo.istAn(board.id())));
        }
      }
    }
    return schalter;
  }

  /**
   * Schaltet Nachrichten zu Läufen eines Boards auf diesem Gerät ein oder ab (E13).
   *
   * @throws PushAboNichtGefundenException wenn es zum Endpunkt kein eigenes laufendes Abo gibt
   * @throws BoardNotFoundException wenn das Board nicht besteht, archiviert ist oder seine Läufe
   *     für das Konto nicht sichtbar sind
   */
  @Transactional
  public void schalte(long userId, String endpoint, long boardId, boolean an) {
    PushAbo abo = eigenesAbo(userId, endpoint);
    if (!permissions.hatNachtlaufZugriff(userId, boards.requireProjectId(boardId))) {
      throw new BoardNotFoundException();
    }
    abos.save(abo.geschaltet(boardId, an));
  }

  private PushAbo eigenesAbo(long userId, String endpoint) {
    Instant jetzt = clock.instant();
    return abos.findByEndpoint(endpoint)
        .filter(abo -> abo.userId() == userId && !abo.istAbgelaufen(jetzt))
        .orElseThrow(PushAboNichtGefundenException::new);
  }
}
