package org.mwolff.manban.nightrun.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.nightrun.domain.NightRunErrorClass;
import org.mwolff.manban.nightrun.domain.NightRunUsage;

/**
 * Ausgehender Port der Verbrauchs-Auswertung: Lesezugriffe über {@code night_run} und {@code
 * night_run_item} (Issue #937, Plan #933).
 *
 * <p>Summiert wird im Backend und nicht im Browser (Plan E1). Die Spannen kommen als fertige {@link
 * Instant} aus {@code NightRunPeriod}; der Port filtert mit {@code from} einschließlich und {@code
 * to} ausschließlich über {@code started_at} des Laufs.
 *
 * <p><b>Gezählt werden die aufbewahrten Läufe.</b> Ein Arbeitspaket, dessen Lauf verdrängt wurde
 * (Issue #964), gehört zu keiner Nacht mehr, die die Auswertung als Lauf zeigen könnte — und die
 * Grenze der Aufbewahrung macht {@link #oldestRetainedRunStart} sichtbar (Plan E8).
 *
 * <p>Fehlende Verbrauchsangaben bleiben fehlend: Eine Summe über lauter {@code NULL} ist {@code
 * NULL} und wird nie 0 (Plan E5).
 */
public interface NightRunUsageRepository {

  /**
   * Die Nächte innerhalb der Spanne, ältester zuerst. Eine Nacht sind alle Läufe, die in dieselbe
   * zonenlokale Nacht fallen — mit der Tagesgrenze 12:00: Ein Lauf vor 12:00 zählt zur
   * vorangegangenen Nacht (Plan E21, Issue #969).
   */
  List<NightTotals> totalsPerNight(long projectId, Instant from, Instant to, ZoneId zone);

  /** Die Summen je Kartennummer über alle Anläufe in der Spanne, aufsteigend nach Nummer. */
  List<CardTotals> totalsPerCard(long projectId, Instant from, Instant to);

  /**
   * Die Gesamtsummen der Spanne — Lauf-Summen und Summe über die Arbeitspakete getrennt, damit der
   * nicht zuordenbare Rest als Differenz entstehen kann (Plan E6).
   */
  PeriodTotals totals(long projectId, Instant from, Instant to);

  /**
   * Startzeitpunkt des ältesten aufbewahrten Laufs; leer, wenn das Projekt keinen hat (Plan E8).
   */
  Optional<Instant> oldestRetainedRunStart(long projectId);

  /**
   * Eine Nacht als Tagesgruppe.
   *
   * @param night Datum, an dem die Nacht beginnt
   * @param runCount Zahl der Läufe dieser Nacht
   * @param durationMs Summe der Laufdauern
   * @param cardCount Zahl der verschiedenen Kartennummern über alle Läufe — mehrere Anläufe an
   *     derselben Karte zählen als eine
   * @param runUsage Summe der gemeldeten Lauf-Verbräuche
   * @param itemUsage Summe der Verbräuche der Arbeitspakete
   * @param errorClasses die in dieser Nacht vorkommenden Fehlerklassen
   */
  record NightTotals(
      LocalDate night,
      long runCount,
      long durationMs,
      long cardCount,
      NightRunUsage runUsage,
      NightRunUsage itemUsage,
      Set<NightRunErrorClass> errorClasses) {}

  /**
   * Eine Kartennummer mit ihren Anläufen in der Spanne.
   *
   * @param cardNumber projektweite Kartennummer
   * @param attemptCount Zahl der Anläufe
   * @param durationMs Summe der Dauern; {@code null}, wenn kein Anlauf eine Dauer trägt
   * @param usage Summe der Verbräuche
   */
  record CardTotals(
      int cardNumber, long attemptCount, @Nullable Long durationMs, NightRunUsage usage) {}

  /**
   * Die Gesamtsummen einer Spanne.
   *
   * @param runCount Zahl der Läufe
   * @param durationMs Summe der Laufdauern; 0 ohne Lauf — eine Dauer ist immer gemessen
   * @param cardCount Zahl der verschiedenen Kartennummern
   * @param runUsage Summe der gemeldeten Lauf-Verbräuche
   * @param itemUsage Summe der Verbräuche der Arbeitspakete
   */
  record PeriodTotals(
      long runCount,
      long durationMs,
      long cardCount,
      NightRunUsage runUsage,
      NightRunUsage itemUsage) {}
}
