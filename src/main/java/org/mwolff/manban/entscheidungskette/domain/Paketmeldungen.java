package org.mwolff.manban.entscheidungskette.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.KitMeldungen;
import org.mwolff.manban.common.KitMeldungen.Meldungsart;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.Karte;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.Kommentar;

/**
 * Die Meldungen des Kits am Paket (Issue #2116): Abschlussbericht, Code-Review, Übertragung,
 * Nachtrag des Build-Diensts und Veröffentlichung, je Karte nach Zeitpunkt und ID (Plan #2108 E17).
 */
final class Paketmeldungen {

  private static final String HERKUNFT_KIT = "TOKEN";
  private static final Set<Meldungsart> PAKETARTEN =
      Set.of(
          Meldungsart.ABSCHLUSSBERICHT,
          Meldungsart.CODE_REVIEW,
          Meldungsart.UEBERTRAGUNG,
          Meldungsart.BUILD_NACHTRAG,
          Meldungsart.VEROEFFENTLICHUNG);
  private static final Comparator<Kommentar> FOLGE =
      Comparator.comparing(Kommentar::zeitpunkt).thenComparingLong(Kommentar::id);

  private final Map<Long, List<Gelesen>> jeKarte = new HashMap<>();

  Paketmeldungen(List<Kommentar> kommentare) {
    kommentare.stream()
        .sorted(FOLGE)
        .forEach(
            k ->
                lies(k)
                    .ifPresent(
                        g ->
                            jeKarte.computeIfAbsent(k.kartenId(), id -> new ArrayList<>()).add(g)));
  }

  /** Die Meldungen dieser Art am Paket, in ihrer Folge. */
  List<Gelesen> von(Karte paket, Meldungsart art) {
    return jeKarte.getOrDefault(paket.id(), List.of()).stream()
        .filter(g -> g.art() == art)
        .toList();
  }

  /**
   * Eine Meldung des Kits am Paket. Ist sie von Hand geändert, zählt allein ihre bewahrte
   * Kopfzeile, und ihre Angaben gelten nicht (Plan #2108 E4, Kriterium 13).
   */
  private static Optional<Gelesen> lies(Kommentar k) {
    if (!HERKUNFT_KIT.equals(k.herkunft())) {
      return Optional.empty();
    }
    if (k.vonHandGeaendertAm() != null) {
      return paketart(k.vonHandKopfzeile()).map(art -> new Gelesen(k, art, null));
    }
    String kopf = k.text().lines().filter(z -> !z.isBlank()).findFirst().orElse("");
    return paketart(kopf)
        .flatMap(
            art -> KitMeldungLeser.lies(k.herkunft(), k.text()).map(m -> new Gelesen(k, art, m)));
  }

  private static Optional<Meldungsart> paketart(@Nullable String kopf) {
    return KitMeldungen.artDerKopfzeile(kopf).filter(PAKETARTEN::contains);
  }

  /**
   * Ein gelesener Kommentar am Paket.
   *
   * @param meldung die gelesene Meldung; {@code null}, wenn sie von Hand geändert wurde
   */
  record Gelesen(Kommentar kommentar, Meldungsart art, @Nullable KitMeldung meldung) {}
}
