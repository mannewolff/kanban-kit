package org.mwolff.manban.nightrun.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.Quantil;

/**
 * Die Rechnung der Kostenvoraussage eines Vorhabens (Issue #2003, Plan #1990 E3, E4, E6, E7, E12;
 * fachlich #1954 AK 4 bis 7). Rein, ohne Datenbank: Die Eingaben trägt {@code
 * VorhabenKostenService} zusammen, nach dem Muster von {@link ModellvergleichErmittlung}.
 *
 * <p><b>Spanne.</b> Jede Grundlage — Vorgangszahl und Preis je Stufe, Paketzahl, Paketpreis — geht
 * mit ihrem 15-, 50- und 85-%-Wert ein ({@link Quantil}, E4). Der typische Betrag rechnet mit den
 * 50-%-Werten, die Spanne mit den 15- und 85-%-Werten; er ist damit nicht der Median der
 * Gesamtkosten früherer Vorhaben.
 *
 * <p><b>Erste Stufe</b> ({@link Rechnung#ANFORDERUNG}): Je nicht durchlaufener Stufe der Dokumente
 * Vorgangszahl × Preis, bei einer begonnenen Stufe abzüglich des dort Angefallenen, mindestens 0.
 * Die Paketzahl ist die typische früherer Vorhaben, mindestens aber die bekannte; bekannte Pakete
 * rechnen nach ihrer Aufgabenstufe abzüglich des Angefallenen, Pakete in Done mit 0, die übrigen
 * erwarteten mit dem Paketpreis über alle Aufgabenstufen.
 *
 * <p><b>Zweite Stufe</b> ({@link Rechnung#PAKETE}): die bekannten Pakete wie oben, dazu die Stufen,
 * die nach dem Schnitt noch ausstehen oder begonnen sind.
 *
 * <p>Welche der beiden Rechnungen gilt, ist eine Eingabe (E12): Hält der Job „erstes Paket“ und
 * „nichts mehr ungeschnitten offen“ im selben Lauf fest, rechnet er beide nacheinander. Die
 * bisherigen Kosten kommen in jedem Fall hinzu — die Voraussage nennt das ganze Vorhaben.
 */
public final class KostenVoraussage {

  /** Die Stufen der Kette in ihrer Folge (E7). */
  private static final List<NightRunAttemptStage> KETTE =
      List.of(
          NightRunAttemptStage.PLAN,
          NightRunAttemptStage.REVIEW,
          NightRunAttemptStage.PAKETE,
          NightRunAttemptStage.ABDECKUNG,
          NightRunAttemptStage.UMSETZUNG);

  /** Die Stufen vor der Umsetzung, die eine Voraussage je Vorgang schätzt, in Kettenfolge. */
  static final List<NightRunAttemptStage> DOKUMENTSTUFEN = KETTE.subList(0, 4);

  /** Mindestzahl der Grundlage-Vorhaben (AK 7). */
  static final int MINDESTGRUNDLAGE = 3;

  private static final BigDecimal UNTEN = new BigDecimal("0.15");
  private static final BigDecimal TYPISCH = new BigDecimal("0.50");
  private static final BigDecimal OBEN = new BigDecimal("0.85");

  /** Die gewünschte Rechnung (E12). */
  public enum Rechnung {
    /** Erste Stufe: aus der Anforderung, solange noch geschnitten wird (AK 4). */
    ANFORDERUNG,
    /** Zweite Stufe: aus den Paketen (AK 5). */
    PAKETE
  }

  /** Wie die Voraussage entstand — so nennt sie die Zeile. */
  public enum Art {
    /** Erste Stufe bei einem Vorhaben mit fachlicher Anforderung. */
    ANFORDERUNG,
    /** Erste Stufe bei einem Vorhaben ohne fachliche Anforderung. */
    VOR_DEM_SCHNITT,
    /** Zweite Stufe. */
    PAKETE
  }

  /**
   * Die Aufgabenstufe eines Pakets, als eigener Typ der Domäne: Die Fassade {@code
   * card.application.Aufgabenstufe} darf sie nicht kennen.
   */
  public enum Paketstufe {
    LEICHT,
    MITTEL,
    SCHWER
  }

