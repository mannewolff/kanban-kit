package org.mwolff.manban.auth.application;

import java.time.Instant;

/**
 * Ende der Sitzung, mit der eine Anfrage angemeldet ist (Issue #1997, Plan #1989 E14). Der Web-
 * Adapter legt es als Details an jede gültige Anmeldung per Session-Cookie: nach einer Erneuerung
 * das Ende des neuen Tokens, sonst das im Token festgehaltene.
 *
 * <p>Die Sitzung ist zustandslos — ihr Ende kennt nur der Filter, der das Token prüft. Wer etwas so
 * lange gelten lassen will wie die Sitzung auf diesem Gerät (das Push-Abo), liest es hier ab.
 *
 * @param expiresAt Zeitpunkt, zu dem die Sitzung abläuft
 */
public record SitzungsEnde(Instant expiresAt) {}
