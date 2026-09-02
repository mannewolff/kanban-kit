import { describe, expect, it } from 'vitest'
import { NIGHT_RUN_ERROR_CLASSES, parseNightRunLog } from './nightRunLog'

/**
 * Fixtures sind **anonymisiert** (Issue #720): Pfade als `<PFAD>`, keine Sitzungs-IDs,
 * Titel nach dem Schema `Paket N`. Zeilenmuster und Struktur bleiben erhalten — echte
 * Ausschnitte einzuchecken widerspraeche Entscheidung A1 des Plans #718 und landete
 * dauerhaft in der Historie eines Repos, das oeffentlich werden soll.
 */

/** Runner-Zeile mit Zeitstempel-Praefix, wie `log()` in `night.mjs` sie schreibt. */
const z = (minute: number, text: string) =>
  `[2026-09-01T22:${String(minute).padStart(2, '0')}:00.000Z] ${text}`

const START = (minute: number, modus = 'Implementierung') =>
  z(minute, `Nacht-Runner startet (Modus ${modus}, max 5 Sessions, Modell claude-opus-5, Label none)`)

const ENDE = (minute: number) =>
  z(minute, 'Nacht-Runner beendet: 1 erfolgreich, 0 zurueckgestellt, 1 Session(s) gestartet.')

describe('NIGHT_RUN_ERROR_CLASSES', () => {
  it('ist zur Laufzeit ein Array mit genau sieben Eintraegen', () => {
    expect(Array.isArray(NIGHT_RUN_ERROR_CLASSES)).toBe(true)
    expect(NIGHT_RUN_ERROR_CLASSES).toHaveLength(7)
  })

  it('nennt die sieben Klassen aus Plan #718', () => {
    expect([...NIGHT_RUN_ERROR_CLASSES]).toEqual([
      'CHECKS_RED',
      'CHECKS_NOT_STARTED',
      'DEPENDENCY_UNMET',
      'UNEXPECTED_STATE',
      'HARD_ABORT',
      'AWAITING_DECISION',
      'REVIEWER_FAILED',
    ])
  })
})

describe('parseNightRunLog — Zerlegung in Laeufe', () => {
  it('liefert bei leerem Text keine Laeufe', () => {
    expect(parseNightRunLog('')).toEqual({ runs: [], dryRunCount: 0 })
  })

  it('ignoriert Text vor der ersten Startzeile', () => {
    const text = ['irgendwas', '{"type":"assistant"}', START(0), ENDE(5)].join('\n')
    const { runs } = parseNightRunLog(text)
    expect(runs).toHaveLength(1)
    expect(runs[0].startedAt).toBe('2026-09-01T22:00:00.000Z')
  })

  it('ignoriert auch eine Runner-Zeile mit Praefix vor dem ersten Start', () => {
    // Sie gehoert zu keinem Lauf — sie darf weder einen eroeffnen noch als ungedeutet
    // in einem spaeteren Lauf auftauchen.
    const text = [z(0, 'Voellig unbekannte Zeile vor dem Start'), START(1), ENDE(5)].join('\n')
    const { runs } = parseNightRunLog(text)
    expect(runs).toHaveLength(1)
    expect(runs[0].unparsedCount).toBe(0)
  })

  it('trennt mehrere Laeufe einer Datei am jeweiligen Start', () => {
    const text = [START(0), ENDE(5), START(10), ENDE(20)].join('\n')
    const { runs } = parseNightRunLog(text)
    expect(runs.map((r) => r.startedAt)).toEqual([
      '2026-09-01T22:00:00.000Z',
      '2026-09-01T22:10:00.000Z',
    ])
  })

  it('rechnet die Laufdauer aus Start- und Abschlusszeitstempel', () => {
    const { runs } = parseNightRunLog([START(0), ENDE(5)].join('\n'))
    expect(runs[0].durationMs).toBe(5 * 60_000)
    expect(runs[0].incomplete).toBe(false)
  })

  it('erkennt einen Start nur am Zeilenanfang, nicht als Teilzeichenkette', () => {
    // Ein Sitzungsecho koennte den Text tragen — es darf keinen Lauf eroeffnen.
    const text = [START(0), z(1, '  #10 > Bash: Nacht-Runner startet steht hier nur als Text'), ENDE(5)].join('\n')
    expect(parseNightRunLog(text).runs).toHaveLength(1)
  })
})

