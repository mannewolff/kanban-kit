package org.mwolff.manban.entscheidungskette.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.Aktivitaet;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.Karte;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.Kommentar;
import org.mwolff.manban.entscheidungskette.domain.Paketmeldungen.Gelesen;

/**
 * Die Versuche der Umsetzung am Paket (Issue #2116, Plan #2108 E10). Ein Versuch beginnt mit jedem
 * Zug nach In Progress und endet mit dem Abschlussbericht oder als zurückgezogen mit einem Zug nach
 * Backlog oder Ready; ein Bericht ohne offenen Versuch ist ein eigener. Ein Zug ohne bestimmbare
 * Spalte steht nicht in der Eingabe und beginnt und beendet darum keinen Versuch.
 */
final class Umsetzungsversuche {

  private static final String BACKLOG = "BACKLOG";
  private static final String READY = "READY";
  private static final String IN_PROGRESS = "IN_PROGRESS";

  private Umsetzungsversuche() {}

  /**
   * Die Versuche in ihrer Folge; ohne jeden ein Glied „nicht erfasst“. Bei gleichem Zeitpunkt kommt
   * der Zug vor dem Bericht.
   *
   * @param zuege die Züge des Pakets mit bestimmbarer Spalte, nach Zeitpunkt
   * @param berichte die Abschlussberichte des Pakets, nach Zeitpunkt
   */
  static List<Glied> glieder(Karte paket, List<Aktivitaet> zuege, List<Gelesen> berichte) {
    Folge folge = new Folge(paket);
    int naechster = 0;
    for (Aktivitaet zug : zuege) {
      while (naechster < berichte.size()
          && berichte.get(naechster).kommentar().zeitpunkt().isBefore(zug.zeitpunkt())) {
        folge.bericht(berichte.get(naechster));
        naechster++;
      }
      folge.zug(zug);
    }
    berichte.subList(naechster, berichte.size()).forEach(folge::bericht);
    folge.schliesse(null);
    return folge.versuche.isEmpty()
        ? List.of(
            Glied.ohneAngaben(Gliedart.UMSETZUNG, Zustand.NICHT_ERFASST, Verweis.KEINER, Set.of()))
        : List.copyOf(folge.versuche);
  }

  /** Ein Versuch ohne Bericht: Zug und Herkunft tragen Akteur und Zeitpunkt. */
  private static Glied ohneBericht(Karte paket, Aktivitaet beginn, @Nullable Aktivitaet rueckzug) {
    Umsetzungsangaben angaben =
        rueckzug == null
            ? new Umsetzungsangaben(Umsetzungsausgang.OFFEN, null, null, Angabe.LEER)
            : new Umsetzungsangaben(
                Umsetzungsausgang.ZURUECKGEZOGEN, rueckzug.zeitpunkt(), null, Angabe.LEER);
    return new Glied(
        Gliedart.UMSETZUNG,
        Zustand.ERFASST,
        null,
        zugAkteur(beginn),
        beginn.zeitpunkt(),
        null,
        Kartenglieder.verweis(paket),
        Set.of(),
        null,
        angaben);
  }

  /**
   * Ein abgeschlossener Versuch. Ist der Bericht von Hand geändert, gelten seine Angaben nicht;
   * ohne Zug nach In Progress ist der Versuch dann nicht erfasst (Kriterium 13).
   */
  private static Glied mitBericht(Karte paket, @Nullable Aktivitaet beginn, Gelesen bericht) {
    Kommentar k = bericht.kommentar();
    Verweis quelle = Kartenglieder.quelle(k, paket);
    if (bericht.meldung() instanceof Abschlussbericht gelesen) {
      return new Glied(
          Gliedart.UMSETZUNG,
          Zustand.ERFASST,
          null,
          umsetzer(beginn, k, gelesen),
          beginn == null ? k.zeitpunkt() : beginn.zeitpunkt(),
          null,
          quelle,
          Set.of(),
          null,
          new Umsetzungsangaben(
              Umsetzungsausgang.ABGESCHLOSSEN,
              k.zeitpunkt(),
              gelesen.commit(),
              gelesen.prueflauf()));
    }
    Set<Hinweis> vonHand = Set.of(Hinweis.VON_HAND_GEAENDERT);
    if (beginn == null) {
      return Glied.ohneAngaben(Gliedart.UMSETZUNG, Zustand.NICHT_ERFASST, quelle, vonHand);
    }
    return new Glied(
        Gliedart.UMSETZUNG,
        Zustand.ERFASST,
        null,
        zugAkteur(beginn),
        beginn.zeitpunkt(),
        null,
        quelle,
        vonHand,
        null,
        new Umsetzungsangaben(Umsetzungsausgang.ABGESCHLOSSEN, k.zeitpunkt(), null, Angabe.LEER));
  }

  /** {@code Umgesetzt von:}, sonst der Zug nach In Progress, sonst der Zugang des Berichts. */
  private static @Nullable Akteur umsetzer(
      @Nullable Aktivitaet beginn, Kommentar k, Abschlussbericht gelesen) {
    String modell = gelesen.umgesetztVon();
    if (modell != null) {
      return Akteur.modell(modell);
    }
    return beginn == null ? Akteur.werkzeug(k.tokenName(), k.agent()) : zugAkteur(beginn);
  }

  private static @Nullable Akteur zugAkteur(Aktivitaet zug) {
    return Akteur.nachHerkunft(zug.herkunft(), zug.person(), zug.tokenName(), zug.agent());
  }

  /** Der Gang durch Züge und Berichte mit dem gerade offenen Versuch. */
  private static final class Folge {

    private final Karte paket;
    private final List<Glied> versuche = new ArrayList<>();
    private @Nullable Aktivitaet offen;

    Folge(Karte paket) {
      this.paket = paket;
    }

    void zug(Aktivitaet zug) {
      String ziel = zug.spaltenSchluessel();
      if (IN_PROGRESS.equals(ziel)) {
        schliesse(null);
        offen = zug;
      } else if (READY.equals(ziel) || BACKLOG.equals(ziel)) {
        schliesse(zug);
      }
    }

    void bericht(Gelesen bericht) {
      versuche.add(mitBericht(paket, offen, bericht));
      offen = null;
    }

    /** Schließt den offenen Versuch: zurückgezogen mit {@code rueckzug}, sonst offen. */
    void schliesse(@Nullable Aktivitaet rueckzug) {
      Aktivitaet beginn = offen;
      if (beginn != null) {
        versuche.add(ohneBericht(paket, beginn, rueckzug));
        offen = null;
      }
    }
  }
}
