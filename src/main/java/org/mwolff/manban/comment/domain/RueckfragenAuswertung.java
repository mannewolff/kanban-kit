package org.mwolff.manban.comment.domain;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.comment.domain.Antwort.Einzelantwort;
import org.mwolff.manban.comment.domain.Rueckfrage.Art;
import org.mwolff.manban.comment.domain.Rueckfrage.Eintrag;

/**
 * Stand einer Rückfrage und ihrer Fragen aus den Antworten an der Karte (Issue #2210, Plan #2206
 * E8, E11; Vertrag V1).
 *
 * <ul>
 *   <li>Gültig ist eine Antwort nur, wenn sie auf diese Rückfrage verweist, kein Beitrag der KI ist
 *       und nicht über einen gewöhnlichen Zugang ({@code STANDARD}) kam. Trägt sie einen {@code
 *       commit}, muss er der Stand sein, den die Rückfrage zur Freigabe stellt — bei einer
 *       überholten Rückfrage der der jüngsten Vorschau.
 *   <li>Die gültigen Antworten gelten in zeitlicher Reihenfolge: {@code ja} übernimmt die
 *       Empfehlung jeder noch nicht beantworteten Frage, {@code weg} beantwortet die Einträge der
 *       Art Weg, jede Einzelantwort setzt den Stand ihrer Frage. Eine spätere Antwort ergänzt so
 *       die frühere.
 *   <li>Beantwortet ist die Rückfrage, wenn jede Frage beantwortet ist; eine zurückgestellte hält
 *       sie offen. Eine Freigabe-Rückfrage, für deren Projekt eine jüngere Vorschau mit anderem
 *       Stand gemeldet ist, ist überholt und zählt nicht mehr als offen.
 * </ul>
 */
public final class RueckfragenAuswertung {

  private static final String ZUGANG_STANDARD = "STANDARD";

  private RueckfragenAuswertung() {}

  /** Stand einer Rückfrage. */
  public enum RueckfrageZustand {
    OFFEN,
    BEANTWORTET,
    UEBERHOLT
  }

  /** Stand einer Frage. */
  public enum FrageZustand {
    OFFEN,
    BEANTWORTET,
    ZURUECKGESTELLT
  }

  /**
   * Eine Antwort an der Karte mit ihrem Absender.
   *
   * @param antwort die gelesene Antwort
   * @param am wann ihr Kommentar angelegt wurde
   * @param vonKi ob der Kommentar ein Beitrag der KI ist
   * @param zugangArt Art des Zugangs beim Anlegen; {@code null} bei der Sitzung
   */
  public record EingegangeneAntwort(
      Antwort antwort, Instant am, boolean vonKi, @Nullable String zugangArt) {}

  /**
   * Stand einer Frage.
   *
   * @param nr Nummer des Eintrags
   * @param zustand offen, beantwortet oder zurückgestellt
   * @param wahl Nummer der gewählten Möglichkeit; {@code null} ohne
   * @param text eigene Antwort; {@code null} ohne
   * @param empfehlung ob die Empfehlung übernommen wurde
   */
  public record FrageStand(
      int nr,
      FrageZustand zustand,
      @Nullable Integer wahl,
      @Nullable String text,
      boolean empfehlung) {

    static FrageStand offen(int nr) {
      return new FrageStand(nr, FrageZustand.OFFEN, null, null, false);
    }
  }

  /**
   * Stand einer Rückfrage.
   *
   * @param rueckfrage die Rückfrage
   * @param zustand offen, beantwortet oder überholt
   * @param fragen Stand je Eintrag, in der Reihenfolge der Rückfrage
   */
  public record Stand(Rueckfrage rueckfrage, RueckfrageZustand zustand, List<FrageStand> fragen) {

    public Stand {
      fragen = List.copyOf(fragen);
    }
  }

