package org.mwolff.manban.nightrun.application;

import java.time.Duration;
import org.mwolff.manban.nightrun.domain.NightRunKind;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Konfiguration der Nachtlauf-Auswertung (Issue #722).
 *
 * <p>Die Grenze des Ringpuffers steht als Property neben dem Service, nicht in der Web-Schicht und
 * nicht in der Datenbank (Plan #718, A10) — Vorbild ist {@code manban.storage.max-per-card}. Ein
 * Datenbank-Trigger wäre im Test unsichtbar.
 *
 * <p><b>Warum 190 Läufe</b> (Issue #935, Plan #933 E7): Die Auswertung zeigt einen Monat neben
 * seinem Vormonat, und verdrängt wird nach den jüngsten Läufen — der laufende Monat belegt den
 * Ringpuffer mit. Bei zwei Läufen je Nacht sind das laufender Monat (bis 62) plus zuletzt
 * abgeschlossener Monat (62) plus Vormonat (62), zusammen bis zu 186. Unbegrenzt aufzubewahren
 * scheidet aus: Jedes Arbeitspaket trägt bis zu 4.000 Zeichen Auszug.
 *
 * <p>Läufe und verwaiste Arbeitspakete haben getrennte Grenzen (Issue #966): Seit Issue #964
 * überdauert ein Paket die Verdrängung seines Laufs, und ohne eigene Grenze wüchse {@code
 * night_run_item} unbegrenzt. Pakete eines aufbewahrten Laufs zählen nicht mit — sie fallen erst
 * mit ihrem Lauf.
 *
 * <p><b>Warum je Gattung ein eigenes Grenzenpaar</b> (Issue #1011, Plan #1007 E14): Interaktive
 * Sitzungen sind deutlich häufiger als Nachtläufe. Unter einer gemeinsamen Grenze verdrängten sie
 * die Nachtläufe binnen Tagen und zerstörten die bestehende Auswertung. {@link
 * #maxRunsFor(NightRunKind)} und {@link #maxOrphanItemsFor(NightRunKind)} wählen das Paar; die
 * Verdrängung selbst kappt innerhalb einer Gattung und berührt die andere nie.
 *
 * @param maxPerProject Zahl der je Projekt aufbewahrten Läufe; fehlend oder kleiner als 1 ergibt
 *     190
 * @param maxItemsPerProject Zahl der je Projekt aufbewahrten <b>verwaisten</b> Arbeitspakete;
 *     fehlend oder kleiner als 1 ergibt 2000. Bei rund zehn Paketen je Nacht ist das ein halbes
 *     Jahr Rückblick.
 * @param maxInteractivePerProject Zahl der je Projekt aufbewahrten <b>interaktiven Sitzungen</b>;
 *     fehlend oder kleiner als 1 ergibt 400. Bei mehreren Sitzungen am Tag deckt das rund ein
 *     halbes Jahr ab — denselben Rückblick, den 190 Läufe für die Nächte geben.
 * @param maxInteractiveItemsPerProject Zahl der je Projekt aufbewahrten <b>verwaisten</b>
 *     Arbeitspakete interaktiver Sitzungen; fehlend oder kleiner als 1 ergibt 4000 — dasselbe
 *     Verhältnis zur Lauf-Grenze wie bei den Nachtläufen.
 * @param stilleFrist die Stille, die ein unfertiger Lauf sich erlauben darf, bevor er als nicht
 *     gelungen gilt (Issue #1091); fehlend, {@code null} oder nicht positiv ergibt 90 Minuten. Eine
 *     Nacht-Runde dauert bis zu einer Stunde (die Stufengrenzen der {@code night.kette} liegen bei
 *     25 bis 30 Minuten je Stufe), und der Runner meldet nach jeder Runde — 90 Minuten lassen einer
 *     langen Runde Luft und sagen einen abgeschossenen Runner noch in derselben Nacht tot. Nicht
 *     positiv wäre keine Frist, sondern ihr Gegenteil: Jeder unfertige Lauf gälte im selben
 *     Augenblick als verstummt.
 */
@ConfigurationProperties(prefix = "manban.nightrun")
public record NightRunProperties(
    Integer maxPerProject,
    Integer maxItemsPerProject,
    Integer maxInteractivePerProject,
    Integer maxInteractiveItemsPerProject,
    Duration stilleFrist) {

  public NightRunProperties {
    if (maxPerProject == null || maxPerProject < 1) {
      maxPerProject = 190;
    }
    if (maxItemsPerProject == null || maxItemsPerProject < 1) {
      maxItemsPerProject = 2000;
    }
    if (maxInteractivePerProject == null || maxInteractivePerProject < 1) {
      maxInteractivePerProject = 400;
    }
    if (maxInteractiveItemsPerProject == null || maxInteractiveItemsPerProject < 1) {
      maxInteractiveItemsPerProject = 4000;
    }
    if (stilleFrist == null || !stilleFrist.isPositive()) {
      stilleFrist = Duration.ofMinutes(90);
    }
  }

  /** Zahl der je Projekt aufbewahrten Läufe dieser Gattung. */
  public int maxRunsFor(NightRunKind kind) {
    return kind == NightRunKind.INTERACTIVE ? maxInteractivePerProject : maxPerProject;
  }

  /** Zahl der je Projekt aufbewahrten <b>verwaisten</b> Arbeitspakete dieser Gattung. */
  public int maxOrphanItemsFor(NightRunKind kind) {
    return kind == NightRunKind.INTERACTIVE ? maxInteractiveItemsPerProject : maxItemsPerProject;
  }
}
