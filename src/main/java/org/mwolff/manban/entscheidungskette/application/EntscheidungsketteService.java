package org.mwolff.manban.entscheidungskette.application;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.auth.application.UserLookup;
import org.mwolff.manban.auth.application.UserSummary;
import org.mwolff.manban.card.application.CardNotFoundException;
import org.mwolff.manban.card.application.CardService;
import org.mwolff.manban.card.application.KartenKetteLeser;
import org.mwolff.manban.card.application.KartenKetteLeser.KartenKette;
import org.mwolff.manban.card.application.KartenKetteLeser.KettenAktivitaet;
import org.mwolff.manban.card.application.KartenKetteLeser.KettenGlied;
import org.mwolff.manban.card.application.KartenKetteLeser.KettenKarte;
import org.mwolff.manban.card.application.KartenKetteLeser.KettenLabelEreignis;
import org.mwolff.manban.comment.application.CommentService;
import org.mwolff.manban.comment.application.CommentService.KettenKommentarView;
import org.mwolff.manban.common.KitMeldungen;
import org.mwolff.manban.entscheidungskette.application.EntscheidungsketteView.AbschnittView;
import org.mwolff.manban.entscheidungskette.application.EntscheidungsketteView.AkteurView;
import org.mwolff.manban.entscheidungskette.application.EntscheidungsketteView.GliedView;
import org.mwolff.manban.entscheidungskette.application.EntscheidungsketteView.PruefungView;
import org.mwolff.manban.entscheidungskette.domain.Abschnitt;
import org.mwolff.manban.entscheidungskette.domain.Akteur;
import org.mwolff.manban.entscheidungskette.domain.CodeReview;
import org.mwolff.manban.entscheidungskette.domain.Einzelheiten;
import org.mwolff.manban.entscheidungskette.domain.Entscheidungskette;
import org.mwolff.manban.entscheidungskette.domain.Freigabeangaben;
import org.mwolff.manban.entscheidungskette.domain.Glied;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.Aktivitaet;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.Art;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.Bezug;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.Bezugsart;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.Karte;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.Kommentar;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.LabelEreignis;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.ReviewVorReady;
import org.mwolff.manban.entscheidungskette.domain.KettenRechner;
import org.mwolff.manban.entscheidungskette.domain.Pruefung;
import org.mwolff.manban.entscheidungskette.domain.Uebertragung;
import org.mwolff.manban.entscheidungskette.domain.Umsetzungsangaben;
import org.mwolff.manban.entscheidungskette.domain.Veroeffentlichung;
import org.mwolff.manban.project.application.KitReviewVorReadyReader;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die Entscheidungskette einer Karte (Issue #2117, Plan #2108 E1, E3, E5, E6, E7, E12, E17, E18):
 * prüft das Leserecht wie der Herkunftsbaum, lädt Karten, Kommentare und die gemeldete Einstellung
 * zur Prüfung vor Ready, rechnet mit {@link KettenRechner} und löst die Namen der Menschen auf.
 *
 * <p>Eine neue Rolle gibt es nicht: Wer die Karte sehen darf, sieht ihre Kette. Ohne
 * Zwischenspeicher und ohne Uhr — zweimal aufgerufen, ergibt derselbe Stand dieselbe Antwort.
 */
// PMD.CouplingBetweenObjects, PMD.ExcessiveImports: Der Dienst ist die eine Stelle, die die
// Fassaden von card, comment,
// project und auth mit der Rechnung verbindet (Plan #2108 E1); er kennt darum deren Sichten und die
// Eingabe- und Ausgabetypen der Rechnung. Eine Aufteilung verschöbe nur die Übersetzung.
@SuppressWarnings({"PMD.CouplingBetweenObjects", "PMD.ExcessiveImports"})
@Service
public class EntscheidungsketteService {

  private final CardService cards;
  private final PermissionChecker permissions;
  private final KartenKetteLeser leser;
  private final CommentService comments;
  private final KitReviewVorReadyReader reviewVorReady;
  private final UserLookup users;

  public EntscheidungsketteService(
      CardService cards,
      PermissionChecker permissions,
      KartenKetteLeser leser,
      CommentService comments,
      KitReviewVorReadyReader reviewVorReady,
      UserLookup users) {
    this.cards = cards;
    this.permissions = permissions;
    this.leser = leser;
    this.comments = comments;
    this.reviewVorReady = reviewVorReady;
    this.users = users;
  }

  /**
   * Die Kette der Karte.
   *
   * @return {@link EntscheidungsketteView#UNSICHTBAR} in einem Projekt ohne Kit, an einer Idee und
   *     an einem Vorhaben
   * @throws CardNotFoundException wenn es die Karte nicht gibt, sie im Papierkorb liegt oder der
   *     Aufrufer kein Mitglied ihres Projekts ist — alles gleich, damit ein Fremder nichts über die
   *     Existenz erfährt
   */
  @Transactional(readOnly = true)
  public EntscheidungsketteView kette(long userId, long cardId) {
    long projekt = cards.requireProjectId(cardId);
    try {
      permissions.requireMembership(userId, projekt);
    } catch (ProjectNotFoundException e) {
      throw new CardNotFoundException(e);
    }
    KartenKette kette = leser.lade(cardId);
    Art art = Art.valueOf(kette.karte().art());
    if (art == Art.IDEE || art == Art.VORHABEN || !arbeitetMitKit(projekt)) {
      return EntscheidungsketteView.UNSICHTBAR;
    }
    KettenEingabe eingabe =
        new KettenEingabe(
            karte(kette.karte()),
            kette.vorfahren().stream().map(EntscheidungsketteService::bezug).toList(),
            kette.nachfahren().stream().map(EntscheidungsketteService::bezug).toList(),
            kette.ideen().stream().map(EntscheidungsketteService::karte).toList(),
            kette.aktivitaeten().stream().map(EntscheidungsketteService::aktivitaet).toList(),
            comments.kettenKommentare(kartenIds(kette)).stream()
                .map(EntscheidungsketteService::kommentar)
                .toList(),
            kette.labelEreignisse().stream().map(EntscheidungsketteService::labelEreignis).toList(),
            reviewVorReady
                .meldung(projekt)
                .map(m -> new ReviewVorReady(m.wert(), m.geaendertAm()))
                .orElse(null));
    return new Namen().sicht(KettenRechner.rechne(eingabe));
  }

  /** Eine Karte, die das Kit angelegt hat, oder ein Kommentar des Kits im Projekt (E6). */
  private boolean arbeitetMitKit(long projekt) {
    return leser.traegtKitKarte(projekt)
        || comments.projektTraegtKitKommentar(projekt, KitMeldungen.festeAnfaenge());
  }

  /** Die Karten der Kette im Bestand: die geöffnete, Vorfahren, Nachfahren und Ideen. */
  private static Set<Long> kartenIds(KartenKette kette) {
    Set<Long> ids = new LinkedHashSet<>();
    ids.add(kette.karte().id());
    for (List<KettenGlied> glieder : List.of(kette.vorfahren(), kette.nachfahren())) {
      for (KettenGlied g : glieder) {
        KettenKarte k = g.karte();
        if (k != null) {
          ids.add(k.id());
        }
      }
    }
    kette.ideen().forEach(i -> ids.add(i.id()));
    return ids;
  }

  private static Karte karte(KettenKarte k) {
    return new Karte(
        k.id(),
        k.nummer(),
        k.titel(),
        k.beschreibung(),
        Art.valueOf(k.art()),
        k.spalte(),
        k.spaltenSchluessel(),
        k.archiviert(),
        k.wegBelegGeaendertAm());
  }

  private static Bezug bezug(KettenGlied g) {
    KettenKarte k = g.karte();
    return new Bezug(
        Bezugsart.valueOf(g.zustand().name()), g.nummer(), k == null ? null : karte(k));
  }

  private static Aktivitaet aktivitaet(KettenAktivitaet a) {
    return new Aktivitaet(
        a.kartenId(),
        a.actorUserId(),
        a.type(),
        a.zeitpunkt(),
        a.origin(),
        a.tokenName(),
        a.agent(),
        a.spaltenSchluessel(),
        a.laufStart());
  }

  private static LabelEreignis labelEreignis(KettenLabelEreignis e) {
    return new LabelEreignis(
        e.kartenId(), e.labelName(), e.zeitpunkt(), e.actorUserId(), e.origin(), e.tokenName());
  }

  private static Kommentar kommentar(KettenKommentarView k) {
    return new Kommentar(
        k.id(),
        k.cardId(),
        k.body(),
        k.createdAt(),
        k.origin(),
        k.tokenName(),
        k.agent(),
        k.vonHandGeaendertAm(),
        k.vonHandKopfzeile());
  }

  /** Übersetzt die Kette in die Sicht und löst je Person den Anzeigenamen einmal auf. */
  private final class Namen {

    private final Map<Long, Optional<String>> bekannt = new HashMap<>();

    EntscheidungsketteView sicht(Entscheidungskette kette) {
      return new EntscheidungsketteView(
          true,
          kette.art(),
          kette.abschnitte().stream().map(this::abschnitt).toList(),
          kette.nachfahren());
    }

    private AbschnittView abschnitt(Abschnitt a) {
      return new AbschnittView(a.art(), a.glieder().stream().map(this::glied).toList());
    }

    private GliedView glied(Glied g) {
      Einzelheiten e = g.einzelheiten();
      Pruefung p = g.pruefung();
      return new GliedView(
          g.art(),
          g.zustand(),
          akteur(g.freigegebenVon()),
          akteur(g.ausgefuehrtVon()),
          g.zeitpunkt(),
          g.ergebnis(),
          g.quelle(),
          g.hinweise(),
          p == null
              ? null
              : new PruefungView(
                  p.art(), p.runde(), p.pruefer(), p.funde(), glied(p.einarbeitung())),
          e instanceof Freigabeangaben f ? f : null,
          e instanceof Umsetzungsangaben u ? u : null,
          e instanceof CodeReview c ? c : null,
          e instanceof Uebertragung u ? u : null,
          e instanceof Veroeffentlichung v ? v : null);
    }

    /**
     * Ein Mensch mit Personen-ID erscheint mit Anzeigenamen; ein gelöschter Nutzer ohne Namen. Eine
     * Personen-ID trägt nur ein Mensch ({@link Akteur}), darum entscheidet sie allein.
     */
    private @Nullable AkteurView akteur(@Nullable Akteur a) {
      if (a == null) {
        return null;
      }
      Long person = a.person();
      String name = person != null ? anzeigename(person) : a.name();
      return new AkteurView(a.typ(), name, a.modell());
    }

    private @Nullable String anzeigename(long person) {
      return bekannt
          .computeIfAbsent(person, id -> users.findById(id).map(UserSummary::displayName))
          .orElse(null);
    }
  }
}