  /** Warum keine Voraussage beziffert wird (AK 7). */
  public enum Grund {
    /** Weniger als drei Grundlage-Vorhaben. */
    ZU_WENIG_VORHABEN,
    /** Eine noch zu schätzende Stufe hat keinen Preis beim zuletzt eingesetzten Modell. */
    STUFE_OHNE_PREIS,
    /** Pakete sind zu schätzen, aber es gibt keinen einzigen Paketpreis. */
    KEINE_PAKETPREISE
  }

  /**
   * Ein bekanntes Arbeitspaket des Vorhabens.
   *
   * @param nummer projektweite Nummer der Karte
   * @param done ob es in Done liegt — dann steht es nur in den bisherigen Kosten
   * @param stufe seine Aufgabenstufe; {@code null} ohne gültige Angabe
   * @param angefallen was an ihm schon angefallen ist
   */
  public record Paket(
      int nummer, boolean done, @Nullable Paketstufe stufe, BigDecimal angefallen) {}

  /**
   * Der Stand des Vorhabens, für das die Voraussage gilt.
   *
   * @param hatAnforderung ob eine fachliche Anforderung dazugehört
   * @param ungeschnittenOffen ob ein Plan oder eine Anforderung noch ungeschnitten offen ist
   * @param angefallenJeStufe je begonnener Stufe (ein Vorgang liegt vor, E7) die dort angefallenen
   *     Kosten; eine Stufe ohne Schlüssel ist nicht begonnen
   * @param pakete die bekannten Arbeitspakete
   * @param bisher die bisher am Vorhaben angefallenen Kosten
   * @param unvollstaendig ob Messungen oder Kostenangaben am Vorhaben fehlen
   */
  public record Vorhaben(
      boolean hatAnforderung,
      boolean ungeschnittenOffen,
      Map<NightRunAttemptStage, BigDecimal> angefallenJeStufe,
      List<Paket> pakete,
      BigDecimal bisher,
      boolean unvollstaendig) {}

  /**
   * Ein abgeschlossenes, vollständig aufbewahrtes Vorhaben der letzten zwölf Wochen.
   *
   * @param vorgaengeJeStufe Zahl seiner Vorgänge je Stufe, jeder Prüfer und jede Wiederholung
   *     einzeln
   * @param paketzahl Zahl seiner Arbeitspakete
   */
  public record GrundlageVorhaben(
      Map<NightRunAttemptStage, Integer> vorgaengeJeStufe, int paketzahl) {}

  /**
   * Die Preise der letzten zwölf Wochen im ganzen Projekt (E6).
   *
   * @param jeStufe je Stufe die Kosten der Vorgänge des dort zuletzt eingesetzten Modells
   * @param paketJeStufe je Aufgabenstufe die Gesamtpreise der Pakete beim zuletzt umsetzenden
   *     Modell
   * @param paketAlle dieselben Gesamtpreise über alle Aufgabenstufen, auch ohne Stufe
   */
  public record Preise(
      Map<NightRunAttemptStage, List<BigDecimal>> jeStufe,
      Map<Paketstufe, List<BigDecimal>> paketJeStufe,
      List<BigDecimal> paketAlle) {}

  /**
   * Die Eingaben der Rechnung.
   *
   * @param rechnung die gewünschte Rechnung (E12)
   * @param vorhaben der Stand des Vorhabens
   * @param grundlage die abgeschlossenen Vorhaben; die ohne Vorgang in einer Stufe der Dokumente
   *     zählen nicht (E3)
   * @param preise die Preise
   */
  public record Eingaben(
      Rechnung rechnung, Vorhaben vorhaben, List<GrundlageVorhaben> grundlage, Preise preise) {}

  /** Das Ergebnis: eine bezifferte Voraussage oder der Grund, warum es keine gibt. */
  public sealed interface Ergebnis permits Voraussage, ZuWenigDaten {}

  /**
   * Eine bezifferte Voraussage, in USD auf Cent gerundet.
   *
   * @param art wie sie entstand
   * @param typisch die Kosten des ganzen Vorhabens mit den 50-%-Werten
   * @param unten dasselbe mit den 15-%-Werten
   * @param oben dasselbe mit den 85-%-Werten
   * @param bisher die bisher angefallenen Kosten, in allen drei Beträgen enthalten
   * @param paketzahl aus wie vielen Paketen sie rechnet; in der ersten Stufe die typische Zahl,
   *     mindestens die bekannte
   * @param ohneStufe Zahl der bekannten Pakete, die mit dem Paketpreis über alle Aufgabenstufen
   *     rechnen, weil ihnen die Stufe oder ihrer Stufe der Preis fehlt
   * @param unvollstaendig ob am Vorhaben Messungen oder Kostenangaben fehlen
   */
  public record Voraussage(
      Art art,
      BigDecimal typisch,
      BigDecimal unten,
      BigDecimal oben,
      BigDecimal bisher,
      int paketzahl,
      int ohneStufe,
      boolean unvollstaendig)
      implements Ergebnis {}

