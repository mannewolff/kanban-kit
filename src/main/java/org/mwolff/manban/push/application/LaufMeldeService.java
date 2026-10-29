package org.mwolff.manban.push.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import org.mwolff.manban.auth.application.UserLookup;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.nightrun.application.MeldbareLaeufe.MeldbarerLauf;
import org.mwolff.manban.nightrun.domain.NightRunItem;
import org.mwolff.manban.outbox.application.OutboxMessage;
import org.mwolff.manban.outbox.application.OutboxWriter;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.push.domain.Empfaengerwahl;
import org.mwolff.manban.push.domain.Empfaengerwahl.Konten;
import org.mwolff.manban.push.domain.PushAbo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Meldet einen Lauf beim ersten Auftreten seines Meldegrunds (Issue #1998, Plan #1989 E12, E13;
 * fachliche Quelle #1961 AK 11, 12).
 *
 * <p><b>Höchstens eine Nachricht je Lauf.</b> Die Zeile in {@code push_lauf_gemeldet} entscheidet:
 * Nur der Aufruf, der sie anlegt, plant Nachrichten — in derselben Transaktion je passendem Abo
 * eine Outbox-Nachricht mit dem Schlüssel {@code push-lauf:<runId>:<aboId>}. Jeder weitere Takt und
 * jedes weitere Paket desselben Laufs finden die Zeile vor und planen nichts. Rollt die Transaktion
 * zurück, verschwinden Zeile und Nachrichten gemeinsam, und der nächste Takt versucht es erneut.
 *
 * <p><b>Wer eine Nachricht bekommt</b>, rechnet {@link Empfaengerwahl}. Abgelaufene Abos werden
 * dabei übersprungen und gelöscht (E14).
 */
@Service
public class LaufMeldeService {

  private final PushLaufMeldungRepository meldungen;
  private final PushAboRepository abos;
  private final UserLookup users;
  private final PermissionChecker permissions;
  private final BoardService boards;
  private final OutboxWriter outbox;
  private final Clock clock;

  public LaufMeldeService(
      PushLaufMeldungRepository meldungen,
      PushAboRepository abos,
      UserLookup users,
      PermissionChecker permissions,
      BoardService boards,
      OutboxWriter outbox,
      Clock clock) {
    this.meldungen = meldungen;
    this.abos = abos;
    this.users = users;
    this.permissions = permissions;
    this.boards = boards;
    this.outbox = outbox;
    this.clock = clock;
  }

  /**
   * Meldet den Lauf, sofern er noch nicht gemeldet ist.
   *
   * @return die Zahl der geplanten Nachrichten; 0, wenn der Lauf schon gemeldet war oder kein Abo
   *     passt
   */
  @Transactional
  public int melde(MeldbarerLauf lauf) {
    Instant jetzt = clock.instant();
    if (!meldungen.merke(lauf.laufId(), jetzt, lauf.grund().grund().name())) {
      return 0;
    }
    Long boardId = lauf.boardId();
    Empfaengerwahl.Lauf wahl =
        new Empfaengerwahl.Lauf(
            lauf.projectId(),
            boardId,
            boardId == null ? Set.copyOf(boards.listActiveBoardIds(lauf.projectId())) : Set.of(),
            jetzt);
    Konten konten = konten();
    int geplant = 0;
    for (PushAbo abo : abos.findAll()) {
      if (abo.istAbgelaufen(jetzt)) {
        abos.deleteById(abo.requireId());
      } else if (Empfaengerwahl.benachrichtigt(abo, wahl, konten) && plane(lauf, abo, jetzt)) {
        geplant++;
      }
    }
    return geplant;
  }

  private boolean plane(MeldbarerLauf lauf, PushAbo abo, Instant gemeldetAt) {
    NightRunItem paket = lauf.grund().paket();
    LaufNachricht nachricht =
        new LaufNachricht(
            lauf.laufId(),
            abo.requireId(),
            abo.userId(),
            lauf.projectId(),
            LaufNachricht.titel(lauf.projectName(), lauf.boardName()),
            lauf.grund().grund(),
            paket == null ? null : paket.cardNumber(),
            gemeldetAt);
    return outbox.schedule(
        new OutboxMessage(LaufNachricht.TYP, nachricht.schluessel(), nachricht.payload()));
  }

  private Konten konten() {
    return new Konten() {
      @Override
      public boolean istAktiv(long userId) {
        return users.istAktiv(userId);
      }

      @Override
      public boolean darfSehen(long userId, long projectId) {
        return permissions.hatNachtlaufZugriff(userId, projectId);
      }
    };
  }
}
