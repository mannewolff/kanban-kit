package org.mwolff.manban.card.infrastructure.mail;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.mwolff.manban.auth.application.AuthProperties;
import org.mwolff.manban.auth.application.UserLookup;
import org.mwolff.manban.auth.application.UserSummary;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.card.application.CardNotFoundException;
import org.mwolff.manban.card.application.CardRepository;
import org.mwolff.manban.card.application.ZuweisungsBenachrichtigung;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.common.PayloadFields;
import org.mwolff.manban.common.SecureTokens;
import org.mwolff.manban.outbox.application.OutboxMessage;
import org.mwolff.manban.outbox.application.OutboxWriter;
import org.mwolff.manban.project.application.ProjectService;
import org.mwolff.manban.project.application.ProjectService.AccessibleProject;
import org.springframework.stereotype.Component;

/**
 * Merkt die Nachricht an neu Zuständige in der Outbox vor (Issue #1916), in der Transaktion der
 * Zuweisung: Scheitert sie, geht nichts hinaus, und ein langsamer SMTP-Server hält sie nicht auf.
 * Zugestellt wird nach dem Commit über {@link ZuweisungsMailHandler}.
 *
 * <p>Je Empfänger ein Eintrag mit Kartennummer, Titel, Projekt, Board, dem Namen der handelnden
 * Person und dem direkten Link der Karte (#1915) — nie mit dem Kartentext.
 *
 * <p>Schlüssel: Hash aus Karte, Empfänger und Zeitpunkt der Zuweisung. Dieselbe Zuweisung ergibt
 * einen Eintrag; wer entfernt und später wieder zugewiesen wird, bekommt eine neue Nachricht.
 */
@Component
class OutboxZuweisungsMailer implements ZuweisungsBenachrichtigung {

  private final OutboxWriter outbox;
  private final CardRepository cards;
  private final BoardService boards;
  private final ProjectService projects;
  private final UserLookup users;
  private final AuthProperties authProperties;
  private final Clock clock;

  OutboxZuweisungsMailer(
      OutboxWriter outbox,
      CardRepository cards,
      BoardService boards,
      ProjectService projects,
      UserLookup users,
      AuthProperties authProperties,
      Clock clock) {
    this.outbox = outbox;
    this.cards = cards;
    this.boards = boards;
    this.projects = projects;
    this.users = users;
    this.authProperties = authProperties;
    this.clock = clock;
  }

  @Override
  public void zugewiesen(long cardId, List<Long> neueZustaendige, long handelnderUserId) {
    Card card = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    String board = boards.requireBoardSummary(card.boardId()).name();
    String von =
        users
            .findById(handelnderUserId)
            .map(UserSummary::displayName)
            .orElse("#" + handelnderUserId);
    String link =
        authProperties.baseUrl() + "/projects/" + card.projectId() + "/karten/" + card.number();
    String zeitpunkt = clock.instant().toString();
    for (Long empfaengerId : neueZustaendige) {
      Optional<UserSummary> empfaenger = users.findById(empfaengerId);
      if (empfaenger.isEmpty()) {
        continue;
      }
      String email = empfaenger.get().email();
      outbox.schedule(
          new OutboxMessage(
              ZuweisungsMailHandler.TYPE,
              ZuweisungsMailHandler.TYPE
                  + ":"
                  + SecureTokens.sha256Hex(cardId + "\n" + empfaengerId + "\n" + zeitpunkt),
              PayloadFields.join(
                  email,
                  String.valueOf(card.number()),
                  card.title(),
                  projektName(empfaengerId, card.projectId()),
                  board,
                  von,
                  link)));
    }
  }

  /**
   * Name des Projekts aus der Sicht des Empfängers: Er ist Mitglied (geprüft in der Zuordnung),
   * also steht es in seiner Projektliste.
   */
  private String projektName(long empfaengerId, long projectId) {
    return projects.listAccessible(empfaengerId).stream()
        .filter(p -> p.id() == projectId)
        .map(AccessibleProject::name)
        .findFirst()
        .orElse("#" + projectId);
  }
}
