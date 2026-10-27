package org.mwolff.manban.nightrun.domain;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Ein einzelner Vorgang an einer Karte in einer Stufe der Kette (Issue #1944, Plan #1905 E1).
 *
 * <p>Anders als {@link NightRunItemStage}, die je Stufe eines Pakets höchstens einen Eintrag trägt
 * und Korrekturrunden summiert, ist jeder Versuch und jeder Prüfer ein eigener Vorgang. Die
 * Reihenfolge der Ausführung hält die Liste am Paket ({@link NightRunItem#attempts}).
 *
 * <p>Modell, Denktiefe und Verbrauch dürfen fehlen (Plan #1905 E6): Eine Meldung ohne sie wird
 * unverändert angenommen, und ein Vorgang ohne Modell zählt im Vergleich nicht mit.
 *
 * @param stage die Stufe, in der der Vorgang lief
 * @param cardNumber die Karte, an der der Vorgang arbeitete
 * @param model das Modell, wie der Runner es führt (etwa {@code claude-opus-5-5}); {@code null}
 *     heißt „nicht gemeldet"
 * @param effort die Denktiefe, wie gemeldet; {@code null} heißt „nicht gemeldet"
 * @param succeeded ob der Vorgang gelungen ist, nach dem Ausgang, den der Runner meldet
 * @param usage Verbrauch des Vorgangs; {@code null}, wenn nichts davon gemessen wurde
 * @param writtenCards die Karten der Dokumente, die der Vorgang geschrieben hat, in gemeldeter
 *     Reihenfolge — leer statt {@code null}
 */
public record NightRunAttempt(
    NightRunAttemptStage stage,
    int cardNumber,
    @Nullable String model,
    @Nullable String effort,
    boolean succeeded,
    @Nullable NightRunUsage usage,
    List<Integer> writtenCards) {

  /**
   * Die Kartenliste wird beim Anlegen kopiert und unveränderlich gemacht; fehlt sie, ist sie leer.
   */
  public NightRunAttempt {
    writtenCards = writtenCards == null ? List.of() : List.copyOf(writtenCards);
  }
}
