package org.mwolff.manban.fremdboard.application;

import java.util.Optional;

/**
 * Port der Erlaubnisliste für Adressen eines fremden Boards (Issue #2166, Plan #2152 A14),
 * umgesetzt in {@code fremdboard.infrastructure}: nur {@code https} und nur Hosts aus {@code
 * MANBAN_FREMDBOARD_HOSTS}.
 */
@FunctionalInterface
public interface AdressPruefung {

  /** Der Grund, aus dem die Adresse nicht erlaubt ist; leer, wenn sie erlaubt ist. */
  Optional<String> ablehnung(String adresse);
}