  /** Für eine Voraussage liegen zu wenig Daten vor. */
  public record ZuWenigDaten(Grund grund) implements Ergebnis {}

  private KostenVoraussage() {}

  /** Die Voraussage zu den Eingaben. */
  public static Ergebnis rechne(Eingaben eingaben) {
    List<GrundlageVorhaben> grundlage =
        eingaben.grundlage().stream().filter(KostenVoraussage::hatJedeStufe).toList();
    if (grundlage.size() < MINDESTGRUNDLAGE) {
      return new ZuWenigDaten(Grund.ZU_WENIG_VORHABEN);
    }
    Vorhaben vorhaben = eingaben.vorhaben();
    Spanne stufen = stufen(grundlage, vorhaben, eingaben.preise().jeStufe());
    if (stufen == null) {
      return new ZuWenigDaten(Grund.STUFE_OHNE_PREIS);
    }
    Spanne typischePaketzahl =
        Spanne.von(grundlage.stream().map(g -> BigDecimal.valueOf(g.paketzahl())).toList());
    Paketschaetzung pakete = pakete(eingaben, typischePaketzahl);
    if (pakete == null) {
      return new ZuWenigDaten(Grund.KEINE_PAKETPREISE);
    }
    return voraussage(
        eingaben, vorhaben, stufen.plus(pakete.rest()), typischePaketzahl, pakete.ohneStufe());
  }

  /**
   * Die Schätzung der nicht durchlaufenen Dokumentstufen; {@code null}, wenn einer davon der Preis
   * fehlt.
   */
  private static @Nullable Spanne stufen(
      List<GrundlageVorhaben> grundlage,
      Vorhaben vorhaben,
      Map<NightRunAttemptStage, List<BigDecimal>> preise) {
    Spanne rest = Spanne.NULL;
    for (NightRunAttemptStage stufe : DOKUMENTSTUFEN) {
      if (durchlaufen(stufe, vorhaben)) {
        continue;
      }
      List<BigDecimal> stufenpreise = preise.getOrDefault(stufe, List.of());
      if (stufenpreise.isEmpty()) {
        return null;
      }
      Spanne zahl =
          Spanne.von(
              grundlage.stream()
                  .map(g -> BigDecimal.valueOf(g.vorgaengeJeStufe().getOrDefault(stufe, 0)))
                  .toList());
      rest =
          rest.plus(
              zahl.mal(Spanne.von(stufenpreise))
                  .abzueglich(vorhaben.angefallenJeStufe().getOrDefault(stufe, BigDecimal.ZERO)));
    }
    return rest;
  }

  /** Die Schätzung der Pakete und wie viele davon ohne eigenen Stufenpreis rechnen. */
  private record Paketschaetzung(Spanne rest, int ohneStufe) {}

  /**
   * Die Schätzung der offenen bekannten und, in der ersten Stufe, der übrigen erwarteten Pakete;
   * {@code null}, wenn Pakete zu schätzen sind, aber kein Paketpreis vorliegt.
   */
  private static @Nullable Paketschaetzung pakete(Eingaben eingaben, Spanne typischePaketzahl) {
    List<Paket> bekannte = eingaben.vorhaben().pakete();
    Spanne erwartet =
        eingaben.rechnung() == Rechnung.ANFORDERUNG
            ? typischePaketzahl
                .mindestens(BigDecimal.valueOf(bekannte.size()))
                .minus(bekannte.size())
            : Spanne.NULL;
    List<Paket> offen = bekannte.stream().filter(p -> !p.done()).toList();
    if (offen.isEmpty() && erwartet.oben().signum() == 0) {
      return new Paketschaetzung(Spanne.NULL, 0);
    }
    Preise preise = eingaben.preise();
    if (preise.paketAlle().isEmpty()) {
      return null;
    }
    Spanne alle = Spanne.von(preise.paketAlle());
    Spanne rest = erwartet.mal(alle);
    int ohneStufe = 0;
    for (Paket paket : offen) {
      List<BigDecimal> eigene =
          paket.stufe() == null
              ? List.of()
              : preise.paketJeStufe().getOrDefault(paket.stufe(), List.of());
      if (eigene.isEmpty()) {
        ohneStufe++;
      }
      Spanne preis = eigene.isEmpty() ? alle : Spanne.von(eigene);
      rest = rest.plus(preis.abzueglich(paket.angefallen()));
    }
    return new Paketschaetzung(rest, ohneStufe);
  }

