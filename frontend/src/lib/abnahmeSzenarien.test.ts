import { describe, expect, it } from 'vitest'
import { abnahmeKommentar, abnahmeStand, szenarienAus, type Szenario, type Vermerk } from './abnahmeSzenarien'

/** Baut einen Body aus Zeilen — die Fixtures bleiben so zeilengenau lesbar. */
const body = (...zeilen: string[]) => zeilen.join('\n')

const OKAY: Vermerk = { urteil: 'okay', notiz: '' }
const nichtOkay = (notiz = ''): Vermerk => ({ urteil: 'nicht okay', notiz })

describe('szenarienAus — Abschnitt und Szenarien', () => {
  it('liest mehrere Szenarien in der Reihenfolge des Fachplans', () => {
    const szenarien = szenarienAus(
      body(
        '## Ziel',
        'Wenn das hier stuende, waere es kein Szenario.',
        '## Szenarien',
        '### Karte anlegen',
        'Angenommen ein leeres Board',
        '### Karte verschieben',
        'Wenn ich die Karte ziehe',
      ),
    )
    expect(szenarien).toEqual([
      { name: 'Karte anlegen', schritte: ['Angenommen ein leeres Board'] },
      { name: 'Karte verschieben', schritte: ['Wenn ich die Karte ziehe'] },
    ])
  })

  it('liest die Schritte eines Szenarios in der Reihenfolge des Fachplans', () => {
    const [szenario] = szenarienAus(
      body(
        '## Szenarien',
        '### Anmelden',
        'Angenommen ein Konto existiert',
        'Und es ist bestaetigt',
        'Wenn ich mich anmelde',
        'Dann sehe ich die Projekte',
        'Aber keine fremden',
      ),
    )
    expect(szenario.schritte).toEqual([
      'Angenommen ein Konto existiert',
      'Und es ist bestaetigt',
      'Wenn ich mich anmelde',
      'Dann sehe ich die Projekte',
      'Aber keine fremden',
    ])
  })

  it('liest Listenpunkte und fette Schluesselwoerter ohne Listenzeichen und Fettdruck', () => {
    const [szenario] = szenarienAus(
      body(
        '## Szenarien',
        '### Liste',
        '- **Angenommen** ein Board',
        '* Wenn ich klicke',
        '+ **Dann** geht ein Dialog auf',
        '1. Und er hat einen Titel',
        '  **Aber**   keinen Knopf  ',
      ),
    )
    expect(szenario.schritte).toEqual([
      'Angenommen ein Board',
      'Wenn ich klicke',
      'Dann geht ein Dialog auf',
      'Und er hat einen Titel',
      'Aber keinen Knopf',
    ])
  })

  it('liest die englische Form', () => {
    expect(
      szenarienAus(
        body(
          '## Scenarios',
          '### Sign in',
          '- **Given** an account',
          '- And it is verified',
          '- When I sign in',
          '- Then I see my projects',
          '- But no foreign ones',
        ),
      ),
    ).toEqual([
      {
        name: 'Sign in',
        schritte: ['Given an account', 'And it is verified', 'When I sign in', 'Then I see my projects', 'But no foreign ones'],
      },
    ])
  })

  it('beendet den Abschnitt an der naechsten ##-Ueberschrift', () => {
    const szenarien = szenarienAus(
      body('## Szenarien', '### Eins', 'Angenommen A', '## Akzeptanzkriterien', '### Zwei', 'Angenommen B'),
    )
    expect(szenarien).toEqual([{ name: 'Eins', schritte: ['Angenommen A'] }])
  })

  it('beendet den Abschnitt auch an einer #-Ueberschrift', () => {
    expect(szenarienAus(body('## Szenarien', '### Eins', 'Angenommen A', '# Anhang', 'Dann B'))).toEqual([
      { name: 'Eins', schritte: ['Angenommen A'] },
    ])
  })

  it('liefert fuer einen Body ohne Abschnitt eine leere Liste', () => {
    expect(szenarienAus(body('## Ziel', '### Eins', 'Angenommen A', 'Dann B'))).toEqual([])
    expect(szenarienAus('')).toEqual([])
  })

  it('erkennt den Abschnitt nur als eigene ##-Ueberschrift', () => {
    expect(szenarienAus(body('### Szenarien', '### Eins', 'Angenommen A'))).toEqual([])
    expect(szenarienAus(body('Text ## Szenarien', '### Eins', 'Angenommen A'))).toEqual([])
    expect(szenarienAus(body('## Szenarien und mehr', '### Eins', 'Angenommen A'))).toEqual([])
  })

  it('laesst ein Szenario ohne Schritte weg', () => {
    expect(
      szenarienAus(body('## Szenarien', '### Leer', 'nur Text ohne Schluesselwort', '### Voll', 'Dann geht es')),
    ).toEqual([{ name: 'Voll', schritte: ['Dann geht es'] }])
  })

  it('haengt eine Zeile ohne Schluesselwort als Fortsetzung an den vorigen Schritt', () => {
    const [szenario] = szenarienAus(
      body('## Szenarien', '### Lang', 'Angenommen ein Board', '  mit drei Spalten', '', 'Dann ist es voll'),
    )
    expect(szenario.schritte).toEqual(['Angenommen ein Board mit drei Spalten', 'Dann ist es voll'])
  })

  it('verwirft eine Zeile ohne Schluesselwort vor dem ersten Schritt eines Szenarios', () => {
    const [eins, zwei] = szenarienAus(
      body(
        'Angenommen vor dem Abschnitt',
        '## Szenarien',
        'Angenommen vor dem ersten Szenario',
        '### Eins',
        'Einleitender Satz',
        'Angenommen A',
        '### Zwei',
        'Noch ein Satz',
        'Wenn B',
      ),
    )
    expect(eins).toEqual({ name: 'Eins', schritte: ['Angenommen A'] })
    expect(zwei).toEqual({ name: 'Zwei', schritte: ['Wenn B'] })
  })

  it('erkennt ein Schluesselwort nur als ganzes Wort', () => {
    const [szenario] = szenarienAus(body('## Szenarien', '### Wort', 'Angenommen A', 'Wenngleich es so aussieht', 'Dann'))
    expect(szenario.schritte).toEqual(['Angenommen A Wenngleich es so aussieht', 'Dann'])
  })

  it('liest keine Zeilen aus einem Codeblock', () => {
    const [szenario] = szenarienAus(
      body(
        '## Szenarien',
        '### Mit Beispiel',
        'Angenommen ein Fachplan',
        '```markdown',
        'Angenommen im Beispiel',
        '## Ziel',
        '### Kein Szenario',
        '```',
        '~~~',
        'Wenn im zweiten Beispiel',
        '~~~',
        'Dann bleibt der Abschnitt offen',
      ),
    )
    expect(szenario.schritte).toEqual(['Angenommen ein Fachplan', 'Dann bleibt der Abschnitt offen'])
  })

  it('erkennt den Abschnitt nicht in einem Codeblock', () => {
    expect(szenarienAus(body('```', '## Szenarien', '### Eins', 'Angenommen A', '```'))).toEqual([])
  })

  it('behaelt zwei gleichnamige Szenarien als zwei', () => {
    expect(szenarienAus(body('## Szenarien', '### Gleich', 'Angenommen A', '### Gleich', 'Angenommen B'))).toEqual([
      { name: 'Gleich', schritte: ['Angenommen A'] },
      { name: 'Gleich', schritte: ['Angenommen B'] },
    ])
  })

  it('liest Windows-Zeilenenden', () => {
    expect(szenarienAus('## Szenarien\r\n### Eins\r\nAngenommen A\r\n')).toEqual([{ name: 'Eins', schritte: ['Angenommen A'] }])
  })
})

