package org.mwolff.manban.entscheidungskette.domain;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.Art;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.Bezug;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.Karte;

/**
 * Rechnet die Entscheidungskette (Issues #2115, #2116, Plan #2108 E10 bis E15, E17, E18): Idee,
 * Fachplan, Plan und die Herkunft der geöffneten Karte, je Dokument mit Prüfrunden, am Paket dazu
 * Issue-Review, Freigabe, Umsetzung, Code-Review, Übertragung und Veröffentlichung ({@link
 * Paketglieder}), am Fachplan und am Plan die Nachfahren mit ihrem zuletzt erreichten Glied. Reine
 * Rechnung ohne Uhr und ohne Ports; zweimal gerechnet ergibt dieselbe Ausgabe.
 *
 * <p>„Nicht vorgesehen“ gilt wörtlich nach Kriterium 6: {@code [Task]} belegt nur den Plan, das
 * Fehlen von {@code Fachliche Quelle:} an einem Plan mit {@code Plan-Modell:} den Fachplan. Eine
 * gesetzte Marke {@code weg_beleg_geaendert_am} an der belegenden Karte macht daraus „nicht
 * erfasst“.
 */
public final class KettenRechner {

  private KettenRechner() {}

  /** Die Kette der Karte aus der Eingabe. */
  public static Entscheidungskette rechne(KettenEingabe eingabe) {
    Karte karte = eingabe.karte();
    Dokumentart art = Dokumentart.von(karte.art());
    Kartenglieder glieder = new Kartenglieder(eingabe.aktivitaeten(), eingabe.kommentare());
    Paketglieder paketglieder = new Paketglieder(eingabe, glieder);
    List<Dokumentart> stufen = stufen(karte, art);
    Map<Dokumentart, Bezug> vorfahren = ordne(karte, stufen, eingabe.vorfahren());

    List<Abschnitt> abschnitte = new ArrayList<>();
    abschnitte.add(idee(eingabe, vorfahren, glieder));
    if (stufen.contains(Dokumentart.FACHPLAN)) {
      Bezug fachplan = vorfahren.get(Dokumentart.FACHPLAN);
      abschnitte.add(
          fachplan == null
              ? new Abschnitt(Dokumentart.FACHPLAN, List.of(fehlenderFachplan(karte, vorfahren)))
              : abschnitt(Dokumentart.FACHPLAN, fachplan, glieder));
    }
    if (art == Dokumentart.PAKET) {
      Bezug plan = vorfahren.get(Dokumentart.PLAN);
      abschnitte.add(
          plan == null
              ? new Abschnitt(Dokumentart.PLAN, List.of(fehlenderPlan(karte)))
              : abschnitt(Dokumentart.PLAN, plan, glieder));
    }
    abschnitte.add(paketglieder.abschnitt(art, karte, freigebende(vorfahren)));

    List<Nachfahre> nachfahren =
        art == Dokumentart.PAKET
            ? List.of()
            : eingabe.nachfahren().stream().map(paketglieder::nachfahre).toList();
    return new Entscheidungskette(art, List.copyOf(abschnitte), nachfahren);
  }

  /** Fachplan und Plan der Kette, an denen ein Kennzeichen die Freigabe sein kann (E11). */
  private static List<Karte> freigebende(Map<Dokumentart, Bezug> vorfahren) {
    List<Karte> freigebende = new ArrayList<>();
    for (Dokumentart stufe : List.of(Dokumentart.FACHPLAN, Dokumentart.PLAN)) {
      Bezug b = vorfahren.get(stufe);
      Karte k = b == null ? null : b.karte();
      if (k != null) {
        freigebende.add(k);
      }
    }
    return freigebende;
  }

  /** Die Vorfahren-Glieder der Karte, nächstes zuerst; ein {@code [Task]} hat keinen Plan. */
  private static List<Dokumentart> stufen(Karte karte, Dokumentart art) {
    if (karte.art() == Art.TASK) {
      return List.of(Dokumentart.FACHPLAN);
    }
    return switch (art) {
      case PAKET -> List.of(Dokumentart.PLAN, Dokumentart.FACHPLAN);
      case PLAN -> List.of(Dokumentart.FACHPLAN);
      default -> List.of();
    };
  }

  /**
   * Ordnet die Vorfahren den Gliedern zu: eine vorhandene Karte nach ihrer Art, eine ohne Angaben
   * nach den Zeilen {@code Plan:} und {@code Fachliche Quelle:} der Kette, sonst nach ihrer Stufe.
   * Das erste passende gewinnt (Plan #2108 E12).
   */
  private static Map<Dokumentart, Bezug> ordne(
      Karte karte, List<Dokumentart> stufen, List<Bezug> vorfahren) {
    Set<Integer> planNummern = new HashSet<>(Dokumentzeilen.planNummern(karte.beschreibung()));
    Set<Integer> quellNummern = new HashSet<>(Dokumentzeilen.quellNummern(karte.beschreibung()));
    for (Bezug b : vorfahren) {
      Karte k = b.karte();
      if (k != null) {
        planNummern.addAll(Dokumentzeilen.planNummern(k.beschreibung()));
        quellNummern.addAll(Dokumentzeilen.quellNummern(k.beschreibung()));
      }
    }
    Map<Dokumentart, Bezug> belegt = new EnumMap<>(Dokumentart.class);
    int naechste = 0;
    for (Bezug b : vorfahren) {
      Dokumentart ziel = ziel(b, stufen, naechste, planNummern, quellNummern);
      if (ziel != null && stufen.contains(ziel) && !belegt.containsKey(ziel)) {
        belegt.put(ziel, b);
        naechste = Math.max(naechste, stufen.indexOf(ziel) + 1);
      }
    }
    return belegt;
  }

