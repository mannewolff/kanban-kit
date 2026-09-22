package org.mwolff.manban.accesstoken.application;

import java.time.Instant;

/**
 * Ausgehender Port für die Drosselung des Nutzungsstempels (Issue #997, Plan #995 E6).
 *
 * <p>Der Stempel {@code last_used_at} wurde zuvor bei <strong>jedem</strong> API-Aufruf
 * geschrieben. Der {@code UPDATE} nimmt einen Zeilen-Lock auf <em>einer</em> Token-Zeile — und
 * genau die gleichzeitigen Befehle einer Person teilen sich diese Zeile; sie konnten strukturell
 * nicht parallel laufen. Der Port trägt die Entscheidung „schreiben oder nicht", damit sie ohne
 * zusätzlichen Lesezugriff fällt.
 */
// PMD.ImplicitFunctionalInterface: bewusst KEIN @FunctionalInterface — das Interface ist ein
// ausgehender Port mit zustandsbehafteter Implementierung, kein Lambda-Ziel; eine zweite Methode
// (etwa zum Vergessen eines widerrufenen Tokens) darf ohne Bruch dazukommen.
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface LastUsedStampThrottle {

  /**
   * Fordert das Recht an, den Nutzungsstempel dieses Tokens jetzt zu schreiben.
   *
   * <p>Genau <strong>ein</strong> Aufrufer je Token und Minute bekommt {@code true} — auch bei
   * gleichzeitigen Aufrufen. Wer {@code false} erhält, schreibt nicht; „zuletzt benutzt" bleibt
   * damit minutengenau statt aufrufgenau.
   *
   * @param tokenId Token, dessen Stempel gemeint ist
   * @param now Jetzt-Zeitpunkt des Aufrufers (aus der injizierten Uhr)
   * @return {@code true}, wenn jetzt gestempelt werden soll
   */
  boolean claimStamp(long tokenId, Instant now);
}