const DREI: Szenario[] = [
  { name: 'Anlegen', schritte: ['Angenommen A'] },
  { name: 'Ziehen', schritte: ['Wenn B'] },
  { name: 'Loeschen', schritte: ['Dann C'] },
]

describe('abnahmeStand', () => {
  it('zaehlt alle Szenarien okay', () => {
    expect(abnahmeStand(DREI, [OKAY, OKAY, OKAY])).toEqual({ okay: 3, nichtOkay: 0, offen: 0, nichtOkaySzenarien: [] })
  })

  it('nennt die nicht okayen Szenarien mit Nummer und Namen', () => {
    expect(abnahmeStand(DREI, [nichtOkay('kaputt'), OKAY, nichtOkay()])).toEqual({
      okay: 1,
      nichtOkay: 2,
      offen: 0,
      nichtOkaySzenarien: [
        { nummer: 1, name: 'Anlegen' },
        { nummer: 3, name: 'Loeschen' },
      ],
    })
  })

  it('zaehlt Szenarien ohne Vermerk als offen, auch hinter dem Ende der Vermerke', () => {
    expect(abnahmeStand(DREI, [undefined, OKAY])).toEqual({ okay: 1, nichtOkay: 0, offen: 2, nichtOkaySzenarien: [] })
  })
})

