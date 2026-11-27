package org.mwolff.manban.codeanalyse.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.codeanalyse.domain.Wochenwerte.NichtZaehlend;
import org.mwolff.manban.codeanalyse.domain.Wochenwerte.Summe;
import org.mwolff.manban.codeanalyse.domain.Wochenwerte.Woche;

/**
 * Ermittelt die Wochenwerte der Platte „Codeanalyse“ aus allen Messungen eines Projekts (Issue
 * #2273, Plan #2272 E6, E7, E9) — rein, ohne Datenbank und ohne Uhr. Die Reihenfolge der Eingaben
 * spielt keine Rolle.
 *
 * <p><b>Wochen (E6):</b> die zwölf {@link Kalenderwochen} bis {@code jetzt}; maßgeblich ist {@code
 * publishedAt}. Messungen außerhalb des Fensters zählen in keiner Woche.
 *
 * <p><b>Dichte und Rückgang (E7):</b> Dichte = Funde / Zeilen × 1.000 aus den Wochensummen der
 * zählenden Messungen, je Art und als Summe; ohne Zeilen keine Dichte. Rückgang = (Funde vorher −
 * Funde Endzustand) / Funde vorher × 100; ohne Fund vorher kein Wert.
 */
public final class BefunddichteErmittlung {

  private static final double JE_TAUSEND_ZEILEN = 1_000.0;
  private static final double HUNDERT = 100.0;

  private BefunddichteErmittlung() {}

  /** Die Wochenwerte zu den Messungen eines Projekts im Fenster bis {@code jetzt}. */
  public static Wochenwerte ermittle(Collection<Messung> messungen, Instant jetzt) {
    Map<Integer, List<Messung>> jeWoche = new HashMap<>();
    for (Messung messung : messungen) {
      Kalenderwochen.index(messung.publishedAt(), jetzt)
          .ifPresent(i -> jeWoche.computeIfAbsent(i, k -> new ArrayList<>()).add(messung));
    }
    List<Woche> wochen = new ArrayList<>();
    for (int i = 0; i < Kalenderwochen.ANZAHL; i++) {
      wochen.add(woche(Kalenderwochen.beginn(i, jetzt), jeWoche.getOrDefault(i, List.of())));
    }
    Instant fensterbeginn = Kalenderwochen.fensterbeginn(jetzt);
    Instant dataSince =
        messungen.stream()
            .map(Messung::publishedAt)
            .min(Instant::compareTo)
            .filter(erste -> erste.isAfter(fensterbeginn))
            .orElse(null);
    return new Wochenwerte(!messungen.isEmpty(), dataSince, wochen);
  }

  private static Woche woche(Instant wochenbeginn, List<Messung> messungen) {
    List<Messung> zaehlende = messungen.stream().filter(Messung::zaehlt).toList();
    Map<String, Integer> nichtZaehlend = new TreeMap<>();
    for (Messung messung : messungen) {
      if (!messung.zaehlt()) {
        nichtZaehlend.merge(Objects.requireNonNull(messung.grund()), 1, Integer::sum);
      }
    }
    List<NichtZaehlend> gruende =
        nichtZaehlend.entrySet().stream()
            .map(e -> new NichtZaehlend(e.getKey(), e.getValue()))
            .toList();
    if (zaehlende.isEmpty()) {
      return new Woche(wochenbeginn, 0, null, null, null, null, gruende);
    }
    long karten = zaehlende.stream().mapToLong(Messung::karten).sum();
    Summe vorher = summe(zaehlende, m -> Objects.requireNonNull(m.vorher()));
    Summe endzustand = summe(zaehlende, m -> Objects.requireNonNull(m.endzustand()));
    return new Woche(
        wochenbeginn,
        zaehlende.size(),
        karten,
        vorher,
        endzustand,
        rueckgang(vorher.funde(), endzustand.funde()),
        gruende);
  }

  private static Summe summe(List<Messung> zaehlende, Function<Messung, Zustand> zustand) {
    long fehler = 0;
    long sicherheitsluecken = 0;
    long wartbarkeitsmaengel = 0;
    long zeilen = 0;
    for (Messung messung : zaehlende) {
      Zustand z = zustand.apply(messung);
      fehler += z.fehler();
      sicherheitsluecken += z.sicherheitsluecken();
      wartbarkeitsmaengel += z.wartbarkeitsmaengel();
      zeilen += z.zeilen();
    }
    long funde = fehler + sicherheitsluecken + wartbarkeitsmaengel;
    return new Summe(
        fehler,
        sicherheitsluecken,
        wartbarkeitsmaengel,
        funde,
        zeilen,
        dichte(fehler, zeilen),
        dichte(sicherheitsluecken, zeilen),
        dichte(wartbarkeitsmaengel, zeilen),
        dichte(funde, zeilen));
  }

  private static @Nullable Double dichte(long funde, long zeilen) {
    return zeilen == 0 ? null : funde * JE_TAUSEND_ZEILEN / zeilen;
  }

  private static @Nullable Double rueckgang(long fundeVorher, long fundeEndzustand) {
    return fundeVorher == 0 ? null : (fundeVorher - fundeEndzustand) * HUNDERT / fundeVorher;
  }
}
