package org.mwolff.manban.entscheidungskette.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.Aktivitaet;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.Art;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.Bezug;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.Bezugsart;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.Karte;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.Kommentar;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.LabelEreignis;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.ReviewVorReady;

/** Baut Eingaben der Rechnung für die Tests (Issues #2115, #2116). */
final class KettenAufbau {

  static final String TOKEN = "TOKEN";
  static final String SESSION = "SESSION";
  static final Instant LAUF = Instant.parse("2026-11-10T07:00:00Z");
  static final String ZUGANG = "kit-nacht";
  static final String AGENT = "claude-opus-5-5";
  static final Instant T0 = Instant.parse("2026-11-10T08:00:00Z");

  static final Karte FACHPLAN =
      karte(1, 10, Art.FACHLICH, "[Requirement] Kette", "Autor-Modell: claude-opus-5-5\n");
  static final Karte PLAN =
      karte(
          2,
          20,
          Art.PLAN,
          "[Plan] Kette",
          "Plan-Modell: claude-opus-5-5\nFachliche Quelle: Issue #10\n");
  static final Karte PLAN_TECHNISCH =
      karte(2, 20, Art.PLAN, "[Plan] Technik", "Plan-Modell: claude-opus-5-5\n");
  static final Karte PAKET =
      karte(3, 30, Art.ARBEITSPAKET, "Paket", "Plan: Issue #20\nFachliche Quelle: Issue #10\n");
  static final Karte TASK = karte(3, 30, Art.TASK, "[Task] Klein", "Autor-Modell: x\n");

  static Abschnitt abschnitt(Entscheidungskette kette, Dokumentart art) {
    return kette.abschnitte().stream().filter(a -> a.art() == art).findFirst().orElseThrow();
  }

  static Glied erstes(Entscheidungskette kette, Dokumentart art) {
    return abschnitt(kette, art).glieder().getFirst();
  }

  private final Karte geoeffnet;
  private final List<Bezug> vorfahren = new ArrayList<>();
  private final List<Bezug> nachfahren = new ArrayList<>();
  private final List<Karte> ideen = new ArrayList<>();
  private final List<Aktivitaet> aktivitaeten = new ArrayList<>();
  private final List<Kommentar> kommentare = new ArrayList<>();
  private final List<LabelEreignis> labels = new ArrayList<>();
  private @Nullable ReviewVorReady review;

  private KettenAufbau(Karte karte) {
    this.geoeffnet = karte;
  }

  static KettenAufbau an(Karte karte) {
    return new KettenAufbau(karte);
  }

  static Instant um(int minuten) {
    return T0.plusSeconds(60L * minuten);
  }

  static Karte karte(long id, int nummer, Art art, String titel, @Nullable String text) {
    return new Karte(id, nummer, titel, text, art, "Ready", "ready", false, null);
  }

  static Karte archiviert(Karte k) {
    return new Karte(
        k.id(),
        k.nummer(),
        k.titel(),
        k.beschreibung(),
        k.art(),
        k.spalte(),
        k.spaltenSchluessel(),
        true,
        k.wegBelegGeaendertAm());
  }

  static Karte wegGeaendert(Karte k) {
    return new Karte(
        k.id(),
        k.nummer(),
        k.titel(),
        k.beschreibung(),
        k.art(),
        k.spalte(),
        k.spaltenSchluessel(),
        k.archiviert(),
        um(90));
  }

  static Karte inSpalte(Karte k, String spalte, String schluessel) {
    return new Karte(
        k.id(),
        k.nummer(),
        k.titel(),
        k.beschreibung(),
        k.art(),
        spalte,
        schluessel,
        k.archiviert(),
        k.wegBelegGeaendertAm());
  }

  static Bezug vorhanden(Karte k) {
    return new Bezug(Bezugsart.VORHANDEN, k.nummer(), k);
  }

  static Bezug geloescht(int nummer) {
    return new Bezug(Bezugsart.GELOESCHT, nummer, null);
  }

  static Bezug papierkorb(int nummer) {
    return new Bezug(Bezugsart.PAPIERKORB, nummer, null);
  }

  static Bezug anderesProjekt() {
    return new Bezug(Bezugsart.ANDERES_PROJEKT, null, null);
  }

  KettenAufbau vorfahr(Bezug b) {
    vorfahren.add(b);
    return this;
  }

  KettenAufbau nachfahr(Bezug b) {
    nachfahren.add(b);
    return this;
  }

  KettenAufbau idee(Karte k) {
    ideen.add(k);
    return this;
  }

  KettenAufbau angelegt(
      long kartenId, @Nullable String herkunft, @Nullable Long person, Instant zeitpunkt) {
    aktivitaeten.add(
        new Aktivitaet(
            kartenId,
            person,
            "CREATED",
            zeitpunkt,
            herkunft,
            TOKEN.equals(herkunft) ? ZUGANG : null,
            TOKEN.equals(herkunft) ? AGENT : null,
            null,
            null));
    return this;
  }

  /** Ein Zug eines Menschen in der Oberfläche. */
  KettenAufbau zugVonMensch(long kartenId, @Nullable String schluessel, long person, Instant zeit) {
    return aktivitaet(
        new Aktivitaet(kartenId, person, "MOVED", zeit, SESSION, null, null, schluessel, null));
  }

  /** Ein Zug über ein Token; mit {@code laufStart} durch einen Lauf. */
  KettenAufbau zugUeberToken(
      long kartenId, @Nullable String schluessel, Instant zeit, @Nullable Instant laufStart) {
    return aktivitaet(
        new Aktivitaet(kartenId, null, "MOVED", zeit, TOKEN, ZUGANG, AGENT, schluessel, laufStart));
  }

  KettenAufbau label(LabelEreignis e) {
    labels.add(e);
    return this;
  }

  KettenAufbau reviewVorReady(boolean verlangt, @Nullable Instant geaendertAm) {
    this.review = new ReviewVorReady(verlangt, geaendertAm);
    return this;
  }

  KettenAufbau aktivitaet(Aktivitaet a) {
    aktivitaeten.add(a);
    return this;
  }

  KettenAufbau kommentar(long id, long kartenId, String text, Instant zeitpunkt) {
    return kommentar(
        new Kommentar(id, kartenId, text, zeitpunkt, TOKEN, ZUGANG, AGENT, null, null));
  }

  KettenAufbau kommentar(Kommentar k) {
    kommentare.add(k);
    return this;
  }

  KettenEingabe eingabe() {
    return new KettenEingabe(
        geoeffnet,
        List.copyOf(vorfahren),
        List.copyOf(nachfahren),
        List.copyOf(ideen),
        List.copyOf(aktivitaeten),
        List.copyOf(kommentare),
        List.copyOf(labels),
        review);
  }

  Entscheidungskette rechne() {
    return KettenRechner.rechne(eingabe());
  }
}