describe('abnahmeKommentar', () => {
  it('meldet alle Szenarien okay', () => {
    expect(abnahmeKommentar(DREI, [OKAY, OKAY, OKAY])).toBe(
      body(
        '## Abnahme der Szenarien',
        '',
        'Ergebnis: alle 3 Szenarien okay.',
        '',
        '1. **Anlegen** — okay',
        '2. **Ziehen** — okay',
        '3. **Loeschen** — okay',
      ),
    )
  })

  it('nennt bei gemischtem Ergebnis die nicht okayen Szenarien und die Notiz', () => {
    expect(abnahmeKommentar(DREI, [OKAY, nichtOkay('Karte springt zurueck'), nichtOkay('Dialog fehlt')])).toBe(
      body(
        '## Abnahme der Szenarien',
        '',
        'Ergebnis: 1 von 3 okay, nicht okay: 2. Ziehen, 3. Loeschen.',
        '',
        '1. **Anlegen** — okay',
        '2. **Ziehen** — nicht okay: Karte springt zurueck',
        '3. **Loeschen** — nicht okay: Dialog fehlt',
      ),
    )
  })

  it('schreibt „nicht okay“ ohne Notiz ohne Doppelpunkt', () => {
    const kommentar = abnahmeKommentar(DREI, [OKAY, nichtOkay(), nichtOkay('   ')])
    expect(kommentar).toContain('2. **Ziehen** — nicht okay\n')
    expect(kommentar.endsWith('3. **Loeschen** — nicht okay')).toBe(true)
  })

  it('macht eine mehrzeilige Notiz einzeilig', () => {
    expect(abnahmeKommentar(DREI, [OKAY, OKAY, nichtOkay('erste Zeile\r\n\n  zweite Zeile\nletzte ')])).toContain(
      '3. **Loeschen** — nicht okay: erste Zeile zweite Zeile letzte',
    )
  })

  it('maskiert Markdown-Zeichen in Name und Notiz', () => {
    const szenarien: Szenario[] = [{ name: 'a|b *c* _d_', schritte: ['Angenommen A'] }]
    expect(abnahmeKommentar(szenarien, [nichtOkay('x|y *z* _w_ `v` [u] \\t')])).toBe(
      body(
        '## Abnahme der Szenarien',
        '',
        'Ergebnis: 0 von 1 okay, nicht okay: 1. a\\|b \\*c\\* \\_d\\_.',
        '',
        '1. **a\\|b \\*c\\* \\_d\\_** — nicht okay: x\\|y \\*z\\* \\_w\\_ \\`v\\` \\[u\\] \\\\t',
      ),
    )
  })

  it('unterscheidet gleichnamige Szenarien durch die Nummer', () => {
    const gleich: Szenario[] = [
      { name: 'Gleich', schritte: ['Angenommen A'] },
      { name: 'Gleich', schritte: ['Angenommen B'] },
    ]
    expect(abnahmeKommentar(gleich, [OKAY, nichtOkay('B fehlt')])).toBe(
      body(
        '## Abnahme der Szenarien',
        '',
        'Ergebnis: 1 von 2 okay, nicht okay: 2. Gleich.',
        '',
        '1. **Gleich** — okay',
        '2. **Gleich** — nicht okay: B fehlt',
      ),
    )
  })

  it('fuehrt Szenarien ohne Vermerk als offen', () => {
    expect(abnahmeKommentar(DREI, [OKAY, undefined])).toBe(
      body(
        '## Abnahme der Szenarien',
        '',
        'Ergebnis: 1 von 3 okay, offen: 2.',
        '',
        '1. **Anlegen** — okay',
        '2. **Ziehen** — offen',
        '3. **Loeschen** — offen',
      ),
    )
  })
})
