import { describe, expect, it } from 'vitest'
import {
  MAX_DESCRIPTION_LENGTH,
  MAX_IDEAS_PER_IMPORT,
  MAX_TITLE_LENGTH,
  splitSpecIntoSections,
} from './specImport'

describe('splitSpecIntoSections', () => {
  it('macht aus jeder H2-Überschrift eine Karte mit Titel und Beschreibung', () => {
    const md = [
      '## Anmeldung',
      'Nutzer meldet sich mit E-Mail an.',
      '',
      '## Registrierung',
      'Neue Nutzer legen ein Konto an.',
      '',
      '## Passwort vergessen',
      'Zurücksetzen per Mail.',
    ].join('\n')

    expect(splitSpecIntoSections(md, 2)).toEqual([
      {
        title: 'Anmeldung',
        description: 'Nutzer meldet sich mit E-Mail an.',
        titleTruncated: false,
        descriptionTruncated: false,
      },
      {
        title: 'Registrierung',
        description: 'Neue Nutzer legen ein Konto an.',
        titleTruncated: false,
        descriptionTruncated: false,
      },
      {
        title: 'Passwort vergessen',
        description: 'Zurücksetzen per Mail.',
        titleTruncated: false,
        descriptionTruncated: false,
      },
    ])
  })

  it('deutet eine #-Zeile in einem Code-Block nicht als Überschrift', () => {
    const md = [
      '## Installation',
      'Vorbereiten:',
      '```bash',
      '# Abhängigkeiten holen',
      'npm install',
      '```',
      'Fertig.',
    ].join('\n')

    const sections = splitSpecIntoSections(md, 1)
    expect(sections).toEqual([])

    const level2 = splitSpecIntoSections(md, 2)
    expect(level2).toHaveLength(1)
    expect(level2[0].description).toContain('# Abhängigkeiten holen')
  })

  it('schützt auch Tilde-Fences und schließt nur mit demselben Zeichen', () => {
    const md = ['## A', '~~~', '## Kein Titel', '```', '## Auch nicht', '~~~', '## B', 'Text'].join(
      '\n',
    )

    const sections = splitSpecIntoSections(md, 2)
    expect(sections.map((s) => s.title)).toEqual(['A', 'B'])
    expect(sections[0].description).toContain('## Kein Titel')
  })

  it('schließt einen Fence nicht mit einer kürzeren Markierung oder mit Info-String', () => {
    const md = ['## A', '````', '```', '## Kein Titel', '``` js', '````', '## B'].join('\n')

    expect(splitSpecIntoSections(md, 2).map((s) => s.title)).toEqual(['A', 'B'])
  })

  it('erkennt eingerückte Fences und schließt trotz Leerraum hinter der Markierung', () => {
    const md = ['## A', '  ```js', '## Kein Titel', '  ``` ', '## B'].join('\n')

    const sections = splitSpecIntoSections(md, 2)
    expect(sections.map((s) => s.title)).toEqual(['A', 'B'])
    expect(sections[0].description).toContain('## Kein Titel')
  })

  it('lässt einen nicht geschlossenen Code-Block bis zum Dateiende offen', () => {
    const md = ['## A', '```', '## Kein Titel'].join('\n')

    const sections = splitSpecIntoSections(md, 2)
    expect(sections.map((s) => s.title)).toEqual(['A'])
    expect(sections[0].description).toContain('## Kein Titel')
  })

  it('wechselt die Auflösung mit der gewählten Überschriftenebene', () => {
    const md = ['# Kapitel', 'Vorwort.', '## Erstes', 'A', '## Zweites', 'B'].join('\n')

    expect(splitSpecIntoSections(md, 1).map((s) => s.title)).toEqual(['Kapitel'])
    expect(splitSpecIntoSections(md, 2).map((s) => s.title)).toEqual(['Erstes', 'Zweites'])
  })

  it('verwirft den Vorspann vor der ersten passenden Überschrift', () => {
    const md = ['Inhaltsverzeichnis', '- Erstes', '', '## Erstes', 'Der Text.'].join('\n')

    expect(splitSpecIntoSections(md, 2)).toEqual([
      {
        title: 'Erstes',
        description: 'Der Text.',
        titleTruncated: false,
        descriptionTruncated: false,
      },
    ])
  })

  it('behält tiefere Überschriften als Teil der Beschreibung', () => {
    const md = ['## Erstes', 'Rahmen.', '### Detail', 'Feinheit.', '## Zweites', 'B'].join('\n')

    const sections = splitSpecIntoSections(md, 2)
    expect(sections.map((s) => s.title)).toEqual(['Erstes', 'Zweites'])
    expect(sections[0].description).toBe('Rahmen.\n### Detail\nFeinheit.')
  })

  it('liefert für eine leere Datei nichts', () => {
    expect(splitSpecIntoSections('', 2)).toEqual([])
    expect(splitSpecIntoSections('   \n\n', 2)).toEqual([])
  })

  it('liefert nichts, wenn die Datei keine Überschrift der gewählten Ebene hat', () => {
    expect(splitSpecIntoSections('Nur Fließtext ohne Struktur.', 2)).toEqual([])
  })

  it('erlaubt einen Abschnitt ohne Text unter der Überschrift', () => {
    const sections = splitSpecIntoSections(['## Leer', '', '## Voll', 'Text'].join('\n'), 2)

    expect(sections[0]).toEqual({
      title: 'Leer',
      description: '',
      titleTruncated: false,
      descriptionTruncated: false,
    })
  })

  it('kürzt einen zu langen Titel und meldet die Kürzung', () => {
    const long = 'T'.repeat(MAX_TITLE_LENGTH + 5)

    const [section] = splitSpecIntoSections(`## ${long}\nText`, 2)

    expect(section.title).toHaveLength(MAX_TITLE_LENGTH)
    expect(section.titleTruncated).toBe(true)
  })

  it('kürzt eine zu lange Beschreibung und meldet die Kürzung', () => {
    const long = 'B'.repeat(MAX_DESCRIPTION_LENGTH + 5)

    const [section] = splitSpecIntoSections(`## Titel\n${long}`, 2)

    expect(section.description).toHaveLength(MAX_DESCRIPTION_LENGTH)
    expect(section.descriptionTruncated).toBe(true)
  })

  it('verarbeitet Windows-Zeilenenden ohne Wagenrücklauf im Ergebnis', () => {
    const sections = splitSpecIntoSections('## Titel\r\nText\r\n', 2)

    expect(sections).toEqual([
      { title: 'Titel', description: 'Text', titleTruncated: false, descriptionTruncated: false },
    ])
  })

  it('entfernt die abschließende Rautenfolge einer Überschrift', () => {
    expect(splitSpecIntoSections('## Titel ##\nText', 2)[0].title).toBe('Titel')
  })

  it('startet keine Karte für eine Überschrift ohne Text', () => {
    const md = ['## Erstes', 'A', '##', 'B', '## ', 'C'].join('\n')

    const sections = splitSpecIntoSections(md, 2)
    expect(sections.map((s) => s.title)).toEqual(['Erstes'])
    expect(sections[0].description).toBe('A\n##\nB\n## \nC')
  })

  it('ignoriert eine Überschrift ohne Text auch vor der ersten echten Überschrift', () => {
    expect(splitSpecIntoSections('##\n## Erstes\nA', 2).map((s) => s.title)).toEqual(['Erstes'])
  })

  it('erkennt nur echte ATX-Überschriften (Leerzeichen nötig, höchstens drei Einrückungen)', () => {
    const md = ['## Erstes', '##kein Titel', '    ## eingerückt', '   ## Zweites'].join('\n')

    const sections = splitSpecIntoSections(md, 2)
    expect(sections.map((s) => s.title)).toEqual(['Erstes', 'Zweites'])
    expect(sections[0].description).toBe('##kein Titel\n    ## eingerückt')
  })

  it('hält die Obergrenze als Konstante bereit (Grenze des Batch-Endpoints)', () => {
    expect(MAX_IDEAS_PER_IMPORT).toBe(200)
  })
})
