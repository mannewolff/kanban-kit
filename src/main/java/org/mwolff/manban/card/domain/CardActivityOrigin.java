package org.mwolff.manban.card.domain;

/**
 * Server-verifizierte Herkunft eines Aktivitätseintrags: interaktive Browser-Session oder
 * Access-Token (PAT). Alt-Einträge vor Einführung dieser Unterscheidung tragen keine Herkunft
 * ({@code null} am {@link CardActivity}-Eintrag) — für die Vergangenheit ist sie nicht
 * rekonstruierbar (Issue #494).
 */
public enum CardActivityOrigin {
  SESSION,
  TOKEN
}