describe('parseNightRunLog — Probelaeufe und Laeufe ohne Session', () => {
  it('verwirft einen Probelauf und zaehlt ihn', () => {
    const text = [
      START(0, 'Implementierung'),
      z(1, 'Dry-Run beendet: 2 Session(s) wuerden starten.'),
      START(10),
      ENDE(20),
    ].join('\n')
    const { runs, dryRunCount } = parseNightRunLog(text)
    expect(dryRunCount).toBe(1)
    expect(runs).toHaveLength(1)
    expect(runs[0].startedAt).toBe('2026-09-01T22:10:00.000Z')
  })

  it('bewahrt einen echten Lauf ohne Session bei leerem Ready', () => {
    const text = [START(0), z(1, 'Ready ist leer — nichts zu tun.'), ENDE(2)].join('\n')
    const { runs, dryRunCount } = parseNightRunLog(text)
    expect(dryRunCount).toBe(0)
    expect(runs).toHaveLength(1)
    expect(runs[0].items).toHaveLength(0)
  })

  it('bewahrt einen echten Lauf nach einem Label-Tippfehler', () => {
    const text = [
      START(0),
      z(1, "WARNUNG: kein Ready-Issue traegt das Label 'no' — es wird nichts verarbeitet."),
      ENDE(2),
    ].join('\n')
    const { runs } = parseNightRunLog(text)
    expect(runs).toHaveLength(1)
    expect(runs[0].items).toHaveLength(0)
  })
})

describe('parseNightRunLog — unvollstaendige Laeufe und harter Abbruch', () => {
  it('markiert einen Lauf ohne Abschlusszeile als incomplete', () => {
    const text = [START(0), z(1, 'Session 1/5: Issue #100 — Paket 1')].join('\n')
    const { runs } = parseNightRunLog(text)
    expect(runs[0].incomplete).toBe(true)
  })

  it('macht ein angefangenes Arbeitspaket ohne Ausgang rot mit HARD_ABORT', () => {
    const text = [START(0), z(1, 'Session 1/5: Issue #100 — Paket 1')].join('\n')
    const { runs } = parseNightRunLog(text)
    expect(runs[0].items).toHaveLength(1)
    expect(runs[0].items[0]).toMatchObject({ cardNumber: 100, state: 'RED', errorClass: 'HARD_ABORT' })
  })

  it('deutet eine praefixlose Fehler-Zeile als harten Abbruch auf Lauf-Ebene, ohne sie als ungedeutet zu zaehlen', () => {
    const text = [START(0), 'Fehler: Working Tree ist nicht sauber.'].join('\n')
    const { runs } = parseNightRunLog(text)
    expect(runs).toHaveLength(1)
    expect(runs[0].unparsedCount).toBe(0)
    expect(runs[0].items).toHaveLength(0)
    expect(runs[0].runState).toBe('RED')
    expect(runs[0].runErrorClass).toBe('HARD_ABORT')
    expect(runs[0].runExcerpt).toBe('Fehler: Working Tree ist nicht sauber.')
  })
})