  /**
   * Wertet die Rückfrage aus.
   *
   * @param antworten alle Antworten an der Karte, gleich auf welche Rückfrage
   * @param juengste die jüngste Vorschau des Projekts, wenn eine gemeldet ist
   */
  public static Stand auswerten(
      Rueckfrage rueckfrage, List<EingegangeneAntwort> antworten, Optional<Vorschau> juengste) {
    boolean ueberholt = istUeberholt(rueckfrage, juengste);
    @Nullable String bezug = ueberholt ? juengste.get().revision() : rueckfrage.commit();
    Map<Integer, FrageStand> staende = new LinkedHashMap<>();
    rueckfrage.eintraege().forEach(e -> staende.put(e.nr(), FrageStand.offen(e.nr())));
    antworten.stream()
        .filter(a -> a.antwort().vorlage() == rueckfrage.kommentarId())
        .filter(a -> gueltig(a, bezug))
        .sorted(Comparator.comparing(EingegangeneAntwort::am))
        .forEach(a -> anwenden(rueckfrage, a.antwort(), staende));
    RueckfrageZustand zustand;
    if (ueberholt) {
      zustand = RueckfrageZustand.UEBERHOLT;
    } else if (staende.values().stream().allMatch(s -> s.zustand() == FrageZustand.BEANTWORTET)) {
      zustand = RueckfrageZustand.BEANTWORTET;
    } else {
      zustand = RueckfrageZustand.OFFEN;
    }
    return new Stand(rueckfrage, zustand, List.copyOf(staende.values()));
  }

  /**
   * Ob die Freigabe-Rückfrage überholt ist: Die jüngste Vorschau des Projekts wurde nach ihr
   * gemeldet und zeigt einen anderen Stand (E11). Eine Rückfrage ohne Freigabe ist nie überholt.
   */
  public static boolean istUeberholt(Rueckfrage rueckfrage, Optional<Vorschau> juengste) {
    return rueckfrage.istFreigabe()
        && juengste
            .filter(v -> v.gemeldetAm().isAfter(rueckfrage.angelegtAm()))
            .filter(v -> !v.revision().equals(rueckfrage.commit()))
            .isPresent();
  }

  private static boolean gueltig(EingegangeneAntwort eingang, @Nullable String bezug) {
    String commit = eingang.antwort().commit();
    return !eingang.vonKi()
        && !ZUGANG_STANDARD.equals(eingang.zugangArt())
        && (commit == null || commit.equals(bezug));
  }

  private static void anwenden(
      Rueckfrage rueckfrage, Antwort antwort, Map<Integer, FrageStand> staende) {
    if (antwort.ja()) {
      for (Eintrag e : rueckfrage.eintraege()) {
        staende.computeIfPresent(
            e.nr(),
            (nr, bisher) ->
                bisher.zustand() == FrageZustand.BEANTWORTET
                    ? bisher
                    : new FrageStand(nr, FrageZustand.BEANTWORTET, e.empfohleneWahl(), null, true));
      }
    }
    Integer weg = antwort.weg();
    if (weg != null) {
      rueckfrage.eintraege().stream()
          .filter(e -> e.art() == Art.WEG && e.bietet(weg))
          .forEach(e -> staende.put(e.nr(), gewaehlt(e.nr(), weg)));
    }
    for (Einzelantwort einzel : antwort.antworten()) {
      rueckfrage
          .eintrag(einzel.nr())
          .flatMap(e -> stand(e, einzel))
          .ifPresent(s -> staende.put(s.nr(), s));
    }
  }

  private static Optional<FrageStand> stand(Eintrag eintrag, Einzelantwort einzel) {
    if (einzel.zurueckstellen()) {
      return Optional.of(
          new FrageStand(eintrag.nr(), FrageZustand.ZURUECKGESTELLT, null, null, false));
    }
    Integer wahl = einzel.wahl();
    if (wahl != null) {
      return eintrag.bietet(wahl) ? Optional.of(gewaehlt(eintrag.nr(), wahl)) : Optional.empty();
    }
    return Optional.of(
        new FrageStand(eintrag.nr(), FrageZustand.BEANTWORTET, null, einzel.text(), false));
  }

  private static FrageStand gewaehlt(int nr, int wahl) {
    return new FrageStand(nr, FrageZustand.BEANTWORTET, wahl, null, false);
  }
}
