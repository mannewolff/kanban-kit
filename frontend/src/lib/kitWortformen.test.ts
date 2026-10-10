import { describe, expect, it } from 'vitest'
import { PROTOKOLL, passe, type Wortform } from './kitWortformen'

/**
 * Je Regel des Abschnitts Protokoll eine deutsche und eine englische Beispielzeile, wie
 * `night.mjs` sie ohne Zeitstempel-Praefix schreibt (Issue #1592). Die englischen Zeilen
 * folgen den Formen aus Plan #1582, E1.
 */
const BEISPIELE: Record<keyof typeof PROTOKOLL, { de: string; en: string }> = {
  start: {
    de: 'Nacht-Runner startet (Modus Implementierung, max 5 Sessions)',
    en: 'Night runner starting (mode implementation, max 5 sessions)',
  },
  abschluss: {
    de: 'Nacht-Runner beendet: 1 erfolgreich, 0 zurueckgestellt.',
    en: 'Night runner finished: 1 succeeded, 0 deferred.',
  },
  stufe: { de: 'Lauf beendet (Stufe issue)', en: 'Run finished (stage issue)' },
  fehler: { de: 'Fehler: Working Tree ist nicht sauber.', en: 'Error: working tree is not clean.' },
  erfolg: {
    de: '  Erfolg nach 5 min, Commit a1b2c3d, Issue #100 in In review.',
    en: '  Success after 5 min, commit a1b2c3d, issue #100 in In review.',
  },
  salvageErfolg: {
    de: '  Salvage erfolgreich, Commit d4e5f6a, Issue #100 in In review.',
    en: '  Salvage succeeded, commit d4e5f6a, issue #100 in In review.',
  },
  fehlschlagDirty: {
    de: '  FEHLSCHLAG nach 5 min: Issue #100 nicht in In review UND Working Tree dirty — harter Stopp.',
    en: '  FAILURE after 5 min: issue #100 not in In review AND working tree dirty — hard stop.',
  },
  fehlschlagNichts: {
    de: '  Fehlschlag nach 5 min: Issue #100 — die Session hat nichts hinterlassen.',
    en: '  Failure after 5 min: issue #100 — the session left nothing behind.',
  },
  fehlschlagSauber: {
    de: '  Fehlschlag nach 5 min: Issue #100 nicht in In review, Tree sauber — weiter.',
    en: '  Failure after 5 min: issue #100 not in In review, tree clean — next.',
  },
  infrastruktur: {
    de: '  INFRASTRUKTUR-FEHLSCHLAG nach 5 min (Exit 1): harter Stopp, Issue #100 bleibt unangetastet.',
    en: '  INFRASTRUCTURE FAILURE after 5 min (exit 1): hard stop, issue #100 remains untouched.',
  },
  harterStopp: {
    de: '  HARTER STOPP: erfolgreiche Runde zu Issue #100 hat unkommittete Reste hinterlassen.',
    en: '  HARD STOP: successful round for issue #100 left uncommitted leftovers.',
  },
  salvageGescheitert: {
    de: '  SALVAGE-VERSUCH gescheitert — harter Stopp. Issue #100 weiterhin nicht in In review.',
    en: '  SALVAGE ATTEMPT failed — hard stop. Issue #100 still not in In review.',
  },
  geprueft: {
    de: '  Erfolg nach 5 min: Issue #100 geprueft ohne Befund, Marker gesetzt.',
    en: '  Success after 5 min: issue #100 checked without findings, marker set.',
  },
  syntheseOhneBeleg: {
    de: '  Nach 5 min: Issue #100 — Synthese ohne Beleg: kit:klaeren gesetzt.',
    en: '  After 5 min: issue #100 — Synthesis without evidence: kit:clarify set.',
  },
  schaerfungFehlt: {
    de: '  Nach 5 min: Issue #100 — Befunde vorhanden, aber kein Body-Vorschlag.',
    en: '  After 5 min: issue #100 — Findings present, but no body proposal.',
  },
  uebersprungenKlaeren: {
    de: '  #100 Paket 1 -> uebersprungen (kit:klaeren, offene Entscheidung)',
    en: '  #100 Package 1 -> skipped (kit:clarify, open decision)',
  },
  uebersprungenListe: {
    de: '  #100 Paket 1 -> uebersprungen (ungeprueft)',
    en: '  #100 Package 1 -> skipped (unchecked)',
  },
  ueberMax: {
    de: '  #100 Paket 1 -> ueber --max 5, bleibt liegen.',
    en: '  #100 Package 1 -> over --max 5, remains pending.',
  },
  zurueckgestellt: {
    de: '#100 zurueckgestellt: Abhaengigkeit #99 nicht erfuellt.',
    en: '#100 deferred: dependency #99 not met.',
  },
  uebersprungenGate: {
    de: '#100 uebersprungen: Marker vorhanden.',
    en: '#100 skipped: marker present.',
  },
  session: { de: 'Session 1/5: Issue #100 — Paket 1', en: 'session 1/5: issue #100 — Package 1' },
  pruefblock: { de: 'Pruefungen der Sessions:', en: 'Checks of the sessions:' },
  pruefGelaufen: {
    de: '  Issue #100: gelaufen: npm test -> gruen (Frontend) | ausgelassen: keine',
    en: '  Issue #100: ran: npm test -> green (Frontend) | skipped: none',
  },
  pruefUngeprueft: {
    de: '  Issue #100: ungeprueft — die Session hat keine Pruefung gefahren.',
    en: '  Issue #100: unchecked — the session ran no check.',
  },
  pruefLeer: {
    de: '  Issue #100: leeres Paket — nichts zu pruefen.',
    en: '  Issue #100: empty package — nothing to check.',
  },
  rot: { de: 'npm test -> rot (Frontend)', en: 'npm test -> red (Frontend)' },
  freigegeben: {
    de: '#100 bewusst ohne Pruefung freigegeben (Pruefung: Verzicht), wird implementiert.',
    en: '#100 deliberately released without checks (check: waived), will be implemented.',
  },
  vorflug: {
    de: '  Vorflug-Session startet (Modell claude-sonnet-5).',
    en: '  Preflight session starting (model claude-sonnet-5).',
  },
  reviewer: { de: '  Reviewer fable (claude) in runner: verfuegbar', en: '  Reviewer fable (claude) in runner: available' },
  keinReviewer: {
    de: '  Kein Reviewer konfiguriert: Pruefung entfaellt.',
    en: '  No reviewer configured: check omitted.',
  },
  tracker: { de: '  Tracker (runner): erreichbar', en: '  Tracker (runner): reachable' },
  formatFix: {
    de: '  buildChecks rot — einmaliger Format-Fix wird angewendet: npm run format',
    en: '  buildChecks red — one-time format fix being applied: npm run format',
  },
  formatFixAngewendet: {
    de: '  FORMAT-FIX angewendet, buildChecks jetzt gruen.',
    en: '  FORMAT FIX applied, buildChecks now green.',
  },
  salvageGestartet: {
    de: '  SALVAGE-VERSUCH gestartet (Checks extern verifiziert gruen): Issue #100.',
    en: '  SALVAGE ATTEMPT started (checks verified green externally): issue #100.',
  },
  salvageNichtMoeglich: { de: '  Salvage nicht moeglich: Checks rot.', en: '  Salvage not possible: checks red.' },
  hinweisZusammenfassung: {
    de: '  Hinweis: die vorherige Pruef-Zusammenfassung fehlt.',
    en: '  Note: the previous check summary is missing.',
  },
  cliMeldung: { de: '  CLI-Meldung: Rate limit erreicht.', en: '  CLI message: rate limit reached.' },
  vorhandeneLabels: {
    de: '  Im Backlog vorhandene Labels: Aktuell',
    en: '  Labels present in Backlog: current',
  },
  tippfehler: { de: '  Tippfehler im --review-label-Wert?', en: '  Typo in the --review-label value?' },
  warnung: { de: 'WARNUNG: Budget knapp.', en: 'WARNING: budget tight.' },
  readyLeer: { de: 'Ready ist leer — nichts zu tun.', en: 'Ready is empty — nothing to do.' },
  keineReviewKandidaten: { de: 'Keine Review-Kandidaten im Backlog.', en: 'No review candidates in Backlog.' },
  morgenRitual: {
    de: 'Morgen-Ritual: /review -> Test -> push main.',
    en: 'Morning ritual: /review -> test -> push main.',
  },
  keineRunde: {
    de: 'Pruefungen: keine Implementierungs-Runde gelaufen.',
    en: 'Checks: no implementation round ran.',
  },
  summe: { de: '  Summe: 12 Session(s) — 12 mit Pruefung.', en: '  Total: 12 session(s) — 12 with checks.' },
}

