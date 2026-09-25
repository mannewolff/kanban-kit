package org.mwolff.manban.card.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.Identifiable;

/**
 * Karte — Kern-Aggregat. Die {@code number} ist board-scoped (eindeutig pro Board).
 *
 * <p>Eine Karte gehört immer zu einem Projekt ({@code projectId}) und immer zu einem Board: {@code
 * boardId}, {@code columnId} und {@code number} sind gesetzt (Issue #1204). Bis zum Rückbau des
 * Ideen-Pools gab es daneben die board-lose Pool-Idee — sie hatte Board, Spalte und teils die
 * Nummer nicht, und jede Sicht auf eine Karte musste den Zustand mitdenken. Diesen Zustand kennt
 * die Anwendung nicht mehr; die Datenbank schließt ihn mit Migration V44 (#1205) auch aus.
 *
 * <p>Ein Datensatz ist entweder eine normale Karte ({@link CardType#CARD}) oder ein Vorhaben
 * ({@link CardType#EPIC}). Vorhaben nehmen nicht am Spalten-Workflow teil (keine aktive Position)
 * und können Kinder gruppieren; eine Karte verweist über {@code parentId} auf ihr Vorhaben.
 *
 * @param id technische ID; {@code null} vor der Persistierung
 * @param boardId zugehöriges Board (immer gesetzt)
 * @param columnId aktuelle Spalte (immer gesetzt)
 * @param number board-scoped Anzeigenummer (immer gesetzt)
 * @param title Titel
 * @param description Markdown-Beschreibung (nullable)
 * @param positionInColumn Position in der Spalte
 * @param archived ob archiviert (dann außerhalb des aktiven Positions-Namespace)
 * @param movedToDoneAt Zeitpunkt des Zugs nach Done (nullable)
 * @param createdBy Ersteller (nullable, z. B. bei PAT)
 * @param createdAt Erstellzeitpunkt
 * @param updatedAt letzte Änderung
 * @param type CARD oder EPIC (gespeicherter Wert des Vorhabens, siehe {@link CardType})
 * @param parentId zugeordnetes Vorhaben (nullable; nur an CARD gesetzt)
 * @param shortcode Kürzel eines Vorhabens (nullable; nur an EPIC)
 * @param dueDate Fälligkeitsdatum (nullable; nur an CARD sinnvoll)
 * @param projectId zugehöriges Projekt (immer gesetzt)
 * @param externalKey idempotenz-Schlüssel eines Automatik-Ingests (nullable, projekt-eindeutig, z.
 *     B. {@code sonar:<issue-key>}; Issue #534) — verhindert Doppel-Anlage durch wiederholte Läufe,
 *     solange die Karte existiert (auch archiviert/Papierkorb); purge gibt ihn frei
 * @param derivedFromCardId Karte, aus der diese entstanden ist (nullable) — die Herkunft der
 *     Prozesskette fachliche Anforderung → Plandokument → Arbeitspaket. Gespeichert wird die
 *     <strong>ID</strong> und nicht die projektweite Nummer: Beim Verschieben in ein anderes
 *     Projekt vergibt {@code CardService.doTransfer} eine neue Nummer, eine gespeicherte Nummer
 *     zeigte danach im Projekt des Kindes auf eine fremde Karte. Die ID bleibt stabil.
 * @param requirementCardId fachliche Anforderung, aus der dieses Vorhaben eröffnet wurde (nullable;
 *     nur an EPIC gesetzt). <strong>Drei Relationen liegen hier nebeneinander und beantworten drei
 *     verschiedene Fragen</strong> — sie auseinanderzuhalten ist wesentlich:
 *     <ul>
 *       <li>{@code parentId} — „wozu gehört diese Karte": Zugehörigkeit, flach, eine Ebene, gesetzt
 *           an der CARD.
 *       <li>{@code derivedFromCardId} — „woraus ist sie entstanden": Abstammung, beliebig tief, an
 *           jeder Karte möglich.
 *       <li>{@code requirementCardId} — „welche Anforderung trägt dieses Vorhaben": genau eine, von
 *           Hand gelegt, nur am EPIC.
 *     </ul>
 *     Warum gespeichert statt gerechnet: Die Zugehörigkeit aller Karten wird abgeleitet, weil sie
 *     ableitbar ist. Welche der zugeordneten Karten die Anforderung ist, ist es nicht — manuelles
 *     Zuordnen bleibt möglich, ein Vorhaben kann also mehrere Wurzeln haben. Wie bei {@code
 *     derivedFromCardId} wird die <strong>ID</strong> gespeichert, nicht die Nummer.
 */
