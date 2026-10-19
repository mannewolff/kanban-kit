package org.mwolff.manban.card.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.card.domain.FlussStay.EndedBy;

/**
 * Durchsatz eines Boards für die Leitstand-Prognose (Plan #1819, A5, E1 bis E4): fertige
 * Arbeitspakete je {@link Prognosetag} über die {@link #TAGE} abgeschlossenen Tage vor dem
 * laufenden. Rein, ohne Datenbank.
 *
 * <p>Fertig ist ein Paket, sobald es In Review oder Done erreicht (E1); der Wechsel zwischen beiden
 * setzt die Folge fort (E2). Ein Erreichen ist ein In-Review- oder Done-Aufenthalt, dessen zeitlich
 * voriger Aufenthalt derselben Karte einen Zustand außerhalb von In Review und Done trägt, auch
 * keinen. Ein Paket zählt höchstens einmal, mit dem Eintritt seines jüngsten Erreichens auf diesem
 * Board, und nur, wenn es seither nirgends wieder offen war (AK 17) — bis zu seinem jüngsten
 * Aufenthalt hier; was danach auf einem anderen Board geschieht, zählt dort (E4). Ein Erreichen mit
 * unbekanntem Eintritt zählt nicht (E3).
 */
public final class Durchsatzermittlung {

  /** Abgeschlossene Tage, über die der Durchsatz zählt (AK 15). */
  public static final int TAGE = 30;

  /** Mindestzahl der Tage mit einem fertigen Paket, ab der eine Prognose gerechnet wird (AK 5). */
  public static final int MINDESTTAGE = 5;

  /**
   * Der heutige Stand einer Karte — nötig, wenn ihr jüngster Aufenthalt mit {@link EndedBy#EXIT}
   * endete.
   *
   * @param status Zustand; {@code null} nach einem Artwechsel weg vom Arbeitspaket
   * @param archiviert ob die Karte archiviert ist
   * @param imPapierkorb ob die Karte im Papierkorb liegt
   */
  public record Kartenstand(@Nullable CardStatus status, boolean archiviert, boolean imPapierkorb) {

    /**
     * Ob ein Paket nach dem Austritt aus dem Fluss weiter zählt: archiviert ja — ein Abschluss
     * bleibt Geschichte, bewusst anders als {@link Flusslage#von} —, im Papierkorb und ohne Zustand
     * nicht.
     */
    boolean zaehltNachAustritt() {
      return archiviert && !imPapierkorb && status != null;
    }
  }

  /**
   * Der Durchsatz der abgeschlossenen Tage.
   *
   * @param tageswerte fertige Pakete je Tag, genau {@link #TAGE} Einträge, der älteste zuerst,
   *     Nulltage eingeschlossen
   * @param tageMitPaketen Zahl der Tage mit mindestens einem fertigen Paket
   */
  public record Durchsatz(List<Integer> tageswerte, int tageMitPaketen) {}

  private Durchsatzermittlung() {}

  /**
   * Der Durchsatz des Boards bis vor den laufenden Tag.
   *
   * @param zone Zone des Lesers; in ihr liegt die Tagesgrenze um 12 Uhr
   * @param boardId Board des Leitstands — nur dort erreichte Pakete zählen (E4)
   * @param aufenthalte Aufenthalte der Karten, auch auf anderen Boards
   * @param heute heutiger Stand je Karte; eine Karte ohne Eintrag zählt nach einem Austritt nicht
   */
  public static Durchsatz ermittle(
      ZoneId zone,
      Instant jetzt,
      long boardId,
      List<FlussStay> aufenthalte,
      Map<Long, Kartenstand> heute) {
    LocalDate erster = Prognosetag.laufenderTag(jetzt, zone).minusDays(TAGE);
    int[] werte = new int[TAGE];
    Map<Long, List<FlussStay>> jeKarte =
        aufenthalte.stream().collect(Collectors.groupingBy(FlussStay::cardId));
    jeKarte.forEach(
        (karte, folge) -> {
          FlussStay erreichen = juengstesErreichen(boardId, folge, heute.get(karte));
          if (erreichen != null) {
            long index =
                ChronoUnit.DAYS.between(erster, Prognosetag.tagDes(erreichen.enteredAt(), zone));
            if (index >= 0 && index < TAGE) {
              werte[(int) index]++;
            }
          }
        });
    List<Integer> tageswerte = new ArrayList<>(TAGE);
    for (int wert : werte) {
      tageswerte.add(wert);
    }
    int tageMitPaketen = (int) tageswerte.stream().filter(wert -> wert > 0).count();
    return new Durchsatz(Collections.unmodifiableList(tageswerte), tageMitPaketen);
  }

  /**
   * Das Erreichen, mit dem die Karte auf dem Board zählt; {@code null}, wenn sie dort nicht zählt.
   */
  private static @Nullable FlussStay juengstesErreichen(
      long boardId, List<FlussStay> aufenthalte, @Nullable Kartenstand stand) {
    List<FlussStay> folge =
        aufenthalte.stream().sorted(Comparator.comparing(FlussStay::enteredAt)).toList();
    int letzterHier = letzterAufenthaltAuf(boardId, folge);
    FlussStay erreichen = null;
    for (int i = 0; i <= letzterHier; i++) {
      FlussStay aufenthalt = folge.get(i);
      if (!fertig(aufenthalt)) {
        erreichen = null;
      } else if (aufenthalt.boardId() == boardId && i > 0 && !fertig(folge.get(i - 1))) {
        erreichen = aufenthalt;
      }
    }
    if (erreichen == null
        || !erreichen.enteredKnown()
        || !zaehltNoch(folge.get(letzterHier), stand)) {
      return null;
    }
    return erreichen;
  }

  /** Index des jüngsten Aufenthalts auf dem Board in der zeitlich geordneten Folge, sonst -1. */
  private static int letzterAufenthaltAuf(long boardId, List<FlussStay> folge) {
    int letzter = -1;
    for (int i = 0; i < folge.size(); i++) {
      if (folge.get(i).boardId() == boardId) {
        letzter = i;
      }
    }
    return letzter;
  }

  /** Ob das Paket nach seinem jüngsten Aufenthalt auf dem Board noch zählt. */
  private static boolean zaehltNoch(FlussStay juengster, @Nullable Kartenstand stand) {
    return juengster.endedBy() != EndedBy.EXIT || (stand != null && stand.zaehltNachAustritt());
  }

  private static boolean fertig(FlussStay aufenthalt) {
    return aufenthalt.status() == CardStatus.IN_REVIEW || aufenthalt.status() == CardStatus.DONE;
  }
}
