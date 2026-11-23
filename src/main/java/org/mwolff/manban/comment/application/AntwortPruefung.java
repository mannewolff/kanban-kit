package org.mwolff.manban.comment.application;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.comment.application.RueckfrageService.AntwortAuftrag;
import org.mwolff.manban.comment.application.RueckfrageService.EinzelAuftrag;
import org.mwolff.manban.comment.domain.Antwort;
import org.mwolff.manban.comment.domain.Antwort.Einzelantwort;
import org.mwolff.manban.comment.domain.Rueckfrage;
import org.mwolff.manban.comment.domain.Rueckfrage.Art;
import org.mwolff.manban.comment.domain.Rueckfrage.Eintrag;
import org.mwolff.manban.comment.domain.RueckfragenAuswertung;
import org.mwolff.manban.comment.domain.Vorschau;
import org.mwolff.manban.common.TextLimits;

/**
 * Prüft den Auftrag des Kunden gegen die Rückfrage und baut daraus die Antwort (Issue #2210, Plan
 * #2206 E8, E11): Jede Einzelantwort nennt eine Frage der Rückfrage und genau eine Angabe, eine
 * Wahl eine angebotene Möglichkeit, ein Weg einen angebotenen Weg; freigeben verlangt eine nicht
 * überholte Freigabe-Rückfrage, deren Stand der angezeigte ist (Issue #2253), und trägt ihn in
 * {@code commit}.
 */
final class AntwortPruefung {

  private static final long GENAU_EINE = 1;

  private AntwortPruefung() {}

  /**
   * Die Antwort zum Auftrag.
   *
   * @throws RueckfrageUeberholtException bei {@code freigeben} an einer überholten Rückfrage
   * @throws FreigabeStandAbweichendException bei {@code freigeben}, wenn der angezeigte Stand nicht
   *     der der Rückfrage ist
   * @throws UngueltigeAntwortException wenn der Auftrag nicht zur Rückfrage passt
   */
  static Antwort antwort(
      Rueckfrage rueckfrage, AntwortAuftrag auftrag, Optional<Vorschau> juengste) {
    String commit = auftrag.freigeben() ? freigabe(rueckfrage, auftrag.commit(), juengste) : null;
    List<Einzelantwort> einzeln =
        auftrag.antwortenOderLeer().stream().map(e -> einzelantwort(rueckfrage, e)).toList();
    Integer weg = auftrag.weg();
    if (weg != null && rueckfrage.eintraege().stream().noneMatch(e -> bietetWeg(e, weg))) {
      throw UngueltigeAntwortException.keinWeg();
    }
    if (!auftrag.ja() && einzeln.isEmpty() && weg == null && commit == null) {
      throw UngueltigeAntwortException.leer();
    }
    return new Antwort(rueckfrage.kommentarId(), auftrag.ja(), einzeln, weg, commit);
  }

  /**
   * Der Text des Kommentars der Antwort, Kopf in der Sprache der Rückfrage (E9).
   *
   * @throws UngueltigeAntwortException wenn er länger ist als ein Kommentar sein darf
   */
  static String kommentar(Rueckfrage rueckfrage, Antwort antwort) {
    String text = antwort.alsKommentar(rueckfrage.sprache());
    if (text.length() > TextLimits.MAX_TEXT) {
      throw UngueltigeAntwortException.zuLang();
    }
    return text;
  }

  private static @Nullable String freigabe(
      Rueckfrage rueckfrage, @Nullable String angezeigt, Optional<Vorschau> juengste) {
    if (!rueckfrage.istFreigabe()) {
      throw UngueltigeAntwortException.keineFreigabe();
    }
    if (RueckfragenAuswertung.istUeberholt(rueckfrage, juengste)) {
      throw new RueckfrageUeberholtException();
    }
    // Der Kommentar der Rückfrage bleibt bearbeitbar (Issue #2253): Freigegeben wird nur der Stand,
    // den der Mensch gesehen hat, nicht der, der gerade im Kommentar steht.
    String commit = rueckfrage.commit();
    if (!Objects.equals(commit, angezeigt)) {
      throw new FreigabeStandAbweichendException();
    }
    return commit;
  }

  private static boolean bietetWeg(Eintrag eintrag, int weg) {
    return eintrag.art() == Art.WEG && eintrag.bietet(weg);
  }

  private static Einzelantwort einzelantwort(Rueckfrage rueckfrage, EinzelAuftrag auftrag) {
    Eintrag eintrag =
        rueckfrage
            .eintrag(auftrag.nr())
            .filter(e -> passt(e, auftrag))
            .orElseThrow(() -> UngueltigeAntwortException.fuerFrage(auftrag.nr()));
    Integer wahl = auftrag.wahl();
    if (wahl != null) {
      return Einzelantwort.gewaehlt(eintrag.nr(), wahl);
    }
    String text = auftrag.text();
    if (text != null) {
      return Einzelantwort.eigene(eintrag.nr(), text);
    }
    return Einzelantwort.zurueckgestellt(eintrag.nr());
  }

  /** Genau eine Angabe, eine Wahl unter den angebotenen Möglichkeiten, ein Text nicht leer. */
  private static boolean passt(Eintrag eintrag, EinzelAuftrag auftrag) {
    Integer wahl = auftrag.wahl();
    String text = auftrag.text();
    boolean zurueck = Boolean.TRUE.equals(auftrag.zurueckstellen());
    long angaben =
        Stream.of(wahl != null, text != null, zurueck).filter(gesetzt -> gesetzt).count();
    if (angaben != GENAU_EINE) {
      return false;
    }
    if (wahl != null) {
      return eintrag.bietet(wahl);
    }
    return text == null || !text.isBlank();
  }
}