  private static Voraussage voraussage(
      Eingaben eingaben, Vorhaben vorhaben, Spanne rest, Spanne typischePaketzahl, int ohneStufe) {
    int bekannt = vorhaben.pakete().size();
    boolean ersteStufe = eingaben.rechnung() == Rechnung.ANFORDERUNG;
    Art art;
    int paketzahl;
    if (ersteStufe) {
      art = vorhaben.hatAnforderung() ? Art.ANFORDERUNG : Art.VOR_DEM_SCHNITT;
      paketzahl =
          Math.max(
              typischePaketzahl.typisch().setScale(0, RoundingMode.HALF_UP).intValueExact(),
              bekannt);
    } else {
      art = Art.PAKETE;
      paketzahl = bekannt;
    }
    BigDecimal bisher = vorhaben.bisher();
    return new Voraussage(
        art,
        cent(bisher.add(rest.typisch())),
        cent(bisher.add(rest.unten())),
        cent(bisher.add(rest.oben())),
        cent(bisher),
        paketzahl,
        ohneStufe,
        vorhaben.unvollstaendig());
  }

  /** E3: Ein Grundlage-Vorhaben zählt nur mit mindestens einem Vorgang in jeder Dokumentstufe. */
  private static boolean hatJedeStufe(GrundlageVorhaben vorhaben) {
    return DOKUMENTSTUFEN.stream()
        .allMatch(s -> vorhaben.vorgaengeJeStufe().getOrDefault(s, 0) > 0);
  }

  /**
   * E7: Eine Stufe ist durchlaufen, sobald eine spätere Stufe begonnen hat und kein Dokument des
   * Vorhabens mehr ungeschnitten offen ist.
   */
  private static boolean durchlaufen(NightRunAttemptStage stufe, Vorhaben vorhaben) {
    return !vorhaben.ungeschnittenOffen()
        && vorhaben.angefallenJeStufe().keySet().stream()
            .anyMatch(begonnen -> KETTE.indexOf(begonnen) > KETTE.indexOf(stufe));
  }

  private static BigDecimal cent(BigDecimal betrag) {
    return betrag.setScale(2, RoundingMode.HALF_UP);
  }

  /** Ein Wert mit seinem 15-, 50- und 85-%-Quantil, oder eine Rechnung daraus. */
  private record Spanne(BigDecimal unten, BigDecimal typisch, BigDecimal oben) {

    static final Spanne NULL = new Spanne(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

    /** Die Quantile nicht leerer Werte. */
    static Spanne von(Collection<BigDecimal> werte) {
      return new Spanne(
          Quantil.von(werte, UNTEN).orElseThrow(),
          Quantil.von(werte, TYPISCH).orElseThrow(),
          Quantil.von(werte, OBEN).orElseThrow());
    }

    Spanne plus(Spanne andere) {
      return new Spanne(
          unten.add(andere.unten), typisch.add(andere.typisch), oben.add(andere.oben));
    }

    Spanne mal(Spanne andere) {
      return new Spanne(
          unten.multiply(andere.unten),
          typisch.multiply(andere.typisch),
          oben.multiply(andere.oben));
    }

    Spanne minus(int abzug) {
      BigDecimal betrag = BigDecimal.valueOf(abzug);
      return je(w -> w.subtract(betrag));
    }

    /** Je Quantil der Wert abzüglich {@code angefallen}, mindestens 0. */
    Spanne abzueglich(BigDecimal angefallen) {
      return je(w -> w.subtract(angefallen).max(BigDecimal.ZERO));
    }

    Spanne mindestens(BigDecimal untergrenze) {
      return je(w -> w.max(untergrenze));
    }

    private Spanne je(UnaryOperator<BigDecimal> f) {
      return new Spanne(f.apply(unten), f.apply(typisch), f.apply(oben));
    }
  }
}
