package org.mwolff.manban.entscheidungskette.domain;

import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.KitMeldungen;
import org.mwolff.manban.common.KitMeldungen.Meldungsart;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.Kommentar;

/**
 * Liest aus den Kommentaren die Meldungen des Kits, die Dokument-Glieder begründen: Plan- und
 * Fachplan-Review und ihre Einarbeitung (Plan #2108 E3, E4, E9).
 */
final class Dokumentmeldungen {

  /** Die Prüfrunden eines Dokuments; das Issue-Review gehört zum Paket. */
  static final Set<Meldungsart> RUNDENARTEN =
      Set.of(Meldungsart.PLAN_REVIEW, Meldungsart.FACHPLAN_REVIEW);

  private static final String HERKUNFT_KIT = "TOKEN";

  private Dokumentmeldungen() {}

  /**
   * Eine Meldung des Kits, die ein Dokument-Glied begründet. Ist sie von Hand geändert, zählt
   * allein ihre bewahrte Kopfzeile, und ihre Angaben gelten nicht (Plan #2108 E4, Kriterium 13).
   */
  static Optional<Gelesen> lies(Kommentar k) {
    if (!HERKUNFT_KIT.equals(k.herkunft())) {
      return Optional.empty();
    }
    if (k.vonHandGeaendertAm() != null) {
      String kopf = k.vonHandKopfzeile();
      return KitMeldungen.artDerKopfzeile(kopf)
          .filter(art -> RUNDENARTEN.contains(art) || art == Meldungsart.EINARBEITUNG)
          .map(art -> new Gelesen(k, art, rundeAusKopf(kopf), null, true));
    }
    return KitMeldungLeser.lies(k.herkunft(), k.text()).flatMap(m -> gelesen(k, m));
  }

  private static Optional<Gelesen> gelesen(Kommentar k, KitMeldung meldung) {
    return switch (meldung) {
      case Pruefrunde p when RUNDENARTEN.contains(p.art()) ->
          Optional.of(new Gelesen(k, p.art(), p.runde(), p, false));
      case Einarbeitung e ->
          Optional.of(new Gelesen(k, Meldungsart.EINARBEITUNG, e.runde(), null, false));
      default -> Optional.empty();
    };
  }

  /** Die Zahl am Ende eines Kopfs, dessen Form {@link KitMeldungen} schon geprüft hat. */
  private static int rundeAusKopf(@Nullable String kopf) {
    String gestutzt = String.valueOf(kopf).strip();
    return Integer.parseInt(gestutzt.substring(gestutzt.lastIndexOf(' ') + 1));
  }

  /**
   * Ein gelesener Kommentar.
   *
   * @param pruefrunde die gelesene Runde; {@code null} bei Einarbeitung oder Änderung von Hand
   */
  record Gelesen(
      Kommentar kommentar,
      Meldungsart art,
      int runde,
      @Nullable Pruefrunde pruefrunde,
      boolean vonHand) {}
}