describe('parseNightRunLog — Zustandszuordnung je Arbeitspaket', () => {
  const mitPruefung = (pruefzeile: string) =>
    [
      START(0),
      z(1, 'Session 1/5: Issue #100 — Paket 1'),
      z(8, '  Erfolg nach 7 min, Commit a1b2c3d, Issue #100 in In review.'),
      z(9, 'Pruefungen der Sessions:'),
      z(9, pruefzeile),
      z(9, '  Summe: 1 Session(s) — 1 mit Pruefung, 0 ohne Aenderung, 0 ungeprueft; 1 Pruefung(en) gelaufen (davon 0 rot), 0 ausgelassen.'),
      ENDE(10),
    ].join('\n')

  it('macht Erfolg mit gruenen Pruefungen gruen', () => {
    const { runs } = parseNightRunLog(
      mitPruefung('  Issue #100: gelaufen: mvn verify -> gruen (Backend) | ausgelassen: keine'),
    )
    expect(runs[0].items[0]).toMatchObject({ state: 'GREEN', commit: 'a1b2c3d', durationMs: 7 * 60_000 })
    expect(runs[0].items[0].errorClass).toBeUndefined()
  })

  it('macht Erfolg mit roter Pruefung gelb, nicht gruen', () => {
    const { runs } = parseNightRunLog(
      mitPruefung('  Issue #100: gelaufen: mvn verify -> rot (Backend) | ausgelassen: keine'),
    )
    expect(runs[0].items[0]).toMatchObject({ state: 'YELLOW', errorClass: 'CHECKS_RED' })
  })

  it('macht Erfolg ohne gefahrene Pruefung gelb mit CHECKS_NOT_STARTED', () => {
    const { runs } = parseNightRunLog(
      mitPruefung('  Issue #100: ungeprueft — die Session hat keine Pruefung gefahren.'),
    )
    expect(runs[0].items[0]).toMatchObject({ state: 'YELLOW', errorClass: 'CHECKS_NOT_STARTED' })
  })

  it('behandelt ein leeres Paket als geprueft und damit gruen', () => {
    const { runs } = parseNightRunLog(
      mitPruefung('  Issue #100: leeres Paket — keine Pruefung, weil nichts veraendert wurde.'),
    )
    expect(runs[0].items[0].state).toBe('GREEN')
  })

  it('macht einen Fehlschlag bei sauberem Tree rot', () => {
    const text = [
      START(0),
      z(1, 'Session 1/5: Issue #100 — Paket 1'),
      z(6, '  Fehlschlag nach 5 min: Issue #100 nicht in In review, Tree sauber — Issue ins Backlog, weiter.'),
      ENDE(7),
    ].join('\n')
    const { runs } = parseNightRunLog(text)
    expect(runs[0].items[0]).toMatchObject({ state: 'RED', errorClass: 'UNEXPECTED_STATE' })
  })

  it('macht einen Infrastruktur-Fehlschlag rot mit HARD_ABORT', () => {
    const text = [
      START(0),
      z(1, 'Session 1/5: Issue #100 — Paket 1'),
      z(3, '  INFRASTRUKTUR-FEHLSCHLAG nach 2 min (Exit 1): Session-Start gescheitert — harter Stopp, Issue #100 bleibt unangetastet.'),
      ENDE(4),
    ].join('\n')
    expect(parseNightRunLog(text).runs[0].items[0]).toMatchObject({ state: 'RED', errorClass: 'HARD_ABORT' })
  })

  it('macht einen Fehlschlag mit dirtyem Tree rot mit HARD_ABORT', () => {
    const text = [
      START(0),
      z(1, 'Session 1/5: Issue #100 — Paket 1'),
      z(6, '  FEHLSCHLAG nach 5 min: Issue #100 nicht in In review UND Working Tree dirty — harter Stopp.'),
    ].join('\n')
    expect(parseNightRunLog(text).runs[0].items[0]).toMatchObject({
      state: 'RED',
      errorClass: 'HARD_ABORT',
      durationMs: 5 * 60_000,
    })
  })

  it('macht unkommittete Reste nach einer erfolgreichen Runde rot mit HARD_ABORT', () => {
    const text = [
      START(0),
      z(1, 'Session 1/5: Issue #100 — Paket 1'),
      z(6, '  HARTER STOPP: erfolgreiche Runde zu Issue #100 hat unkommittete Reste hinterlassen — bitte morgens sichten und aufraeumen.'),
    ].join('\n')
    expect(parseNightRunLog(text).runs[0].items[0]).toMatchObject({ state: 'RED', errorClass: 'HARD_ABORT' })
  })

  it('macht einen gescheiterten Salvage-Versuch rot mit HARD_ABORT', () => {
    const text = [
      START(0),
      z(1, 'Session 1/5: Issue #100 — Paket 1'),
      z(6, '  SALVAGE-VERSUCH gescheitert — harter Stopp. Issue #100 weiterhin nicht in In review.'),
    ].join('\n')
    expect(parseNightRunLog(text).runs[0].items[0]).toMatchObject({ state: 'RED', errorClass: 'HARD_ABORT' })
  })

  it('macht ein uebersprungenes Arbeitspaket grau und haelt den Grund fest', () => {
    const text = [
      START(0),
      z(1, '  #100 Paket 1 -> uebersprungen (ungeprueft (kein Issue-Review-Marker im Body))'),
      ENDE(2),
    ].join('\n')
    const item = parseNightRunLog(text).runs[0].items[0]
    expect(item.state).toBe('GREY')
    expect(item.excerpt).toContain('ungeprueft (kein Issue-Review-Marker im Body)')
  })

  it('macht ein wegen unerfuellter Abhaengigkeit zurueckgestelltes Paket grau mit DEPENDENCY_UNMET', () => {
    const text = [
      START(0),
      z(1, '#100 zurueckgestellt: Abhaengigkeit #99 nicht erfuellt.'),
      ENDE(2),
    ].join('\n')
    expect(parseNightRunLog(text).runs[0].items[0]).toMatchObject({
      state: 'GREY',
      errorClass: 'DEPENDENCY_UNMET',
    })
  })

  it('macht ein Paket mit kit:klaeren rot mit AWAITING_DECISION', () => {
    const text = [
      START(0),
      z(1, '  #100 Paket 1 -> uebersprungen (kit:klaeren, offene Entscheidung)'),
      ENDE(2),
    ].join('\n')
    expect(parseNightRunLog(text).runs[0].items[0]).toMatchObject({
      state: 'RED',
      errorClass: 'AWAITING_DECISION',
    })
  })
})

