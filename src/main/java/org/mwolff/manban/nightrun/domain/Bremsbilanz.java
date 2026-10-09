package org.mwolff.manban.nightrun.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Was die Bremse des Kits gespart hat (Issue #1546, Plan #1547).
 *
 * <p>Die Rechenregel steht nur hier: Die Lesesicht eines Pakets nutzt {@link #gespart} (Issue
 * #1549), der Verbrauchsabruf die Bilanz über viele Pakete aus {@link #of} (Issue #1550).
 *
 * @param brakeCount Zahl der Pakete mit {@link NightRunErrorClass#STUCK} — die Bremse greift je
 *     Paket (E5)
 * @param withoutTimeCount davon die Pakete ohne Zeitwert (E6); sie tragen nichts zur Summe bei
 * @param savedMs Summe der geschätzten gesparten Zeit in Millisekunden, je Sitzung einmal (E4)
 */
public record Bremsbilanz(long brakeCount, long withoutTimeCount, long savedMs) {

  /**
   * Die Bilanz über die genannten Pakete. Pakete ohne {@link NightRunErrorClass#STUCK} zählen
   * nicht, auch nicht ein rotes Paket der Übergangslösung mit „festgefahren" im Auszug.
   *
   * <p>Eine Sitzung ist der Schlüssel aus Lauf und Sitzungskennung (E4): Pakete mit gleichem
   * Schlüssel tragen ihre kleinste gesparte Zeit einmal bei — die Sitzung endet mit dem spätesten
   * Paket, der kleinste Wert ist die vorsichtige Schätzung. Ohne Sitzungskennung oder ohne Lauf ist
   * jedes Paket seine eigene Sitzung, damit verwaiste Pakete verschiedener Nächte nicht
   * zusammenfallen.
   */
  public static Bremsbilanz of(List<NightRunItem> items) {
    long bremsungen = 0;
    long ohneZeitwert = 0;
    Map<Sitzung, Long> jeSitzung = new HashMap<>();
    List<Long> einzeln = new ArrayList<>();
    for (NightRunItem item : items) {
      if (item.errorClass() != NightRunErrorClass.STUCK) {
        continue;
      }
      bremsungen++;
      Long gespart = gespart(item);
      if (gespart == null) {
        ohneZeitwert++;
        continue;
      }
      Sitzung sitzung = Sitzung.von(item);
      if (sitzung == null) {
        einzeln.add(gespart);
      } else {
        jeSitzung.merge(sitzung, gespart, Math::min);
      }
    }
    long summe =
        jeSitzung.values().stream().mapToLong(Long::longValue).sum()
            + einzeln.stream().mapToLong(Long::longValue).sum();
    return new Bremsbilanz(bremsungen, ohneZeitwert, summe);
  }

  /**
   * Die geschätzte gesparte Zeit eines Pakets in Millisekunden: die Zeitgrenze seiner Sitzung
   * abzüglich seiner Laufzeit, nie unter 0 (E7).
   *
   * @return {@code null} für ein Paket ohne {@link NightRunErrorClass#STUCK} und für eine Bremsung
   *     ohne Zeitwert — ohne gemeldete Zeitgrenze oder ohne Laufzeit (E6)
   */
  public static @Nullable Long gespart(NightRunItem item) {
    NightRunStuck stuck = item.stuck();
    Long laufzeit = item.durationMs();
    if (item.errorClass() != NightRunErrorClass.STUCK || stuck == null || laufzeit == null) {
      return null;
    }
    Long zeitgrenze = stuck.sessionLimitMs();
    return zeitgrenze == null ? null : Math.max(0L, zeitgrenze - laufzeit);
  }

  /** Der Sitzungsschlüssel aus E4. */
  private record Sitzung(long nightRunId, String sessionId) {

    /** {@code null}, wenn Lauf oder Sitzungskennung fehlt — dann zählt das Paket für sich. */
    static @Nullable Sitzung von(NightRunItem item) {
      Long lauf = item.nightRunId();
      String kennung = Objects.requireNonNull(item.stuck()).sessionId();
      return lauf == null || kennung == null ? null : new Sitzung(lauf, kennung);
    }
  }
}
