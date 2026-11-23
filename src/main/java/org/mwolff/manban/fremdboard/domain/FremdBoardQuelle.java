package org.mwolff.manban.fremdboard.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Was ein fremdes System liefern muss, damit kanban-kit sein Board spiegeln kann (Issue #2158, Plan
 * #2152 A15). GitLab ist die erste Umsetzung; Jira, Azure und GitHub folgen auf demselben Weg.
 * Dieselbe Liste steht in der Betreiber-Dokumentation unter „Fremdes Board anbinden“.
 *
 * <p>Die Quelle liest nur; kanban-kit schreibt nie in das fremde System (A5). Jede Methode bekommt
 * die {@link Verbindung} des Projekts, aus der die Umsetzung Adresse, Projektpfad und Zugang nimmt.
 * Scheitert ein Abruf, wirft sie {@link FremdAbrufException} oder eine Unterklasse.
 */
public interface FremdBoardQuelle {

  /** Das fremde System, das diese Quelle liest. */
  FremdBoardArt art();

  /**
   * Die Nummern aller Karten des angebundenen Projekts, offene wie geschlossene, getrennt nach
   * sichtbaren und vertraulichen (Issue #2200).
   */
  FremdNummern nummern(Verbindung verbindung);

  /**
   * Die Karten, die sich im fremden System nach {@code seit} geändert haben, offene wie
   * geschlossene, die am längsten unveränderte zuerst — Grundlage des regelmäßigen Abgleichs (A7).
   */
  List<FremdKarte> geaenderteKarten(Verbindung verbindung, Instant seit);

  /**
   * Die Karte mit Titel, Text, Labels und Zustand, aus denen {@link StatusZuordnung} ihren Status
   * ableitet; leer, wenn es sie im fremden System nicht (mehr) gibt.
   */
  Optional<FremdKarte> karte(Verbindung verbindung, long nummer);

  /** Die Wechsel an der Karte mit Zeit, Akteur und Kennung, ältester zuerst. */
  List<FremdEreignis> ereignisse(Verbindung verbindung, long nummer);

  /**
   * Die Kommentare an der Karte mit Autor, Anlage- und Änderungszeit, ältester zuerst, dazu die
   * Kennungen der vertraulichen (Issue #2200).
   */
  FremdNotizen notizen(Verbindung verbindung, long nummer);

  /** Die Adresse, unter der die Karte im fremden System zu öffnen ist („In GitLab öffnen“). */
  String adresse(Verbindung verbindung, long nummer);
}
