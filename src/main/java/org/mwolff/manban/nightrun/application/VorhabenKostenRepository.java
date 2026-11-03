package org.mwolff.manban.nightrun.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.nightrun.domain.KostenVoraussage.Art;
import org.mwolff.manban.nightrun.domain.KostenVoraussage.Voraussage;

/**
 * Port für die festgehaltenen Kosten je Vorhaben (Issue #2004, Plan #1990 E8 bis E10; fachlich
 * #1954 AK 8) — Tabellen {@code vorhaben_kosten} und {@code vorhaben_kosten_einfuehrung}.
 *
 * <p>Je Vorhaben höchstens eine Zeile. Festgehaltene Werte überleben Umzuordnungen, Archivieren und
 * das Verdrängen alter Messungen; aus den Rohdaten sind sie später nicht mehr herstellbar. Mit der
 * Vorhaben-Karte entfällt die Zeile ({@code ON DELETE CASCADE}).
 */
public interface VorhabenKostenRepository {

  /** Die festgehaltenen Zeilen aller Vorhaben des Projekts, aufsteigend nach Vorhaben-ID. */
  List<VorhabenKosten> findByProjectId(long projectId);

  /** Legt die Zeile des Vorhabens an oder ersetzt sie vollständig. */
  void speichere(VorhabenKosten kosten);

  /** Zeitpunkt der Einführung für das Projekt (E8) — leer vor dem ersten Lauf des Jobs. */
  Optional<Instant> eingefuehrtAm(long projectId);

  /** Vermerkt die Einführung für das Projekt; ein zweiter Aufruf ändert nichts. */
  void einfuehren(long projectId, Instant am);

  /** Ob eine Voraussage beziffert oder als „keine Voraussage“ festgehalten wurde. */
  enum Stand {
    BEZIFFERT,
    KEINE
  }

  /**
   * Eine festgehaltene Voraussage. Bei {@link Stand#KEINE} sind Art und Beträge {@code null}.
   *
   * @param stand beziffert oder keine
   * @param art wie die Voraussage entstand
   * @param typischUsd typischer Betrag
   * @param untenUsd unteres Ende der Spanne
   * @param obenUsd oberes Ende der Spanne
   * @param festgehaltenAt wann sie festgehalten wurde
   */
  record FestgehalteneVoraussage(
      Stand stand,
      @Nullable Art art,
      @Nullable BigDecimal typischUsd,
      @Nullable BigDecimal untenUsd,
      @Nullable BigDecimal obenUsd,
      Instant festgehaltenAt) {

    /** „Keine Voraussage“, festgehalten zum genannten Zeitpunkt. */
    public static FestgehalteneVoraussage keine(Instant am) {
      return new FestgehalteneVoraussage(Stand.KEINE, null, null, null, null, am);
    }

    /** Eine bezifferte Voraussage, festgehalten zum genannten Zeitpunkt. */
    public static FestgehalteneVoraussage beziffert(Voraussage voraussage, Instant am) {
      return new FestgehalteneVoraussage(
          Stand.BEZIFFERT,
          voraussage.art(),
          voraussage.typisch(),
          voraussage.unten(),
          voraussage.oben(),
          am);
    }
  }

  /**
   * Die festgehaltenen Kosten eines Vorhabens.
   *
   * @param epicId ID der Vorhaben-Karte
   * @param projectId das Projekt
   * @param anforderung die Voraussage aus der Anforderung; {@code null}, solange sie nicht
   *     festgehalten ist
   * @param pakete die Voraussage aus den Paketen; {@code null}, solange sie nicht festgehalten ist
   * @param abgeschlossenAt Zeitpunkt des letzten Abschlusses; {@code null}, solange das Vorhaben
   *     nicht abgeschlossen ist
   * @param tatsaechlichUsd die tatsächlichen Kosten; {@code null} ohne Abschluss
   * @param tatsaechlichUnvollstaendig ob beim Festhalten Messungen fehlten (E3)
   * @param kostenKarten die Kostenkarten beim Abschluss (E9); leer ohne Abschluss
   */
  record VorhabenKosten(
      long epicId,
      long projectId,
      @Nullable FestgehalteneVoraussage anforderung,
      @Nullable FestgehalteneVoraussage pakete,
      @Nullable Instant abgeschlossenAt,
      @Nullable BigDecimal tatsaechlichUsd,
      boolean tatsaechlichUnvollstaendig,
      List<Integer> kostenKarten) {

    public VorhabenKosten {
      kostenKarten = List.copyOf(kostenKarten);
    }

    /** Eine Zeile ohne festgehaltenen Wert. */
    public static VorhabenKosten leer(long epicId, long projectId) {
      return new VorhabenKosten(epicId, projectId, null, null, null, null, false, List.of());
    }

    /** Dieselbe Zeile mit der Voraussage aus der Anforderung. */
    public VorhabenKosten mitAnforderung(FestgehalteneVoraussage voraussage) {
      return new VorhabenKosten(
          epicId,
          projectId,
          voraussage,
          pakete,
          abgeschlossenAt,
          tatsaechlichUsd,
          tatsaechlichUnvollstaendig,
          kostenKarten);
    }

    /** Dieselbe Zeile mit der Voraussage aus den Paketen. */
    public VorhabenKosten mitPaketen(FestgehalteneVoraussage voraussage) {
      return new VorhabenKosten(
          epicId,
          projectId,
          anforderung,
          voraussage,
          abgeschlossenAt,
          tatsaechlichUsd,
          tatsaechlichUnvollstaendig,
          kostenKarten);
    }

    /** Dieselbe Zeile mit Abschluss und tatsächlichen Kosten. */
    public VorhabenKosten mitAbschluss(
        Instant am, BigDecimal tatsaechlich, boolean unvollstaendig, List<Integer> karten) {
      return new VorhabenKosten(
          epicId, projectId, anforderung, pakete, am, tatsaechlich, unvollstaendig, karten);
    }

    /** Dieselbe Zeile ohne Abschluss; die Voraussagen bleiben (E10). */
    public VorhabenKosten wiederOffen() {
      return new VorhabenKosten(
          epicId, projectId, anforderung, pakete, null, null, false, List.of());
    }
  }
}
