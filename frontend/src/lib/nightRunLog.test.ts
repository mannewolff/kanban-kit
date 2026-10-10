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
  it('ist zur Laufzeit ein Array mit genau neun Eintraegen', () => {
    expect(Array.isArray(NIGHT_RUN_ERROR_CLASSES)).toBe(true)
    expect(NIGHT_RUN_ERROR_CLASSES).toHaveLength(9)
  })

  it('nennt die sieben Klassen aus Plan #718, die achte aus #842 und die neunte aus #1546', () => {
    expect([...NIGHT_RUN_ERROR_CLASSES]).toEqual([
      'CHECKS_RED',
      'CHECKS_NOT_STARTED',
      'DEPENDENCY_UNMET',
      'UNEXPECTED_STATE',
      'HARD_ABORT',
      'AWAITING_DECISION',
      'REVIEWER_FAILED',
      'TIME_BUDGET_EXCEEDED',
      'STUCK',
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
    const text = [START(0), z(1, '  #10 > Bash: echo Nacht-Runner startet (Modus Implementierung)'), ENDE(5)].join('\n')
    expect(parseNightRunLog(text).runs).toHaveLength(1)
  })

  it('erkennt einen Abschluss nur am Zeilenanfang, nicht als Teilzeichenkette', () => {
    const text = [START(0), z(1, '  #10 > Bash: grep "Nacht-Runner beendet" protokoll.log')].join('\n')
    expect(parseNightRunLog(text).runs[0].incomplete).toBe(true)
  })

  it('erkennt ein Praefix nur am Zeilenanfang — ein Zeitstempel im Sitzungsstrom eroeffnet keine Runner-Zeile', () => {
    const text = [START(0), '{"text":"[2026-09-01T22:03:00.000Z] irgendwas"}', ENDE(5)].join('\n')
    expect(parseNightRunLog(text).runs[0].unparsedCount).toBe(0)
  })

  it('deutet den Modus Implementierung als Umsetzungs-Lauf', () => {
    expect(parseNightRunLog([START(0), ENDE(5)].join('\n')).runs[0].mode).toBe('IMPLEMENTATION')
  })

  it('erkennt den Pruef-Lauf am Anfang des Modus, auch mit Zusatz', () => {
    expect(parseNightRunLog([START(0, 'ReviewPlan'), ENDE(5)].join('\n')).runs[0].mode).toBe('REVIEW')
  })

  it('fuehrt am Umsetzungs-Lauf ohne Abbruch weder Stufe noch Lauf-Zustand', () => {
    const run = parseNightRunLog([START(0), ENDE(5)].join('\n')).runs[0]
    expect(Object.keys(run).sort()).toEqual([
      'durationMs',
      'incomplete',
      'items',
      'mode',
      'processedCount',
      'skippedCount',
      'startedAt',
      'unparsedCount',
      'unparsedSample',
    ])
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
    expect(runs[0].incomplete).toBe(false)
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

  it('macht "Synthese ohne Beleg" rot mit AWAITING_DECISION', () => {
    // Das Kit setzt in diesem Fall `kit:klaeren` — eine offene Entscheidung wartet auf
    // einen Menschen, und der Ergebnisstand-Parser benennt sie genauso (Issue #816).
    const { runs } = parseNightRunLog(
      review(
        '  Nach 3.2 min: Issue #200 — Synthese ohne Beleg: 2 als uebernommen bezeichnete Funde stehen nicht im Body-Vorschlag.',
      ),
    )
    expect(runs[0].items[0]).toMatchObject({
      cardNumber: 200,
      state: 'RED',
      errorClass: 'AWAITING_DECISION',
      durationMs: 192_000,
    })
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

/**
 * Diese vier Faelle stammen aus der manuellen Pruefung des Issues: zehn echte Protokolle
 * (August bis September, sieben Runner-Fassungen, rund 30 MB) durch den Parser gegeben.
 * Alle vier waren mit den urspruenglichen Fixtures unsichtbar — sie pruefen die Musterliste
 * gegen die Wirklichkeit statt gegen sich selbst.
 */
describe('parseNightRunLog — an echten Protokollen gefundene Muster', () => {
  it('versteht Minutenangaben mit Nachkommastelle', () => {
    // Der Runner schreibt `nach 8.8 min`, nicht `nach 8 min`. Ohne diesen Fall blieb
    // jedes erfolgreiche Arbeitspaket im Eroeffnungszustand — 19 gruene Pakete eines
    // echten Laufs erschienen als RED/HARD_ABORT.
    const text = [
      START(0),
      z(1, 'Session 1/5: Issue #100 — Paket 1'),
      z(10, '  Erfolg nach 8.8 min, Commit a1b2c3d, Issue #100 in In review.'),
      ENDE(11),
    ].join('\n')
    const run = parseNightRunLog(text).runs[0]
    expect(run.unparsedCount).toBe(0)
    expect(run.items[0]).toMatchObject({ state: 'GREEN', durationMs: 528_000 })
  })

  it('deutet jede Gate-Ablehnung, nicht nur den Review-Marker-Fall', () => {
    const text = [
      START(0),
      z(1, '#411 uebersprungen: Idee ([Idee]), wird nicht implementiert.'),
      z(1, '#431 uebersprungen: fachliches Issue ([Fachlich]), wird nicht implementiert.'),
      z(2, '#269 uebersprungen: ungeprueft (kein Issue-Review-Marker im Body).'),
      ENDE(3),
    ].join('\n')
    const run = parseNightRunLog(text).runs[0]
    expect(run.unparsedCount).toBe(0)
    expect(run.items.map((i) => i.state)).toEqual(['GREY', 'GREY', 'GREY'])
  })

  it('deutet die Reviewer-Zeile auch mit Bindestrich in der Umgebung', () => {
    const text = [
      START(0, 'Review'),
      z(1, '  Reviewer opus (claude) in review-session: verfuegbar'),
      z(1, '  Reviewer codex (command) in review-session: NICHT verfuegbar — Befehl benoetigte Genehmigung'),
      z(1, '  Reviewer fable (claude): verfuegbar'),
      z(2, 'Nacht-Review beendet (Stufe issue): 0 ohne Befund, 0 mit Befund, 0 Schaerfung fehlt.'),
    ].join('\n')
    expect(parseNightRunLog(text).runs[0].unparsedCount).toBe(0)
  })

  it('deutet beide Praepositionen der Label-Zeile', () => {
    // Der Runner schreibt „In Ready", aber „Im Backlog".
    const text = [
      START(0),
      z(1, '  In Ready vorhandene Labels: keine'),
      z(1, '  Im Backlog vorhandene Labels: Aktuell, review:offen'),
      ENDE(2),
    ].join('\n')
    expect(parseNightRunLog(text).runs[0].unparsedCount).toBe(0)
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

  // „Festgefahren" kommt allein ueber die Ergebnisdatei des Kits, nie aus dem Textprotokoll
  // (Plan #1547, E10): Eine solche Zeile bleibt ungedeutet und erzeugt kein Paket.
  it('deutet keine Zeile des Textprotokolls zu festgefahren', () => {
    const text = [START(0), z(1, 'Issue #100 festgefahren: mvn verify, 3 Versuche'), ENDE(2)].join('\n')
    const run = parseNightRunLog(text).runs[0]
    expect(run.items.some((item) => item.errorClass === 'STUCK')).toBe(false)
    expect(run.unparsedCount).toBe(1)
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
      z(7, '  #102 Paket 3 -> uebersprungen (ungeprueft (kein Issue-Review-Marker im Body))'),
      ENDE(8),
    ].join('\n')
    const run = parseNightRunLog(text).runs[0]
    expect(run.processedCount).toBe(1)
    expect(run.skippedCount).toBe(2)
    expect(run.items.map((i) => i.position)).toEqual([0, 1, 2])
  })
})

/**
 * Das Rohprotokoll je Arbeitspaket (Issue #746, Plan #744 A1–A3). Es traegt den
 * Sitzungsstrom und bleibt damit im Browser — `zurEinlieferung` pickt seine Felder
 * einzeln, `rawLines` ist nicht darunter (Plan #718, A1).
 */
describe('parseNightRunLog — Rohprotokoll je Arbeitspaket', () => {
  it('nimmt die Session-Oeffnungszeile mit Zeitstempel-Praefix als erste Zeile auf', () => {
    const text = [START(0), z(1, 'Session 1/5: Issue #100 — Paket 1'), ENDE(2)].join('\n')
    const item = parseNightRunLog(text).runs[0].items[0]
    expect(item.rawLines[0]).toBe(z(1, 'Session 1/5: Issue #100 — Paket 1'))
  })

  it('trennt die Rohprotokolle mehrerer Arbeitspakete', () => {
    const text = [
      START(0),
      z(1, 'Session 1/5: Issue #100 — Paket 1'),
      z(6, '  Erfolg nach 5 min, Commit a1b2c3d, Issue #100 in In review.'),
      z(7, 'Session 2/5: Issue #101 — Paket 2'),
      z(9, '  Fehlschlag nach 2 min: Issue #101 nicht in In review, Tree sauber — Issue ins Backlog, weiter.'),
      ENDE(10),
    ].join('\n')
    const [erstes, zweites] = parseNightRunLog(text).runs[0].items
    expect(erstes.rawLines).toEqual([
      z(1, 'Session 1/5: Issue #100 — Paket 1'),
      z(6, '  Erfolg nach 5 min, Commit a1b2c3d, Issue #100 in In review.'),
    ])
    expect(zweites.rawLines).toEqual([
      z(7, 'Session 2/5: Issue #101 — Paket 2'),
      z(9, '  Fehlschlag nach 2 min: Issue #101 nicht in In review, Tree sauber — Issue ins Backlog, weiter.'),
    ])
  })

  it('haelt Sitzungsecho und Salvage-Zwischenzeilen beim selben Arbeitspaket', () => {
    // Alle drei Zeilen nennen #106 oder gar keine Nummer — keine schliesst das Paket.
    const text = [
      START(0),
      z(1, 'Session 1/5: Issue #106 — Paket 6'),
      z(2, '  #106 > Bash: npm test'),
      z(3, '  SALVAGE-VERSUCH gestartet (Checks extern verifiziert gruen): Issue #106 — Zwischenstand wird gegen das Issue geprueft.'),
      z(4, '  Salvage erfolgreich, Commit d4e5f6a, Issue #106 in In review.'),
      ENDE(5),
    ].join('\n')
    expect(parseNightRunLog(text).runs[0].items[0].rawLines).toHaveLength(4)
  })

  it('ordnet eine Pruefblock-Zeile dem per Nummer referenzierten Paket zu, nicht dem zuletzt offenen', () => {
    const text = [
      START(0),
      z(1, 'Session 1/5: Issue #100 — Paket 1'),
      z(6, '  Erfolg nach 5 min, Commit a1b2c3d, Issue #100 in In review.'),
      z(7, 'Session 2/5: Issue #101 — Paket 2'),
      z(8, '  Issue #100: gelaufen: npm test -> gruen (Frontend) | ausgelassen: keine'),
    ].join('\n')
    const [erstes, zweites] = parseNightRunLog(text).runs[0].items
    expect(erstes.rawLines).toContain(
      z(8, '  Issue #100: gelaufen: npm test -> gruen (Frontend) | ausgelassen: keine'),
    )
    expect(zweites.rawLines).toEqual([z(7, 'Session 2/5: Issue #101 — Paket 2')])
  })

  it('verwirft eine Pruefzeile zu einer unbekannten Nummer', () => {
    const text = [
      START(0),
      z(1, 'Session 1/5: Issue #100 — Paket 1'),
      z(9, 'Pruefungen der Sessions:'),
      z(9, '  Issue #999: gelaufen: npm test -> gruen (Frontend) | ausgelassen: keine'),
      z(9, '  Issue #998: ungeprueft — die Session hat keine Pruefung gefahren.'),
      z(9, '  Issue #997: leeres Paket — keine Pruefung, weil nichts veraendert wurde.'),
      ENDE(10),
    ].join('\n')
    const run = parseNightRunLog(text).runs[0]
    expect(run.unparsedCount).toBe(0)
    expect(run.items).toHaveLength(1)
    expect(run.items[0].rawLines).toEqual([z(1, 'Session 1/5: Issue #100 — Paket 1')])
  })

  it('nimmt Pruefblock-Kopf, Summe und Abschlusszeile in kein Rohprotokoll auf', () => {
    const text = [
      START(0),
      z(1, 'Session 1/5: Issue #100 — Paket 1'),
      z(6, '  Erfolg nach 5 min, Commit a1b2c3d, Issue #100 in In review.'),
      z(9, 'Pruefungen der Sessions:'),
      z(9, '  Issue #100: gelaufen: npm test -> gruen (Frontend) | ausgelassen: keine'),
      z(9, '  Summe: 1 Session(s) — 1 mit Pruefung, 0 ohne Aenderung, 0 ungeprueft; 1 Pruefung(en) gelaufen (davon 0 rot), 0 ausgelassen.'),
      ENDE(10),
    ].join('\n')
    expect(parseNightRunLog(text).runs[0].items[0].rawLines).toEqual([
      z(1, 'Session 1/5: Issue #100 — Paket 1'),
      z(6, '  Erfolg nach 5 min, Commit a1b2c3d, Issue #100 in In review.'),
      z(9, '  Issue #100: gelaufen: npm test -> gruen (Frontend) | ausgelassen: keine'),
    ])
  })

  it('laesst das Rohprotokoll uebergangener Arbeitspakete leer', () => {
    // Sie durchlaufen keine Session-Oeffnungszeile (A3).
    const text = [
      START(0),
      z(1, '  #100 Paket 1 -> uebersprungen (ungeprueft (kein Issue-Review-Marker im Body))'),
      z(2, '#101 zurueckgestellt: Abhaengigkeit #99 nicht erfuellt.'),
      ENDE(3),
    ].join('\n')
    expect(parseNightRunLog(text).runs[0].items.map((i) => i.rawLines)).toEqual([[], []])
  })

  it('nimmt eine Gate-Zeile des naechsten Kandidaten nicht ins Rohprotokoll des vorigen Pakets', () => {
    const text = [
      START(0),
      z(1, 'Session 1/5: Issue #100 — Paket 1'),
      z(6, '  Erfolg nach 5 min, Commit a1b2c3d, Issue #100 in In review.'),
      z(7, '#102 uebersprungen: Idee ([Idee]), wird nicht implementiert.'),
      ENDE(8),
    ].join('\n')
    const [erstes, zweites] = parseNightRunLog(text).runs[0].items
    expect(erstes.rawLines).toHaveLength(2)
    expect(zweites.rawLines).toEqual([])
  })

  it('schliesst das Paket auch bei einer stummen Gate-Zeile mit fremder Nummer', () => {
    // `#104 bewusst ohne Pruefung freigegeben …` traegt keinen Zustand, aber eine
    // fremde Nummer — sie gehoert zum naechsten Kandidaten, nicht zu #100.
    const text = [
      START(0),
      z(1, 'Session 1/5: Issue #100 — Paket 1'),
      z(6, '  Erfolg nach 5 min, Commit a1b2c3d, Issue #100 in In review.'),
      z(7, '#104 bewusst ohne Pruefung freigegeben (Pruefung: Verzicht), wird implementiert.'),
      z(8, 'Morgen-Ritual: /review -> Test -> push main. Protokoll: <PFAD>'),
      ENDE(9),
    ].join('\n')
    expect(parseNightRunLog(text).runs[0].items[0].rawLines).toHaveLength(2)
  })

  it('behaelt bei fehlender Abschlusszeile alle Zeilen bis zum Dateiende, inklusive Sitzungsstrom', () => {
    const text = [
      START(0),
      z(1, 'Session 1/5: Issue #100 — Paket 1'),
      '{"type":"assistant","message":{"role":"assistant"}}',
      z(2, '  #100 > Bash: npm test'),
      '{"type":"user"}',
    ].join('\n')
    const run = parseNightRunLog(text).runs[0]
    expect(run.incomplete).toBe(true)
    expect(run.items[0].rawLines).toEqual([
      z(1, 'Session 1/5: Issue #100 — Paket 1'),
      '{"type":"assistant","message":{"role":"assistant"}}',
      z(2, '  #100 > Bash: npm test'),
      '{"type":"user"}',
    ])
  })

  it('haengt die praefixlose Fehler-Zeile noch an und schliesst das Paket danach', () => {
    const text = [
      START(0),
      z(1, 'Session 1/5: Issue #100 — Paket 1'),
      'Fehler: Working Tree ist nicht sauber.',
      '{"type":"user"}',
    ].join('\n')
    expect(parseNightRunLog(text).runs[0].items[0].rawLines).toEqual([
      z(1, 'Session 1/5: Issue #100 — Paket 1'),
      'Fehler: Working Tree ist nicht sauber.',
    ])
  })

  it('entfernt nur das Wagenruecklauf-Zeichen am Zeilenende', () => {
    const text = [START(0), z(1, 'Session 1/5: Issue #100 — Paket 1'), ENDE(2)].join('\r\n')
    expect(parseNightRunLog(text).runs[0].items[0].rawLines).toEqual([
      z(1, 'Session 1/5: Issue #100 — Paket 1'),
    ])
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

  /**
   * Die Grenze zwischen den beiden Parsern (Issue #865): `stand`, `kennzahlen` und
   * `kettenStufen` stammen ausschliesslich aus einem Ergebnisstand. Der Test belegt
   * heute, dass Code, der nichts setzt, nichts setzt — er sichert aber die Grenze, und
   * genau die ist beim naechsten Umbau der gefaehrdete Punkt (Plan #863, abgelehnter
   * Hinweis 15 des Reviews).
   */
  it('setzt weder die Lauf-Angaben noch die Kennzahlen eines Ergebnisstands', () => {
    const run = parseNightRunLog(vollstaendig).runs[0]
    expect(run).not.toHaveProperty('stand')
    for (const item of run.items) {
      expect(item).not.toHaveProperty('kennzahlen')
      expect(item).not.toHaveProperty('kettenStufen')
    }
  })
})

/**
 * Jede Ausgangszeile mit mehrstelligen Minuten samt zwei Nachkommastellen und einer
 * vierstelligen Kartennummer (Issue #1561). Verglichen wird das ganze Paket, nicht nur der
 * Zustand: Ein eroeffnetes Paket ohne gedeuteten Ausgang ist schon rot mit `HARD_ABORT` —
 * nur Auszug, Dauer und Commit zeigen, ob die Zeile wirklich gegriffen hat.
 */
describe('parseNightRunLog — jede Ausgangszeile vollstaendig gedeutet', () => {
  const ausgang = (zeile: string) =>
    parseNightRunLog(
      [START(0), z(1, 'Session 12/15: Issue #1234 — Paket 1'), z(9, zeile), ENDE(10)].join('\n'),
    ).runs[0]

  const DAUER = 12.25 * 60_000

  it.each([
    ['  Erfolg nach 12.25 min, Commit a1b2c3d, Issue #1234 in In review.', 'GREEN', undefined, DAUER, 'a1b2c3d'],
    ['  Salvage erfolgreich, Commit d4e5f6a, Issue #1234 in In review.', 'GREEN', undefined, undefined, 'd4e5f6a'],
    [
      '  FEHLSCHLAG nach 12.25 min: Issue #1234 nicht in In review UND Working Tree dirty — harter Stopp.',
      'RED',
      'HARD_ABORT',
      DAUER,
      undefined,
    ],
    [
      '  Fehlschlag nach 12.25 min: Issue #1234 — die Session hat nichts hinterlassen, Issue ins Backlog.',
      'RED',
      'CHECKS_NOT_STARTED',
      DAUER,
      undefined,
    ],
    [
      '  Fehlschlag nach 12.25 min: Issue #1234 nicht in In review, Tree sauber — Issue ins Backlog, weiter.',
      'RED',
      'UNEXPECTED_STATE',
      DAUER,
      undefined,
    ],
    [
      '  INFRASTRUKTUR-FEHLSCHLAG nach 12.25 min (Exit 1): Session-Start gescheitert — harter Stopp, Issue #1234 bleibt unangetastet.',
      'RED',
      'HARD_ABORT',
      DAUER,
      undefined,
    ],
    [
      '  INFRASTRUKTUR-FEHLSCHLAG nach 12 min (Exit 1): Session-Start gescheitert — harter Stopp, Issue #1234 bleibt unangetastet.',
      'RED',
      'HARD_ABORT',
      12 * 60_000,
      undefined,
    ],
    [
      '  HARTER STOPP: erfolgreiche Runde zu Issue #1234 hat unkommittete Reste hinterlassen.',
      'RED',
      'HARD_ABORT',
      undefined,
      undefined,
    ],
    [
      '  SALVAGE-VERSUCH gescheitert — harter Stopp. Issue #1234 weiterhin nicht in In review.',
      'RED',
      'HARD_ABORT',
      undefined,
      undefined,
    ],
    ['  Erfolg nach 12.25 min: Issue #1234 geprueft mit Befund, Marker gesetzt.', 'GREEN', undefined, DAUER, undefined],
    [
      '  Nach 12.25 min: Issue #1234 — Synthese ohne Beleg: kit:klaeren gesetzt.',
      'RED',
      'AWAITING_DECISION',
      DAUER,
      undefined,
    ],
    [
      '  Nach 12.25 min: Issue #1234 — Befunde vorhanden, aber kein Body-Vorschlag.',
      'YELLOW',
      'CHECKS_NOT_STARTED',
      DAUER,
      undefined,
    ],
  ])('deutet %s', (zeile, state, errorClass, durationMs, commit) => {
    const run = ausgang(zeile)
    expect(run.unparsedCount).toBe(0)
    expect(run.items).toHaveLength(1)
    const [item] = run.items
    expect({
      cardNumber: item.cardNumber,
      title: item.title,
      state: item.state,
      errorClass: item.errorClass,
      durationMs: item.durationMs,
      commit: item.commit,
      excerpt: item.excerpt,
    }).toEqual({ cardNumber: 1234, title: 'Paket 1', state, errorClass, durationMs, commit, excerpt: zeile })
  })

  it('behaelt Dauer und Commit, wenn eine spaetere Zeile zum selben Paket keine nennt', () => {
    const text = [
      START(0),
      z(1, 'Session 1/5: Issue #1234 — Paket 1'),
      z(6, '  Erfolg nach 5 min, Commit a1b2c3d, Issue #1234 in In review.'),
      z(7, '  HARTER STOPP: erfolgreiche Runde zu Issue #1234 hat unkommittete Reste hinterlassen.'),
    ].join('\n')
    expect(parseNightRunLog(text).runs[0].items[0]).toMatchObject({
      state: 'RED',
      errorClass: 'HARD_ABORT',
      durationMs: 5 * 60_000,
      commit: 'a1b2c3d',
    })
  })

  it('macht ein Paket ueber --max grau und uebernimmt seinen Titel', () => {
    const text = [START(0), z(1, '  #1234 Paket 5 -> ueber --max 12, bleibt liegen.'), ENDE(2)].join('\n')
    const run = parseNightRunLog(text).runs[0]
    expect(run.unparsedCount).toBe(0)
    expect(run.items[0]).toMatchObject({ cardNumber: 1234, title: 'Paket 5', state: 'GREY' })
  })

  it('laesst den Titel eines Pakets leer, dessen Zeile keinen nennt', () => {
    const text = [START(0), z(1, '#1234 zurueckgestellt: Abhaengigkeit #99 nicht erfuellt.'), ENDE(2)].join('\n')
    expect(parseNightRunLog(text).runs[0].items[0].title).toBe('')
  })

  it('laesst ein rotes Paket bei roter Pruefung rot', () => {
    const text = [
      START(0),
      z(1, 'Session 1/5: Issue #100 — Paket 1'),
      z(6, '  Fehlschlag nach 5 min: Issue #100 nicht in In review, Tree sauber — Issue ins Backlog, weiter.'),
      z(7, 'Pruefungen der Sessions:'),
      z(7, '  Issue #100: gelaufen: npm test -> rot (Frontend) | ausgelassen: keine'),
      ENDE(8),
    ].join('\n')
    expect(parseNightRunLog(text).runs[0].items[0]).toMatchObject({ state: 'RED', errorClass: 'UNEXPECTED_STATE' })
  })
})

/**
 * Die Muster greifen nur am Zeilenanfang und mit genau der Einrueckung, die `night.mjs`
 * schreibt (Issue #1561). Eine Zeile, die den Text nur weiter hinten oder mit anderer
 * Einrueckung traegt, ist ungedeutet — sie eroeffnet kein Paket und zaehlt.
 */
describe('parseNightRunLog — Muster nur am Zeilenanfang', () => {
  const nurDiese = (zeile: string) => parseNightRunLog([START(0), z(1, zeile), ENDE(2)].join('\n')).runs[0]

  const AUSGAENGE = [
    '  Erfolg nach 5 min, Commit a1b2c3d, Issue #100 in In review.',
    '  Salvage erfolgreich, Commit d4e5f6a, Issue #100 in In review.',
    '  FEHLSCHLAG nach 5 min: Issue #100 nicht in In review UND Working Tree dirty — harter Stopp.',
    '  Fehlschlag nach 5 min: Issue #100 — die Session hat nichts hinterlassen.',
    '  Fehlschlag nach 5 min: Issue #100 nicht in In review, Tree sauber — weiter.',
    '  INFRASTRUKTUR-FEHLSCHLAG nach 5 min (Exit 1): harter Stopp, Issue #100 bleibt unangetastet.',
    '  HARTER STOPP: erfolgreiche Runde zu Issue #100 hat unkommittete Reste hinterlassen.',
    '  SALVAGE-VERSUCH gescheitert — harter Stopp. Issue #100 weiterhin nicht in In review.',
    '  Erfolg nach 5 min: Issue #100 geprueft ohne Befund, Marker gesetzt.',
    '  Nach 5 min: Issue #100 — Synthese ohne Beleg: kit:klaeren gesetzt.',
    '  Nach 5 min: Issue #100 — Befunde vorhanden, aber kein Body-Vorschlag.',
    '  #100 Paket 1 -> uebersprungen (kit:klaeren, offene Entscheidung)',
    '  #100 Paket 1 -> uebersprungen (ungeprueft)',
    '  #100 Paket 1 -> ueber --max 5, bleibt liegen.',
    '#100 zurueckgestellt: Abhaengigkeit #99 nicht erfuellt.',
    '#100 uebersprungen: Marker vorhanden.',
    'Session 1/5: Issue #100 — Paket 1',
  ]

  const STUMME = [
    '  #100 > Bash: npm test',
    '#100 bewusst ohne Pruefung freigegeben (Pruefung: Verzicht), wird implementiert.',
    '  Vorflug-Session startet (Modell claude-sonnet-5).',
    '  Reviewer fable (claude) in runner: verfuegbar',
    '  Kein Reviewer konfiguriert: Pruefung entfaellt.',
    '  Tracker (runner): erreichbar',
    '  buildChecks rot — einmaliger Format-Fix wird angewendet: npm run format',
    '  FORMAT-FIX angewendet, buildChecks jetzt gruen.',
    '  SALVAGE-VERSUCH gestartet (Checks extern verifiziert gruen): Issue #100.',
    '  Salvage nicht moeglich: Checks rot.',
    '  Hinweis: die vorherige Pruef-Zusammenfassung fehlt.',
    '  CLI-Meldung: Rate limit erreicht.',
    '  In Ready vorhandene Labels: kit:nightrun',
    '  Tippfehler im --label-Wert?',
    '  Tippfehler im --review-label-Wert?',
    'WARNUNG: Budget knapp.',
    'Ready ist leer — nichts zu tun.',
    'Keine Review-Kandidaten im Backlog.',
    'Morgen-Ritual: /review -> Test -> push main.',
    'Pruefungen der Sessions:',
    'Pruefungen: keine Implementierungs-Runde gelaufen.',
    '  Summe: 12 Session(s) — 12 mit Pruefung.',
    '  Issue #100: gelaufen: npm test -> gruen (Frontend) | ausgelassen: keine',
    '  Issue #100: ungeprueft — die Session hat keine Pruefung gefahren.',
    '  Issue #100: leeres Paket — nichts zu pruefen.',
  ]

  it.each(STUMME)('deutet die stumme Zeile %s', (zeile) => {
    expect(nurDiese(zeile).unparsedCount).toBe(0)
  })

  it.each([...AUSGAENGE, ...STUMME])('deutet %s nicht hinter fremdem Text', (zeile) => {
    const run = nurDiese(`x${zeile}`)
    expect(run.unparsedCount).toBe(1)
    expect(run.items).toEqual([])
  })

  it.each([...AUSGAENGE, ...STUMME].filter((zeile) => zeile.startsWith('  ')))(
    'deutet %s nicht mit nur einem Leerzeichen Einrueckung',
    (zeile) => {
      const run = nurDiese(zeile.slice(1))
      expect(run.unparsedCount).toBe(1)
      expect(run.items).toEqual([])
    },
  )

  it.each(['Pruefungen der Sessions: x', 'Pruefungen: keine Implementierungs-Runde gelaufen. x'])(
    'deutet %s nicht mit angehaengtem Text',
    (zeile) => {
      expect(nurDiese(zeile).unparsedCount).toBe(1)
    },
  )

  it('ordnet eine stumme Zeile nur nach der Nummer an ihrem Anfang zu', () => {
    const text = [
      START(0),
      z(1, 'Session 1/5: Issue #100 — Paket 1'),
      z(2, '  CLI-Meldung: siehe #200 im Protokoll'),
      ENDE(3),
    ].join('\n')
    expect(parseNightRunLog(text).runs[0].items[0].rawLines).toEqual([
      z(1, 'Session 1/5: Issue #100 — Paket 1'),
      z(2, '  CLI-Meldung: siehe #200 im Protokoll'),
    ])
  })

  it('entfernt einen Wagenruecklauf mitten in der Zeile nicht', () => {
    const text = [START(0), z(1, 'Session 1/5: Issue #100 — Paket 1'), 'vorher\rnachher', ENDE(2)].join('\n')
    expect(parseNightRunLog(text).runs[0].items[0].rawLines).toEqual([
      z(1, 'Session 1/5: Issue #100 — Paket 1'),
      'vorher\rnachher',
    ])
  })
})