const REGELN = Object.entries(PROTOKOLL) as [keyof typeof PROTOKOLL, Wortform][]

describe('kitWortformen — Abschnitt Protokoll', () => {
  it('fuehrt zu jeder Regel ein Beispielpaar und kein Beispiel ohne Regel', () => {
    expect(Object.keys(BEISPIELE).sort()).toEqual(Object.keys(PROTOKOLL).sort())
  })

  it.each(REGELN)('%s trifft seine deutsche Beispielzeile', (name, form) => {
    expect(passe(form, BEISPIELE[name].de)).not.toBeNull()
  })

  it.each(REGELN)('%s trifft seine englische Beispielzeile', (name, form) => {
    expect(passe(form, BEISPIELE[name].en)).not.toBeNull()
  })

  it.each(REGELN)('%s trifft keine Zeile eines anderen Paars', (name, form) => {
    const fremde = REGELN.filter(([anderer]) => anderer !== name).flatMap(([anderer]) => [
      BEISPIELE[anderer].de,
      BEISPIELE[anderer].en,
    ])
    expect(fremde.filter((zeile) => passe(form, zeile) !== null)).toEqual([])
  })

  it.each(REGELN)('%s liefert in beiden Sprachen dieselben Gruppen', (name, form) => {
    const de = passe(form, BEISPIELE[name].de)
    const en = passe(form, BEISPIELE[name].en)
    expect(de?.length).toBe(en?.length)
  })

  it('nimmt die Entscheidungsfrage unter beiden Labelnamen, in beiden Sprachen', () => {
    for (const zeile of [
      '  #100 Paket 1 -> uebersprungen (kit:clarify, offene Entscheidung)',
      '  #100 Package 1 -> skipped (kit:klaeren, open decision)',
    ]) {
      expect(passe(PROTOKOLL.uebersprungenKlaeren, zeile)).not.toBeNull()
      expect(passe(PROTOKOLL.uebersprungenListe, zeile)).toBeNull()
    }
  })

  it('liefert die Gruppen der deutschen Form, wenn sie trifft', () => {
    expect(passe(PROTOKOLL.start, BEISPIELE.start.de)?.[1]).toBe('Implementierung')
    expect(passe(PROTOKOLL.start, BEISPIELE.start.en)?.[1]).toBe('implementation')
  })

  it('kennzeichnet den Probelauf in beiden Sprachen ueber die erste Gruppe des Abschlusses', () => {
    expect(passe(PROTOKOLL.abschluss, 'Dry-Run beendet: 2 Session(s) wuerden starten.')?.[1]).toBeDefined()
    expect(passe(PROTOKOLL.abschluss, 'Dry run finished: 2 session(s) would start.')?.[1]).toBeDefined()
    expect(passe(PROTOKOLL.abschluss, 'Nacht-Review beendet (Stufe issue)')?.[1]).toBeUndefined()
    expect(passe(PROTOKOLL.abschluss, 'Night review finished (stage issue)')?.[1]).toBeUndefined()
  })

  it('liefert null, wenn keine der beiden Formen trifft', () => {
    expect(passe(PROTOKOLL.warnung, 'Voellig unbekannte Zeile')).toBeNull()
  })
})