describe('parseNightRunLog — Pruef-Lauf', () => {
  const review = (ausgang: string) =>
    [
      START(0, 'Review'),
      z(1, 'Review-Session 1/5: Issue #200 — Paket 2'),
      z(4, ausgang),
      z(5, 'Nacht-Review beendet (Stufe issue): 1 ohne Befund, 0 mit Befund, 0 Schaerfung fehlt.'),
    ].join('\n')

  it('erkennt den Modus und die Stufe', () => {
    const { runs } = parseNightRunLog(review('  Erfolg nach 3 min: Issue #200 geprueft ohne Befund, Marker gesetzt.'))
    expect(runs[0].mode).toBe('REVIEW')
    expect(runs[0].stage).toBe('issue')
  })

  it('macht "geprueft ohne Befund" gruen', () => {
    const { runs } = parseNightRunLog(review('  Erfolg nach 3 min: Issue #200 geprueft ohne Befund, Marker gesetzt.'))
    expect(runs[0].items[0].state).toBe('GREEN')
  })

  it('macht "geprueft mit Befund" gruen', () => {
    const { runs } = parseNightRunLog(
      review('  Erfolg nach 3 min: Issue #200 geprueft mit Befund — kein Marker, wartet auf dich.'),
    )
    expect(runs[0].items[0].state).toBe('GREEN')
  })

  it('macht "Schaerfung fehlt" gelb', () => {
    const { runs } = parseNightRunLog(
      review('  Nach 3 min: Issue #200 — Befunde vorhanden, aber kein Body-Vorschlag — Schaerfung fehlt.'),
    )
    expect(runs[0].items[0].state).toBe('YELLOW')
  })

  it('macht "die Session hat nichts hinterlassen" rot mit CHECKS_NOT_STARTED', () => {
    const { runs } = parseNightRunLog(
      review('  Fehlschlag nach 3 min: Issue #200 — die Session hat nichts hinterlassen, weiter mit dem naechsten.'),
    )
    expect(runs[0].items[0]).toMatchObject({ state: 'RED', errorClass: 'CHECKS_NOT_STARTED' })
  })

  it('macht einen Infrastruktur-Fehlschlag im Pruef-Lauf rot mit HARD_ABORT', () => {
    const { runs } = parseNightRunLog(
      review('  INFRASTRUKTUR-FEHLSCHLAG nach 3 min (Exit 1): Session-Start gescheitert — harter Stopp, Issue #200 bleibt unangetastet.'),
    )
    expect(runs[0].items[0]).toMatchObject({ state: 'RED', errorClass: 'HARD_ABORT' })
  })

  it('macht ein Paket mit bereits gesetztem Marker grau', () => {
    const text = [
      START(0, 'Review'),
      z(1, '#200 uebersprungen: traegt bereits einen Issue-Review-Marker.'),
      z(2, 'Nacht-Review beendet (Stufe issue): 0 ohne Befund, 0 mit Befund, 0 Schaerfung fehlt.'),
    ].join('\n')
    expect(parseNightRunLog(text).runs[0].items[0].state).toBe('GREY')
  })
})

describe('parseNightRunLog — was als ungedeutet zaehlt', () => {
  it('zaehlt Sitzungsstrom-Zeilen ohne Praefix nicht', () => {
    const text = [
      START(0),
      '{"type":"assistant","message":{"role":"assistant"}}',
      '{"type":"user"}',
      ENDE(2),
    ].join('\n')
    expect(parseNightRunLog(text).runs[0].unparsedCount).toBe(0)
  })

  it('zaehlt das Sitzungsecho nicht', () => {
    const text = [START(0), z(1, '  #100 > Bash: npm test'), ENDE(2)].join('\n')
    expect(parseNightRunLog(text).runs[0].unparsedCount).toBe(0)
  })

  it('zaehlt eine Runner-Zeile mit Praefix ohne passendes Muster und liefert einen Auszug', () => {
    const text = [START(0), z(1, 'Voellig unbekannte Runner-Zeile aus einer aelteren Fassung'), ENDE(2)].join('\n')
    const run = parseNightRunLog(text).runs[0]
    expect(run.unparsedCount).toBe(1)
    expect(run.unparsedSample).toEqual(['Voellig unbekannte Runner-Zeile aus einer aelteren Fassung'])
  })

  it('nimmt hoechstens fuenf Zeilen in den Auszug auf, zaehlt aber alle', () => {
    const zeilen = Array.from({ length: 8 }, (_, i) => z(i + 1, `Unbekannte Zeile ${i}`))
    const { runs } = parseNightRunLog([START(0), ...zeilen, ENDE(20)].join('\n'))
    expect(runs[0].unparsedCount).toBe(8)
    expect(runs[0].unparsedSample).toHaveLength(5)
  })
})

