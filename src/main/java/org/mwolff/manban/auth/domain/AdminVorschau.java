package org.mwolff.manban.auth.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.Identifiable;

/**
 * Eine Vorschau „Ansicht als XY“: Ein Plattform-Admin sieht die App nur lesend als ein anderes
 * Konto (Issue #1917, Plan #1914 E2, E3, E12). Derselbe Datensatz ist Zustand und Protokoll.
 *
 * <p>Persistiert wird nur der SHA-256 des Geheimnisses; den Klartext trägt allein das Cookie im
 * Browser des Admins.
 *
 * @param id technische ID; {@code null} vor der Persistierung
 * @param kennung öffentliche, eindeutige Kennung aus dem Cookie
 * @param geheimnisHash SHA-256 (Hex) des Geheimnisses aus dem Cookie
 * @param adminId handelnder Plattform-Admin; {@code null}, wenn sein Konto gelöscht wurde
 * @param adminName Anzeigename des Admins beim Start (Schnappschuss, bleibt nach dem Löschen)
 * @param zielId Konto, als das die App gezeigt wird
 * @param sitzungsGeneration Sitzungs-Generation des Admins beim Start
 * @param gestartetAm Zeitpunkt des Starts
 * @param laeuftAbAm Ablaufzeitpunkt, nach {@code gestartetAm}
 * @param beendetAm Zeitpunkt des Endes; {@code null} solange offen
 * @param endgrund Grund des Endes; {@code null} genau dann, wenn {@code beendetAm} leer ist
 */
public record AdminVorschau(
    @Nullable Long id,
    String kennung,
    String geheimnisHash,
    @Nullable Long adminId,
    String adminName,
    long zielId,
    long sitzungsGeneration,
    Instant gestartetAm,
    Instant laeuftAbAm,
    @Nullable Instant beendetAm,
    @Nullable VorschauEndgrund endgrund)
    implements Identifiable {

  public AdminVorschau {
    if (kennung.isBlank()) {
      throw new IllegalArgumentException("kennung darf nicht leer sein");
    }
    if (geheimnisHash.isBlank()) {
      throw new IllegalArgumentException("geheimnisHash darf nicht leer sein");
    }
    if (adminName.isBlank()) {
      throw new IllegalArgumentException("adminName darf nicht leer sein");
    }
    if (!laeuftAbAm.isAfter(gestartetAm)) {
      throw new IllegalArgumentException("laeuftAbAm muss nach gestartetAm liegen");
    }
    if ((beendetAm == null) != (endgrund == null)) {
      throw new IllegalArgumentException("Ende und Endgrund gibt es nur gemeinsam");
    }
  }

  /** Ob die Vorschau beendet ist. */
  public boolean beendet() {
    return beendetAm != null;
  }

  /** Kopie, die zum Zeitpunkt {@code am} mit dem Grund {@code grund} beendet ist. */
  public AdminVorschau beendet(Instant am, VorschauEndgrund grund) {
    return new AdminVorschau(
        id,
        kennung,
        geheimnisHash,
        adminId,
        adminName,
        zielId,
        sitzungsGeneration,
        gestartetAm,
        laeuftAbAm,
        am,
        grund);
  }
}
