import { test } from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import { alsUmgebung, vapidSchluesselErzeugen } from './vapid-schluessel.mjs';

const SKRIPT = join(dirname(fileURLToPath(import.meta.url)), 'vapid-schluessel.mjs');
const BASE64URL = /^[A-Za-z0-9_-]+$/;

test('erzeugt einen unkomprimierten P-256-Punkt und einen Skalar von 32 Bytes', async () => {
  const { oeffentlich, privat } = await vapidSchluesselErzeugen();

  assert.match(oeffentlich, BASE64URL);
  assert.match(privat, BASE64URL);
  const punkt = Buffer.from(oeffentlich, 'base64url');
  assert.equal(punkt.length, 65);
  assert.equal(punkt[0], 0x04);
  assert.equal(Buffer.from(privat, 'base64url').length, 32);
});

test('beide Schluessel gehoeren zusammen: Signatur mit dem privaten, Pruefung mit dem oeffentlichen', async () => {
  const { oeffentlich, privat } = await vapidSchluesselErzeugen();
  const punkt = Buffer.from(oeffentlich, 'base64url');
  const jwk = {
    kty: 'EC',
    crv: 'P-256',
    x: punkt.subarray(1, 33).toString('base64url'),
    y: punkt.subarray(33).toString('base64url'),
    d: privat,
  };
  const algorithmus = { name: 'ECDSA', hash: 'SHA-256' };
  const privaterSchluessel = await crypto.subtle.importKey(
    'jwk',
    jwk,
    { name: 'ECDSA', namedCurve: 'P-256' },
    false,
    ['sign'],
  );
  const oeffentlicherSchluessel = await crypto.subtle.importKey(
    'raw',
    punkt,
    { name: 'ECDSA', namedCurve: 'P-256' },
    false,
    ['verify'],
  );
  const daten = new TextEncoder().encode('probe');

  const signatur = await crypto.subtle.sign(algorithmus, privaterSchluessel, daten);

  assert.equal(await crypto.subtle.verify(algorithmus, oeffentlicherSchluessel, signatur, daten), true);
});

test('zwei Aufrufe liefern verschiedene Paare', async () => {
  const erstes = await vapidSchluesselErzeugen();
  const zweites = await vapidSchluesselErzeugen();

  assert.notEqual(erstes.privat, zweites.privat);
  assert.notEqual(erstes.oeffentlich, zweites.oeffentlich);
});

test('alsUmgebung schreibt die beiden Zeilen fuer die .env', () => {
  assert.equal(
    alsUmgebung({ oeffentlich: 'BAbc', privat: 'xyz' }),
    'MANBAN_PUSH_VAPID_PUBLIC_KEY=BAbc\nMANBAN_PUSH_VAPID_PRIVATE_KEY=xyz\n',
  );
});

test('der direkte Aufruf gibt genau die beiden Zeilen aus', () => {
  const ausgabe = execFileSync(process.execPath, [SKRIPT], { encoding: 'utf8' });

  const zeilen = ausgabe.trimEnd().split('\n');
  assert.equal(zeilen.length, 2);
  assert.match(zeilen[0], /^MANBAN_PUSH_VAPID_PUBLIC_KEY=[A-Za-z0-9_-]{87}$/);
  assert.match(zeilen[1], /^MANBAN_PUSH_VAPID_PRIVATE_KEY=[A-Za-z0-9_-]{43}$/);
});
