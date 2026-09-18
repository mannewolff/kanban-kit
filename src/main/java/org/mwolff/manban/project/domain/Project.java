package org.mwolff.manban.project.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.Identifiable;

/**
 * Projekt — oberste Ebene (Tenant). Boards, Mitgliedschaften und alles Weitere hängen darunter.
 *
 * @param id technische ID; {@code null} vor der Persistierung
 * @param name Projektname
 * @param ownerUserId Ersteller/Eigentümer
 * @param createdAt Erstellzeitpunkt
 * @param interactiveUsageSince Startzeitpunkt der <b>ersten</b> gemeldeten interaktiven Sitzung;
 *     {@code null}, solange keine gemeldet wurde (Issue #1012, Plan #1007 E18). Der Wert steht ein
 *     für alle Mal — daran unterscheidet die Auswertung „nie erfasst" von „erfasst, dann
 *     verdrängt". Aus der ältesten vorhandenen Sitzung ließe sich die Grenze nicht ableiten, weil
 *     die mit dem Ringpuffer nach vorn wandert. Gesetzt wird er ausschließlich über {@code
 *     ProjectRepository#setInteractiveUsageSinceIfAbsent}, nie über {@code save}.
 */
public record Project(
    @Nullable Long id,
    String name,
    Long ownerUserId,
    Instant createdAt,
    @Nullable Instant interactiveUsageSince)
    implements Identifiable {

  public Project withName(String newName) {
    return new Project(id, newName, ownerUserId, createdAt, interactiveUsageSince);
  }
}
