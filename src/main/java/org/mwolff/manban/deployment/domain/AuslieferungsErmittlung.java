package org.mwolff.manban.deployment.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.Wochenfenster;
import org.mwolff.manban.deployment.domain.Auslieferungskennzahlen.Woche;

/**
 * Ermittelt die vier DORA-Kennzahlen der Platte „Auslieferung“ aus den gespeicherten Meldungen
 * (Issue #1832, Plan #1831 A2) — rein, ohne Datenbank. Die Reihenfolge der Eingaben spielt keine
 * Rolle: Versuche ordnet die Ermittlung selbst nach {@code finishedAt}, bei Gleichstand nach dem
 * Eingang.
 *
 * <p><b>Fenster (E8):</b> die zwölf Wochenfenster aus {@link Wochenfenster} bis {@code jetzt},
 * zusammen 84 Tage; maßgeblich ist {@code finishedAt}.
 *
 * <p><b>Commits (E6, E7):</b> Je {@code sha} ist der erste meldende Versuch der mit dem frühesten
 * {@code finishedAt}; sein {@code committedAt} gilt, und jeder {@code sha} zählt genau einmal.
 * Ausgeliefert ist der Commit durch den frühesten erfolgreichen Versuch, dessen {@code finishedAt}
 * nicht vor dem des ersten meldenden liegt. Ein {@code committedAt} nach der Auslieferung ergibt
 * eine Lead Time von 0.
 *
 * <p><b>Ausfälle (E8, E9):</b> Ein Ausfall reicht vom ersten gescheiterten Versuch einer
 * ununterbrochenen Folge bis zum nächsten erfolgreichen und zählt mit voller Dauer im Fenster
 * seines Endes. Eine noch offene Folge steht in {@code outageSince}, nicht im Median.
 */
public final class AuslieferungsErmittlung {

  private static final Comparator<Auslieferungsversuch> FRUEHER =
      Comparator.comparing(Auslieferungsversuch::finishedAt)
          .thenComparingLong(Auslieferungsversuch::id);

  private static final int HUNDERT = 100;

  private AuslieferungsErmittlung() {}

  /** Die Kennzahlen zu den gemeldeten Versuchen und Commits, im Fenster bis {@code jetzt}. */
  public static Auslieferungskennzahlen ermittle(
      Collection<Auslieferungsversuch> versuche,
      Collection<GemeldeterCommit> commits,
      Instant jetzt) {
    List<Auslieferungsversuch> geordnet = versuche.stream().sorted(FRUEHER).toList();
    List<Auslieferungsversuch> imFenster =
        geordnet.stream().filter(v -> imFenster(v.finishedAt(), jetzt)).toList();
    int erfolge = (int) imFenster.stream().filter(AuslieferungsErmittlung::erfolgreich).count();
    Integer fehlerrate =
        imFenster.isEmpty() ? null : prozent(imFenster.size() - erfolge, imFenster.size());
    Ausfaelle ausfaelle = Ausfaelle.aus(geordnet, jetzt);
    Instant dataSince =
        geordnet.stream()
            .findFirst()
            .map(Auslieferungsversuch::finishedAt)
            .filter(erste -> imFenster(erste, jetzt))
            .orElse(null);
    return new Auslieferungskennzahlen(
        !geordnet.isEmpty(),
        dataSince,
        erfolge,
        wert(Median.von(leadTimes(geordnet, commits, jetzt))),
        fehlerrate,
        wert(Median.von(ausfaelle.dauern())),
        ausfaelle.offenSeit(),
        wochen(imFenster, jetzt));
  }

