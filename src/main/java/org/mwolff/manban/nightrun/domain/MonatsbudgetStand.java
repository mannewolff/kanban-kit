package org.mwolff.manban.nightrun.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * Stand des Monatsbudgets im laufenden Monat (Issue #1966, Plan #1962, fachliche Quelle #1957 AK 5
 * bis 11): Anteil, Warnstufe, Hochrechnung und Überschreitungstag — einmal im Backend gerechnet,
 * ohne Uhr und ohne Datenbank; die Oberfläche zeigt nur an.
 *
 * <p>Verglichen wird stets am <b>genauen Betrag</b>, nie am gerundeten Prozentwert oder an der
 * gerundeten Hochrechnung: 79,6 % sind keine 80 %, und eine Hochrechnung, die das Budget genau
 * trifft, liegt nicht darüber.
 *
 * @param budgetUsd das Monatsbudget; {@code null}, wenn keines festgelegt ist — dann gibt es weder
 *     Anteil noch Warnung noch Überschreitungstag, aber eine Hochrechnung (AK 11)
 * @param spentUsd der Verbrauch des laufenden Monats; {@code null}, wenn Läufe vorliegen, aber
 *     keiner Kosten gemeldet hat (Plan #1962 E2) — dann ergibt sich nichts davon
 * @param monthFirstDay der erste Tag des Monats
 * @param angebrocheneTage die seit Monatsbeginn angebrochenen Tage einschließlich des laufenden
 * @param tageImMonat die Länge des Monats in Tagen
 */
public record MonatsbudgetStand(
    @Nullable BigDecimal budgetUsd,
    @Nullable BigDecimal spentUsd,
    LocalDate monthFirstDay,
    int angebrocheneTage,
    int tageImMonat) {

  /** Ab dem wievielten angebrochenen Tag die Hochrechnung steht (AK 9). */
  private static final int ERSTER_TAG_DER_HOCHRECHNUNG = 3;

  private static final BigDecimal HUNDERT = BigDecimal.valueOf(100);
  private static final BigDecimal ACHTZIG_PROZENT = new BigDecimal("0.8");

  /** Der Anteil des Verbrauchs am Budget in Prozent, ganzzahlig abgerundet. */
  public @Nullable Integer percent() {
    if (budgetUsd == null || spentUsd == null) {
      return null;
    }
    return spentUsd.multiply(HUNDERT).divide(budgetUsd, 0, RoundingMode.FLOOR).intValueExact();
  }

  /** {@code REACHED} ab dem vollen Budget, sonst {@code EIGHTY} ab 80 % — am genauen Betrag. */
  public Warnstufe warning() {
    if (budgetUsd == null || spentUsd == null) {
      return Warnstufe.NONE;
    }
    if (spentUsd.compareTo(budgetUsd) >= 0) {
      return Warnstufe.REACHED;
    }
    if (spentUsd.compareTo(budgetUsd.multiply(ACHTZIG_PROZENT)) >= 0) {
      return Warnstufe.EIGHTY;
    }
    return Warnstufe.NONE;
  }

  /** Ob es für eine Hochrechnung noch zu früh ist — am ersten und zweiten Tag (AK 9). */
  public boolean tooEarly() {
    return angebrocheneTage < ERSTER_TAG_DER_HOCHRECHNUNG;
  }

  /**
   * Der Verbrauch, gleichmäßig je angebrochenem Tag bis Monatsende fortgeschrieben, auf Cent
   * gerundet; {@code null}, solange es zu früh ist oder kein Verbrauch gemeldet wurde.
   */
  public @Nullable BigDecimal forecastUsd() {
    if (spentUsd == null || tooEarly()) {
      return null;
    }
    return spentUsd
        .multiply(BigDecimal.valueOf(tageImMonat))
        .divide(BigDecimal.valueOf(angebrocheneTage), 2, RoundingMode.HALF_UP);
  }

  /**
   * Der erste Tag, an dessen Ende die fortgeschriebene Summe das Budget erreicht oder übersteigt
   * (AK 10) — nur, solange der Verbrauch unter dem Budget und die Hochrechnung darüber liegt. Tag
   * {@code d = ⌈Budget × angebrochene Tage / Verbrauch⌉}, als Datum {@code monthFirstDay + (d −
   * 1)}.
   */
  public @Nullable LocalDate exceedDate() {
    if (budgetUsd == null || spentUsd == null || tooEarly()) {
      return null;
    }
    BigDecimal tage = BigDecimal.valueOf(angebrocheneTage);
    boolean hochrechnungUeberBudget =
        spentUsd.multiply(BigDecimal.valueOf(tageImMonat)).compareTo(budgetUsd.multiply(tage)) > 0;
    if (spentUsd.compareTo(budgetUsd) >= 0 || !hochrechnungUeberBudget) {
      return null;
    }
    long tag = budgetUsd.multiply(tage).divide(spentUsd, 0, RoundingMode.CEILING).longValueExact();
    return monthFirstDay.plusDays(tag - 1);
  }
}
