package org.mwolff.manban.auth.application;

import java.time.Instant;

/**
 * Ergebnis einer erfolgreichen Prüfung eines Session-Tokens (Plan #1801, A10).
 *
 * @param userId das Konto, das das Token ausweist
 * @param expiresAt der im Token festgehaltene Ablauf; aus ihm ergibt sich die Restlaufzeit, nach
 *     der der Web-Adapter die Sitzung gleitend verlängert
 */
public record SessionCheck(long userId, Instant expiresAt) {}