  private static List<Long> leadTimes(
      List<Auslieferungsversuch> geordnet, Collection<GemeldeterCommit> commits, Instant jetzt) {
    Map<Long, Auslieferungsversuch> jeId =
        geordnet.stream().collect(Collectors.toMap(Auslieferungsversuch::id, Function.identity()));
    BinaryOperator<Meldung> fruehere =
        (a, b) -> FRUEHER.compare(a.versuch(), b.versuch()) <= 0 ? a : b;
    Map<String, Meldung> ersteMeldung = new HashMap<>();
    for (GemeldeterCommit commit : commits) {
      Auslieferungsversuch versuch = jeId.get(commit.versuchId());
      if (versuch != null) {
        ersteMeldung.merge(commit.sha(), new Meldung(versuch, commit.committedAt()), fruehere);
      }
    }
    List<Auslieferungsversuch> erfolgreiche =
        geordnet.stream().filter(AuslieferungsErmittlung::erfolgreich).toList();
    List<Long> dauern = new ArrayList<>();
    for (Meldung meldung : ersteMeldung.values()) {
      auslieferung(meldung, erfolgreiche)
          .map(Auslieferungsversuch::finishedAt)
          .filter(ausgeliefert -> imFenster(ausgeliefert, jetzt))
          .ifPresent(ausgeliefert -> dauern.add(sekunden(meldung.committedAt(), ausgeliefert)));
    }
    return dauern;
  }

  private static Optional<Auslieferungsversuch> auslieferung(
      Meldung meldung, List<Auslieferungsversuch> erfolgreiche) {
    Instant gemeldet = meldung.versuch().finishedAt();
    return erfolgreiche.stream().filter(v -> !v.finishedAt().isBefore(gemeldet)).findFirst();
  }

  private static List<Woche> wochen(List<Auslieferungsversuch> imFenster, Instant jetzt) {
    int[] erfolge = new int[Wochenfenster.ANZAHL];
    int[] fehlschlaege = new int[Wochenfenster.ANZAHL];
    for (Auslieferungsversuch versuch : imFenster) {
      int woche = Wochenfenster.index(versuch.finishedAt(), jetzt).orElseThrow();
      if (erfolgreich(versuch)) {
        erfolge[woche]++;
      } else {
        fehlschlaege[woche]++;
      }
    }
    List<Woche> wochen = new ArrayList<>();
    for (int i = 0; i < Wochenfenster.ANZAHL; i++) {
      wochen.add(new Woche(Wochenfenster.beginn(i, jetzt), erfolge[i], fehlschlaege[i]));
    }
    return wochen;
  }

  private static boolean imFenster(Instant zeitpunkt, Instant jetzt) {
    return Wochenfenster.index(zeitpunkt, jetzt).isPresent();
  }

  private static boolean erfolgreich(Auslieferungsversuch versuch) {
    return versuch.ausgang() == Ausgang.SUCCESS;
  }

  private static long sekunden(Instant von, Instant bis) {
    return Math.max(0, Duration.between(von, bis).toSeconds());
  }

  private static int prozent(int teil, int ganzes) {
    return (int) Math.round((double) HUNDERT * teil / ganzes);
  }

  private static @Nullable Long wert(OptionalLong median) {
    return median.isPresent() ? median.getAsLong() : null;
  }

  /** Ein Commit, wie ihn ein bestimmter Versuch meldete. */
  private record Meldung(Auslieferungsversuch versuch, Instant committedAt) {}

  /**
   * Die Ausfälle über alle geordneten Versuche.
   *
   * @param dauern Dauern der im Fenster endenden Ausfälle in Sekunden
   * @param offenSeit Beginn der noch laufenden Folge; {@code null} ohne
   */
  private record Ausfaelle(List<Long> dauern, @Nullable Instant offenSeit) {

    static Ausfaelle aus(List<Auslieferungsversuch> geordnet, Instant jetzt) {
      List<Long> dauern = new ArrayList<>();
      Instant beginn = null;
      for (Auslieferungsversuch versuch : geordnet) {
        if (!erfolgreich(versuch)) {
          if (beginn == null) {
            beginn = versuch.finishedAt();
          }
        } else if (beginn != null) {
          if (imFenster(versuch.finishedAt(), jetzt)) {
            dauern.add(sekunden(beginn, versuch.finishedAt()));
          }
          beginn = null;
        }
      }
      return new Ausfaelle(dauern, beginn);
    }
  }
}
