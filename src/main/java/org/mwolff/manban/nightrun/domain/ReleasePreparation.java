package org.mwolff.manban.nightrun.domain;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Die Morgenmeldung eines Laufs: ob und wie er eine Veröffentlichung vorbereitet hat (Issue #1456,
 * Plan #1447 E12).
 *
 * <p>Zu einem Lauf gehört höchstens eine; eine neue Meldung desselben Laufs ersetzt sie wie die
 * übrigen Felder. Ein Lauf ohne Morgenmeldung trägt {@code null} statt einer Meldung aus lauter
 * fehlenden Feldern.
 *
 * <p>{@code releaseFiles} aus der Meldung fehlt hier mit Absicht: Das Board nimmt das Feld an, aber
 * es speichert es nicht (E12).
 *
 * @param result Ausgang der Vorbereitung
 * @param commitHash Kennung des vorbereiteten Stands, mit der der Mensch ihn außerhalb des Boards
 *     wiederfindet
 * @param version Beschriftung des Stands, etwa die Versionsnummer
 * @param redCheck die fehlgeschlagene Prüfung bei {@link ReleasePreparationResult#RED}
 * @param cardNumbers die enthaltenen Arbeitspakete, in gemeldeter Reihenfolge
 * @param redCards die von der fehlgeschlagenen Prüfung betroffenen Karten
 * @param pending die noch offenen Prüfungen bei {@link ReleasePreparationResult#GREEN_PENDING}
 * @param receivedAt Eingang der Meldung nach der Uhr des Servers — die Meldung selbst trägt keinen
 *     Zeitpunkt der Vorbereitung (E12)
 */
public record ReleasePreparation(
    ReleasePreparationResult result,
    @Nullable String commitHash,
    @Nullable String version,
    @Nullable String redCheck,
    List<Integer> cardNumbers,
    List<Integer> redCards,
    List<String> pending,
    Instant receivedAt) {

  /** Die Listen werden beim Anlegen kopiert und unveränderlich gemacht. */
  public ReleasePreparation {
    cardNumbers = List.copyOf(cardNumbers);
    redCards = List.copyOf(redCards);
    pending = List.copyOf(pending);
  }
}
