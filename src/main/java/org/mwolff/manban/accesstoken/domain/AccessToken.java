package org.mwolff.manban.accesstoken.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.Identifiable;

/**
 * Persönliches API-Zugriffstoken (PAT). Persistiert wird nur {@code tokenHash}; der Klartext wird
 * bei der Erstellung genau einmal ausgegeben.
 *
 * <p>Optional an ein Projekt + Board gebunden ({@code projectId}/{@code boardId}): ein solches
 * Token adressiert genau dieses Board (Kanban-Compat-API, #45), ähnlich einem
 * GitHub-Fine-grained-PAT. Sind beide {@code null}, ist das Token ungebunden.
 *
 * <p>Bewusst <strong>ohne Wither</strong> für {@code lastUsedAt} und {@code revoked} (Issue #878):
 * Ein solcher Wither führt zwangsläufig zum vollständigen Zurückschreiben eines zuvor gelesenen
 * Zustands — und genau dabei ging ein zwischenzeitlicher Widerruf verloren. Nutzung und Widerruf
 * laufen deshalb über spaltenscharfe Updates am Port, nicht über neu gebaute Datensätze.
 *
 * @param id technische ID; {@code null} vor der Persistierung
 * @param userId Besitzer
 * @param projectId gebundenes Projekt; {@code null} = ungebunden
 * @param boardId gebundenes Board; {@code null} = ungebunden
 * @param name vom Nutzer vergebener Name
 * @param tokenHash SHA-256-Hash des Klartext-Tokens
 * @param displayName Anzeigename (z. B. als Autor PAT-erzeugter Kommentare)
 * @param createdAt Erstellzeitpunkt
 * @param lastUsedAt letzte Verwendung, <strong>minutengenau</strong> (Issue #997); {@code null}
 *     solange ungenutzt. Der Stempel wird gedrosselt geschrieben — höchstens einmal je Token und
 *     Minute —, weil der {@code UPDATE} sonst bei jedem API-Aufruf einen Zeilen-Lock auf genau der
 *     Zeile nähme, die sich die gleichzeitigen Befehle einer Person teilen. Ein Wert, der hinter
 *     dem letzten Aufruf zurückliegt, ist daher kein Fehler.
 * @param revoked ob das Token widerrufen wurde
 */
public record AccessToken(
    @Nullable Long id,
    Long userId,
    @Nullable Long projectId,
    @Nullable Long boardId,
    String name,
    String tokenHash,
    String displayName,
    Instant createdAt,
    @Nullable Instant lastUsedAt,
    boolean revoked)
    implements Identifiable {

  /** Ob das Token an ein Projekt + Board gebunden ist. */
  public boolean isBound() {
    return projectId != null && boardId != null;
  }
}