  private static @Nullable Dokumentart ziel(
      Bezug b,
      List<Dokumentart> stufen,
      int naechste,
      Set<Integer> planNummern,
      Set<Integer> quellNummern) {
    Karte k = b.karte();
    if (k != null) {
      return switch (k.art()) {
        case PLAN -> Dokumentart.PLAN;
        case FACHLICH -> Dokumentart.FACHPLAN;
        default -> null;
      };
    }
    // Ohne Nummer (anderes Projekt) trifft keine Zeile: beide Mengen sind HashSets ohne null.
    Integer nummer = b.nummer();
    if (planNummern.contains(nummer)) {
      return Dokumentart.PLAN;
    }
    if (quellNummern.contains(nummer)) {
      return Dokumentart.FACHPLAN;
    }
    return naechste < stufen.size() ? stufen.get(naechste) : null;
  }

  private static Abschnitt abschnitt(Dokumentart art, Bezug b, Kartenglieder glieder) {
    Karte k = b.karte();
    if (k != null) {
      return glieder.abschnitt(art, k);
    }
    return new Abschnitt(art, List.of(Glied.ohneKarte(Gliedart.HERKUNFT, b)));
  }

  /** {@code [Task]} belegt, dass es keinen Plan gibt (Kriterium 6, E13). */
  private static Glied fehlenderPlan(Karte karte) {
    if (karte.art() == Art.TASK) {
      return Glied.ohneAngaben(Gliedart.HERKUNFT, belegt(karte), Verweis.KEINER, Set.of());
    }
    return Glied.ohneAngaben(
        Gliedart.HERKUNFT,
        Zustand.NICHT_ERFASST,
        erste(Dokumentzeilen.planNummern(karte.beschreibung())),
        Set.of());
  }

  /**
   * Ein Plan mit {@code Plan-Modell:} ohne {@code Fachliche Quelle:} belegt, dass es keinen
   * Fachplan gibt (Kriterium 6, E13); ohne solchen Plan ist der Fachplan nicht erfasst.
   */
  private static Glied fehlenderFachplan(Karte karte, Map<Dokumentart, Bezug> vorfahren) {
    Bezug planBezug = vorfahren.get(Dokumentart.PLAN);
    Karte plan = karte.art() == Art.PLAN ? karte : planBezug == null ? null : planBezug.karte();
    if (plan != null
        && Dokumentzeilen.traegtPlanModell(plan.beschreibung())
        && !Dokumentzeilen.traegtFachlicheQuelle(plan.beschreibung())) {
      return Glied.ohneAngaben(Gliedart.HERKUNFT, belegt(plan), Verweis.KEINER, Set.of());
    }
    List<Integer> genannt = new ArrayList<>();
    if (plan != null) {
      genannt.addAll(Dokumentzeilen.quellNummern(plan.beschreibung()));
    }
    genannt.addAll(Dokumentzeilen.quellNummern(karte.beschreibung()));
    return Glied.ohneAngaben(Gliedart.HERKUNFT, Zustand.NICHT_ERFASST, erste(genannt), Set.of());
  }

  /** Nicht vorgesehen, solange die belegende Angabe nicht nachweislich geändert wurde (E14). */
  private static Zustand belegt(Karte beleg) {
    return beleg.wegBelegGeaendertAm() == null ? Zustand.NICHT_VORGESEHEN : Zustand.NICHT_ERFASST;
  }

  private static Verweis erste(List<Integer> nummern) {
    return nummern.isEmpty() ? Verweis.KEINER : Verweis.nurNummer(nummern.getFirst());
  }

  /** Die Idee zur Kette: Ziele sind Fachplan, Plan und die Karte selbst, in dieser Folge. */
  private static Abschnitt idee(
      KettenEingabe eingabe, Map<Dokumentart, Bezug> vorfahren, Kartenglieder glieder) {
    List<Integer> ziele = new ArrayList<>();
    for (Dokumentart stufe : List.of(Dokumentart.FACHPLAN, Dokumentart.PLAN)) {
      Bezug b = vorfahren.get(stufe);
      if (b != null && b.nummer() != null) {
        ziele.add(b.nummer());
      }
    }
    ziele.add(eingabe.karte().nummer());
    return new Abschnitt(
        Dokumentart.IDEE, List.of(Ideensuche.glied(eingabe.ideen(), ziele, glieder)));
  }
}
