#!/usr/bin/env node
/**
 * vapid-schluessel.mjs — erzeugt ein VAPID-Schlüsselpaar (P-256) für Web Push (Issue #1996) und
 * gibt beide Schlüssel als base64url aus, fertig zum Eintragen in die `.env`:
 *
 *   MANBAN_PUSH_VAPID_PUBLIC_KEY=<unkomprimierter Punkt, 65 Bytes>
 *   MANBAN_PUSH_VAPID_PRIVATE_KEY=<Skalar, 32 Bytes>
 *
 * Erzeugt wird mit WebCrypto aus Node selbst, ohne Paket. Der private Schlüssel geht nur auf die
 * Standardausgabe — nicht in eine Datei, nicht ins Log. Ein neues Paar macht alle bestehenden
 * Push-Abos ungültig: Die Browser haben sich mit dem alten öffentlichen Schlüssel angemeldet.
 *
 * Nutzung:
 *   node scripts/vapid-schluessel.mjs
 */
import { realpathSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

/** base64url ohne Auffüllzeichen, wie RFC 8292 und die Anwendung es lesen. */
function base64url(bytes) {
  return Buffer.from(bytes).toString('base64url');
}

/**
 * Erzeugt ein Paar. `oeffentlich` ist der unkomprimierte Punkt (`0x04 || x || y`), `privat` der
 * Skalar `d` — dieselbe Form, die `VapidSchluessel` in der Anwendung liest.
 */
export async function vapidSchluesselErzeugen() {
  const paar = await crypto.subtle.generateKey({ name: 'ECDSA', namedCurve: 'P-256' }, true, [
    'sign',
    'verify',
  ]);
  const punkt = new Uint8Array(await crypto.subtle.exportKey('raw', paar.publicKey));
  const jwk = await crypto.subtle.exportKey('jwk', paar.privateKey);
  return { oeffentlich: base64url(punkt), privat: jwk.d };
}

/** Die beiden Zeilen für die `.env`. */
export function alsUmgebung({ oeffentlich, privat }) {
  return `MANBAN_PUSH_VAPID_PUBLIC_KEY=${oeffentlich}\nMANBAN_PUSH_VAPID_PRIVATE_KEY=${privat}\n`;
}

// Nur beim direkten Aufruf laufen lassen: Der Test importiert dieses Modul.
const direkt =
  process.argv[1] && realpathSync(process.argv[1]) === realpathSync(fileURLToPath(import.meta.url));
if (direkt) {
  process.stdout.write(alsUmgebung(await vapidSchluesselErzeugen()));
}