describe('parseNightRunLog — Zaehlungen', () => {
  it('zaehlt bearbeitete und uebergangene Arbeitspakete getrennt', () => {
    const text = [
      START(0),
      z(1, 'Session 1/5: Issue #100 — Paket 1'),
      z(6, '  Erfolg nach 5 min, Commit a1b2c3d, Issue #100 in In review.'),
      z(7, '  #101 Paket 2 -> uebersprungen (ungeprueft (kein Issue-Review-Marker im Body))'),
      ENDE(8),
    ].join('\n')
    const run = parseNightRunLog(text).runs[0]
    expect(run.processedCount).toBe(1)
    expect(run.skippedCount).toBe(1)
    expect(run.items.map((i) => i.position)).toEqual([0, 1])
  })
})

describe('parseNightRunLog — Fixture "vollstaendiger Lauf"', () => {
  /**
   * Je eine anonymisierte Zeile pro `log(`-Aufruf der heutigen `night.mjs`. Daran wird
   * die Vollzaehligkeit der Musterliste zum Testergebnis statt zur Absichtserklaerung
   * (Issue #720).
   */
  const vollstaendig = [
    START(0),
    z(1, '  Vorflug-Session startet (Modell claude-sonnet-5, Tracker-Probe an).'),
    z(2, '  Reviewer fable (claude) in runner: verfuegbar'),
    z(2, '  Tracker (runner): erreichbar'),
    z(3, 'Session 1/5: Issue #100 — Paket 1'),
    z(3, '  #100 > Bash: npm test'),
    z(4, '  buildChecks rot — einmaliger Format-Fix wird angewendet: npm run format'),
    z(4, '  FORMAT-FIX angewendet, buildChecks jetzt gruen — der Lauf geht weiter.'),
    z(9, '  Erfolg nach 5 min, Commit a1b2c3d, Issue #100 in In review.'),
    z(10, 'Session 2/5: Issue #101 — Paket 2'),
    z(12, '  Fehlschlag nach 2 min: Issue #101 nicht in In review, Tree sauber — Issue ins Backlog, weiter.'),
    z(13, '  #102 Paket 3 -> uebersprungen (ungeprueft (kein Issue-Review-Marker im Body))'),
    z(13, '#103 zurueckgestellt: Abhaengigkeit #99 nicht erfuellt.'),
    z(14, '#104 bewusst ohne Pruefung freigegeben (Pruefung: Verzicht), wird implementiert.'),
    z(14, '  #105 Paket 5 -> ueber --max 5, bleibt liegen.'),
    z(15, '  SALVAGE-VERSUCH gestartet (Checks extern verifiziert gruen): Issue #106 — Zwischenstand wird gegen das Issue geprueft.'),
    z(16, '  Salvage erfolgreich, Commit d4e5f6a, Issue #106 in In review.'),
    z(17, 'Pruefungen der Sessions:'),
    z(17, '  Issue #100: gelaufen: npm test -> gruen (Frontend) | ausgelassen: keine'),
    z(17, '  Issue #101: ungeprueft — die Session hat keine Pruefung gefahren.'),
    z(17, '  Summe: 2 Session(s) — 1 mit Pruefung, 0 ohne Aenderung, 1 ungeprueft; 1 Pruefung(en) gelaufen (davon 0 rot), 0 ausgelassen.'),
    z(18, 'Morgen-Ritual: /review -> Test -> push main. Protokoll: <PFAD>'),
    ENDE(19),
  ].join('\n')

  it('deutet jede Runner-Zeile — unparsedCount ist 0', () => {
    expect(parseNightRunLog(vollstaendig).runs[0].unparsedCount).toBe(0)
  })

  it('findet alle Arbeitspakete des Laufs', () => {
    const run = parseNightRunLog(vollstaendig).runs[0]
    expect(run.items.map((i) => i.cardNumber)).toEqual([100, 101, 102, 103, 105, 106])
  })
})