public record Card(
    @Nullable Long id,
    Long boardId,
    Long columnId,
    Integer number,
    String title,
    @Nullable String description,
    int positionInColumn,
    boolean archived,
    @Nullable Instant movedToDoneAt,
    @Nullable Long createdBy,
    Instant createdAt,
    Instant updatedAt,
    CardType type,
    @Nullable Long parentId,
    @Nullable String shortcode,
    @Nullable Instant dueDate,
    Long projectId,
    @Nullable String externalKey,
    @Nullable Long derivedFromCardId,
    @Nullable Long requirementCardId)
    implements Identifiable {

  public Card withContent(String newTitle, @Nullable String newDescription) {
    return new Card(
        id,
        boardId,
        columnId,
        number,
        newTitle,
        newDescription,
        positionInColumn,
        archived,
        movedToDoneAt,
        createdBy,
        createdAt,
        updatedAt,
        type,
        parentId,
        shortcode,
        dueDate,
        projectId,
        externalKey,
        derivedFromCardId,
        requirementCardId);
  }

  public Card asArchived() {
    return new Card(
        id,
        boardId,
        columnId,
        number,
        title,
        description,
        positionInColumn,
        true,
        movedToDoneAt,
        createdBy,
        createdAt,
        updatedAt,
        type,
        parentId,
        shortcode,
        dueDate,
        projectId,
        externalKey,
        derivedFromCardId,
        requirementCardId);
  }

  /** Wiederherstellen an einer freien Position (append), um Positionskollisionen zu vermeiden. */
  public Card asRestored(int newPositionInColumn) {
    return new Card(
        id,
        boardId,
        columnId,
        number,
        title,
        description,
        newPositionInColumn,
        false,
        movedToDoneAt,
        createdBy,
        createdAt,
        updatedAt,
        type,
        parentId,
        shortcode,
        dueDate,
        projectId,
        externalKey,
        derivedFromCardId,
        requirementCardId);
  }

  public Card withMovedToDoneAt(@Nullable Instant when) {
    return new Card(
        id,
        boardId,
        columnId,
        number,
        title,
        description,
        positionInColumn,
        archived,
        when,
        createdBy,
        createdAt,
        updatedAt,
        type,
        parentId,
        shortcode,
        dueDate,
        projectId,
        externalKey,
        derivedFromCardId,
        requirementCardId);
  }

  /** Setzt oder löscht ({@code null}) die Vorhaben-Zuordnung. */
  public Card withParent(@Nullable Long newParentId) {
    return new Card(
        id,
        boardId,
        columnId,
        number,
        title,
        description,
        positionInColumn,
        archived,
        movedToDoneAt,
        createdBy,
        createdAt,
        updatedAt,
        type,
        newParentId,
        shortcode,
        dueDate,
        projectId,
        externalKey,
        derivedFromCardId,
        requirementCardId);
  }

  /** Setzt das Kürzel (nur für Vorhaben sinnvoll). */
  public Card withShortcode(@Nullable String newShortcode) {
    return new Card(
        id,
        boardId,
        columnId,
        number,
        title,
        description,
        positionInColumn,
        archived,
        movedToDoneAt,
        createdBy,
        createdAt,
        updatedAt,
        type,
        parentId,
        newShortcode,
        dueDate,
        projectId,
        externalKey,
        derivedFromCardId,
        requirementCardId);
  }

  /** Setzt oder löscht ({@code null}) das Fälligkeitsdatum. */
  public Card withDueDate(@Nullable Instant newDueDate) {
    return new Card(
        id,
        boardId,
        columnId,
        number,
        title,
        description,
        positionInColumn,
        archived,
        movedToDoneAt,
        createdBy,
        createdAt,
        updatedAt,
        type,
        parentId,
        shortcode,
        newDueDate,
        projectId,
        externalKey,
        derivedFromCardId,
        requirementCardId);
  }

  /**
   * Setzt oder löscht ({@code null}) die Anforderungskarte des Vorhabens.
   *
   * <p>Gelöscht wird sie an zwei Stellen: auf ausdrückliche Übergabe von {@code null} — ein
   * Vorhaben ohne Anforderung ist ein gültiger Zustand — und beim Projektwechsel, in beide
   * Richtungen: am abgewanderten Vorhaben und an jedem Vorhaben, das auf die abgewanderte
   * Anforderung zeigt.
   */
  public Card withRequirement(@Nullable Long newRequirementCardId) {
    return new Card(
        id,
        boardId,
        columnId,
        number,
        title,
        description,
        positionInColumn,
        archived,
        movedToDoneAt,
        createdBy,
        createdAt,
        updatedAt,
        type,
        parentId,
        shortcode,
        dueDate,
        projectId,
        externalKey,
        derivedFromCardId,
        newRequirementCardId);
  }

  /**
   * Setzt oder löscht ({@code null}) die Herkunft.
   *
   * <p>Wird von {@code CardService.doTransfer} zum Aufräumen beim Projektwechsel gebraucht: Die
   * Herkunft ist projekt-lokal, und ein Verweis über die Projektgrenze zeigte auf eine Nummer, die
   * dort einer anderen Karte gehören kann.
   */
  public Card withDerivedFrom(@Nullable Long newDerivedFromCardId) {
    return new Card(
        id,
        boardId,
        columnId,
        number,
        title,
        description,
        positionInColumn,
        archived,
        movedToDoneAt,
        createdBy,
        createdAt,
        updatedAt,
        type,
        parentId,
        shortcode,
        dueDate,
        projectId,
        externalKey,
        newDerivedFromCardId,
        requirementCardId);
  }
}
