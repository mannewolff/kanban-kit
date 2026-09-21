package org.mwolff.manban.nightrun.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.nightrun.domain.NightRunErrorClass;
import org.mwolff.manban.nightrun.domain.NightRunKind;
import org.mwolff.manban.nightrun.domain.NightRunStage;
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
 * (Issue #964), gehört zu keiner Nacht mehr, die die Auswertung als Lauf zeigen könnte — und {@link
 * #retentionBoundary} macht sichtbar, ob und wo eine Gattung tatsächlich verdrängt hat (Plan E8,
 * Issue #1071).
 *
 * <p>Fehlende Verbrauchsangaben bleiben fehlend: Eine Summe über lauter {@code NULL} ist {@code
 * NULL} und wird nie 0 (Plan E5).
 *
 * <p><b>Getrennt nach Gattung</b> (Issue #1013, Plan #1007): Läufe und interaktive Sitzungen liegen
 * in derselben Tabelle, unterschieden durch {@code kind}. Die Aggregate liefern deshalb je Gattung
 * einen eigenen Satz Summen; die Gesamtsumme entsteht daraus durch Addition und ist damit per
 * Konstruktion <b>genau</b> die Summe der beiden Anteile (#984 AK 5) — nicht eine zweite,
 * unabhängig gerechnete Zahl, die davon abweichen könnte.
 */
public interface NightRunUsageRepository {

  /**
   * Die Nächte innerhalb der Spanne, ältester zuerst. Eine Nacht sind alle Läufe, die in dieselbe
   * zonenlokale Nacht fallen — mit der Tagesgrenze 12:00: Ein Lauf vor 12:00 zählt zur
   * vorangegangenen Nacht (Plan E21, Issue #969).
   */
  List<NightTotals> totalsPerNight(long projectId, Instant from, Instant to, ZoneId zone);

  /**
   * Die Summen je Kartennummer über alle Anläufe in der Spanne, aufsteigend nach Nummer — der
   * Verbrauch je Gattung getrennt (Issue #1013).
   */
  List<CardTotals> totalsPerCard(long projectId, Instant from, Instant to);

  /**
   * Die Summen je Stufe der Kette über alle Vorgänge der Spanne, in der Reihenfolge der Kette
   * (Issue #1114, Plan #1110, #993 AK 8).
   *
   * <p>Gezählt wird über {@code night_run_item_stage}. Ein Lauf, der keine Kette ist, trägt keine
   * Stufen und erscheint hier nicht — und es gibt <b>keine</b> Zeile „ohne Stufe" nach dem Muster
   * von „ohne Vorhaben" (Plan E6): Sie trüge bei einem Umsetzungs-Lauf den Verbrauch einer ganzen
   * Nacht und legte eine Erfassungslücke nahe, wo keine ist.
   */
  List<StageTotals> totalsPerStage(long projectId, Instant from, Instant to);

  /**
   * Die Gesamtsummen der Spanne — Lauf-Summen und Summe über die Arbeitspakete getrennt, damit der
   * nicht zuordenbare Rest als Differenz entstehen kann (Plan E6).
   */
  PeriodTotals totals(long projectId, Instant from, Instant to);

  /**
   * Die Summen über <b>alle</b> aufbewahrten Läufe und Sitzungen des Projekts — ohne Zeitspanne
   * (Plan E19, #984 AK 4).
   *
   * <p>Ein eigener Zugriff und kein Sonderfall von {@link #totals}: Eine Lebenszeit ist kein {@code
   * NightRunPeriod}. Sie hat keinen ersten Tag, keinen Vorzeitraum und lässt sich nicht verschieben
   * — ein vierter Wert in {@code NightRunPeriodType} träfe jede der drei bestehenden Arten mit
   * einem Sonderfall.
   */
  LifetimeTotals lifetimeTotals(long projectId);

  /**
   * Startzeitpunkt des ältesten aufbewahrten Laufs; leer, wenn das Projekt keinen hat (Plan E8).
   */
  Optional<Instant> oldestRetainedRunStart(long projectId);

  /**
   * Zahl und ältester Startzeitpunkt der aufbewahrten Einträge, je Gattung getrennt (Issue #1071,
   * Plan #1067, E8) — die Grundlage der Aufbewahrungsgrenze: Ob eine Gattung ihren Ringpuffer
   * gefüllt hat, entscheidet erst der Aufrufer anhand von {@code NightRunProperties}, denn eine in
   * diese Abfrage gegossene Zahl liefe beim nächsten Wert der Property auseinander. Anders als
   * {@link #oldestRetainedRunStart} — dem gattungsübergreifend ältesten Eintrag — liefert dies je
   * Gattung eine eigene Zahl und einen eigenen Zeitpunkt, unabhängig davon, ob deren Ringpuffer
   * voll ist.
   *
   * <p>Eine Gattung ohne aufbewahrten Eintrag liefert Zahl 0 und keinen Zeitpunkt.
   */
  List<RetainedByKind> retentionBoundary(long projectId);

  /**
   * Zahl und ältester Startzeitpunkt der aufbewahrten Einträge einer Gattung (Plan E8, Issue
   * #1071).
   *
   * @param kind die Gattung
   * @param count Zahl der aufbewahrten Einträge dieser Gattung
   * @param oldestStart Startzeitpunkt des ältesten aufbewahrten Eintrags dieser Gattung; {@code
   *     null}, wenn die Gattung keinen aufbewahrten Eintrag hat
   */
  record RetainedByKind(NightRunKind kind, long count, @Nullable Instant oldestStart) {}

  /**
   * Die Summen einer einzelnen Gattung (Issue #1013).
   *
   * @param runCount Zahl der Einträge dieser Gattung — Läufe bzw. Sitzungen
   * @param runUsage Summe der gemeldeten Verbräuche der Einträge
   * @param itemUsage Summe der Verbräuche ihrer Arbeitspakete
   */
  record KindTotals(long runCount, NightRunUsage runUsage, NightRunUsage itemUsage) {}

  /**
   * Beide Gattungen nebeneinander. Die Gesamtwerte sind hier abgeleitet und nicht gespeichert:
   * Damit <b>ist</b> die Summe die Addition der beiden Anteile (#984 AK 5), statt es nur zu sein,
   * solange zwei getrennte Rechnungen übereinstimmen.
   *
   * @param night Anteil der Nachtläufe
   * @param interactive Anteil der interaktiven Sitzungen
   */
  record TotalsByKind(KindTotals night, KindTotals interactive) {

    /** Zahl aller Einträge — Läufe <b>und</b> Sitzungen. */
    public long runCount() {
      return night.runCount() + interactive.runCount();
    }

    /** Summe der gemeldeten Verbräuche über beide Gattungen. */
    public NightRunUsage runUsage() {
      return night.runUsage().plus(interactive.runUsage());
    }

    /** Summe der Verbräuche der Arbeitspakete über beide Gattungen. */
    public NightRunUsage itemUsage() {
      return night.itemUsage().plus(interactive.itemUsage());
    }
  }

  /**
   * Eine Nacht als Tagesgruppe.
   *
   * @param night Datum, an dem die Nacht beginnt
   * @param durationMs Summe der Laufdauern über beide Gattungen
   * @param cardCount Zahl der verschiedenen Kartennummern über alle Einträge — mehrere Anläufe an
   *     derselben Karte zählen als eine
   * @param byKind die Summen je Gattung; Gesamtwerte entstehen daraus
   * @param errorClasses die in dieser Nacht vorkommenden Fehlerklassen
   */
  record NightTotals(
      LocalDate night,
      long durationMs,
      long cardCount,
      TotalsByKind byKind,
      Set<NightRunErrorClass> errorClasses) {

    /** Zahl der Einträge dieser Nacht — Läufe und Sitzungen. */
    public long runCount() {
      return byKind.runCount();
    }

    /** Summe der gemeldeten Verbräuche dieser Nacht. */
    public NightRunUsage runUsage() {
      return byKind.runUsage();
    }

    /** Summe der Verbräuche der Arbeitspakete dieser Nacht. */
    public NightRunUsage itemUsage() {
      return byKind.itemUsage();
    }
  }

  /**
   * Eine Kartennummer mit ihren Anläufen in der Spanne.
   *
   * @param cardNumber projektweite Kartennummer
   * @param attemptCount Zahl der Anläufe über beide Gattungen
   * @param durationMs Summe der Dauern; {@code null}, wenn kein Anlauf eine Dauer trägt
   * @param nightUsage Summe der Verbräuche aus Nachtläufen
   * @param interactiveUsage Summe der Verbräuche aus interaktiven Sitzungen
   */
  record CardTotals(
      int cardNumber,
      long attemptCount,
      @Nullable Long durationMs,
      NightRunUsage nightUsage,
      NightRunUsage interactiveUsage) {

    /** Summe über beide Gattungen. */
    public NightRunUsage usage() {
      return nightUsage.plus(interactiveUsage);
    }
  }

  /**
   * Eine Stufe der Kette mit den Summen ihrer Vorgänge (Issue #1114).
   *
   * <p>Die Wanduhr-Dauer steht neben und nicht in {@code usage} — dem Muster von {@link CardTotals}
   * folgend: Sie ist keine Verbrauchsangabe des Modells, sondern die Zeit, die die Stufe insgesamt
   * gedauert hat.
   *
   * @param stage die Stufe
   * @param itemCount Zahl der Vorgänge, die diese Stufe durchlaufen haben
   * @param durationMs Summe der Wanduhr-Dauern; {@code null}, wenn kein Vorgang eine trägt
   * @param usage Summe der Verbräuche — Kosten, Tokenmengen, Modellzeit und Züge
   */
  record StageTotals(
      NightRunStage stage, long itemCount, @Nullable Long durationMs, NightRunUsage usage) {}

  /**
   * Die Summen über die ganze Laufzeit eines Projekts (Issue #1014). Ohne Spanne gibt es keine
   * Laufdauer zu zeigen — die Lebenszeit beantwortet „was hat es insgesamt gekostet", nicht „wie
   * lange lief es".
   *
   * @param cardCount Zahl der verschiedenen Kartennummern über alle aufbewahrten Einträge
   * @param byKind die Summen je Gattung; Läufe, Sitzungen und die Gesamtsumme entstehen daraus
   */
  record LifetimeTotals(long cardCount, TotalsByKind byKind) {}

  /**
   * Die Gesamtsummen einer Spanne.
   *
   * @param durationMs Summe der Laufdauern; 0 ohne Eintrag — eine Dauer ist immer gemessen
   * @param cardCount Zahl der verschiedenen Kartennummern
   * @param byKind die Summen je Gattung; Gesamtwerte entstehen daraus
   */
  record PeriodTotals(long durationMs, long cardCount, TotalsByKind byKind) {

    /** Zahl der Einträge der Spanne — Läufe und Sitzungen. */
    public long runCount() {
      return byKind.runCount();
    }

    /** Summe der gemeldeten Verbräuche der Spanne. */
    public NightRunUsage runUsage() {
      return byKind.runUsage();
    }

    /** Summe der Verbräuche der Arbeitspakete der Spanne. */
    public NightRunUsage itemUsage() {
      return byKind.itemUsage();
    }
  }
}
