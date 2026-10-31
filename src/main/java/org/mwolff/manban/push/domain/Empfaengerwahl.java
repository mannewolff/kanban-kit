package org.mwolff.manban.push.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Welches Abo zu einem Lauf benachrichtigt wird — reine Rechnung (Issue #1998, Plan #1989 E12, E13;
 * fachliche Quelle #1961 AK 11, 12).
 *
 * <p>Ein Abo wird benachrichtigt, wenn
 *
 * <ul>
 *   <li>es vor dem ersten Auftreten des Grunds angelegt wurde ({@code gemeldetAt}) — was beim
 *       Einschalten schon besteht, wird nicht nachgemeldet,
 *   <li>es nicht abgelaufen ist (E14),
 *   <li>das Board des Laufs auf dem Gerät nicht abgeschaltet ist (E13),
 *   <li>sein Konto aktiv ist und
 *   <li>es die Läufe des Projekts sehen darf (E9).
 * </ul>
 *
 * <p><b>Lauf ohne Board (E13).</b> Er gilt für alle Boards seines Projekts und meldet, solange für
 * mindestens ein nicht archiviertes Board kein Aus-Eintrag am Abo steht. Hat das Projekt keines,
 * meldet er.
 *
 * <p>Konto und Recht kommen über {@link Konten} herein: Unter {@code ..domain..} gibt es keine
 * Abhängigkeit auf fremde Module.
 */
public final class Empfaengerwahl {

  private Empfaengerwahl() {}

  /**
   * Der Lauf, wie die Wahl ihn sieht.
   *
   * @param projectId Kennung seines Projekts
   * @param boardId das Board seines Zugangsschlüssels; {@code null}, wenn keines erkennbar ist
   * @param aktiveBoards die nicht archivierten Boards des Projekts; zählt nur ohne Board
   * @param gemeldetAt erstes Auftreten des Grunds
   */
  public record Lauf(
      long projectId, @Nullable Long boardId, Set<Long> aktiveBoards, Instant gemeldetAt) {

    public Lauf {
      aktiveBoards = Set.copyOf(aktiveBoards);
    }
  }

  /** Was die Wahl über Konten wissen muss. */
  public interface Konten {

    /** Ob das Konto freigegeben und nicht gesperrt ist. */
    boolean istAktiv(long userId);

    /** Ob das Konto die Läufe des Projekts sehen darf (E9). */
    boolean darfSehen(long userId, long projectId);
  }

  /** Die Abos, die zum Lauf benachrichtigt werden, in ihrer Reihenfolge. */
  public static List<PushAbo> empfaenger(Collection<PushAbo> abos, Lauf lauf, Konten konten) {
    return abos.stream().filter(abo -> benachrichtigt(abo, lauf, konten)).toList();
  }

  /** Ob das Abo zum Lauf benachrichtigt wird; die billigen Prüfungen zuerst. */
  public static boolean benachrichtigt(PushAbo abo, Lauf lauf, Konten konten) {
    return abo.angelegtAt().isBefore(lauf.gemeldetAt())
        && !abo.istAbgelaufen(lauf.gemeldetAt())
        && boardIstAn(abo, lauf)
        && konten.istAktiv(abo.userId())
        && konten.darfSehen(abo.userId(), lauf.projectId());
  }

  private static boolean boardIstAn(PushAbo abo, Lauf lauf) {
    Long boardId = lauf.boardId();
    if (boardId != null) {
      return abo.istAn(boardId);
    }
    return lauf.aktiveBoards().isEmpty() || lauf.aktiveBoards().stream().anyMatch(abo::istAn);
  }
}
