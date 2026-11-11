package org.mwolff.manban.card.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.card.application.ActorContext.ActorStamp;

/**
 * Die vier Herkunftsspalten, die {@code card_activity} und {@code card_label_event} gleichlautend
 * tragen (Issue #2141).
 *
 * <p>{@code origin} und {@code token_name} sind server-verifiziert (Issue #517), {@code agent} ist
 * eine Selbstauskunft des Clients, {@code run_started_at} die Laufkennung des Nachtlaufs (Issue
 * #1426), ebenfalls Selbstauskunft. Alle vier sind nullable: Alt-Einträge und Aufrufe ohne
 * authentifizierten Kontext tragen keine Herkunft.
 *
 * <p>Ein Embeddable statt einer gemeinsamen Oberklasse, wie {@code VerbrauchEmbeddable} im Modul
 * {@code nightrun}: Tabellen und Spaltennamen bleiben unverändert. Sind alle vier Spalten {@code
 * NULL}, setzt Hibernate das eingebettete Feld auf {@code null}; die Entities fangen das in ihrem
 * Getter ab.
 */
@Embeddable
class HerkunftEmbeddable {

  @Column(name = "origin")
  private @Nullable String origin;

  @Column(name = "token_name")
  private @Nullable String tokenName;

  @Column(name = "agent")
  private @Nullable String agent;

  @Column(name = "run_started_at")
  private @Nullable Instant runStartedAt;

  protected HerkunftEmbeddable() {
    // für JPA und als leere Herkunft
  }

  HerkunftEmbeddable(ActorStamp stamp) {
    this.origin = stamp.originName();
    this.tokenName = stamp.tokenName();
    this.agent = stamp.agent();
    this.runStartedAt = stamp.laufStart();
  }

  @Nullable String getOrigin() {
    return origin;
  }

  @Nullable String getTokenName() {
    return tokenName;
  }

  @Nullable String getAgent() {
    return agent;
  }

  @Nullable Instant getRunStartedAt() {
    return runStartedAt;
  }
}
