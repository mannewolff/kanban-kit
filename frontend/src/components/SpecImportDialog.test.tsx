import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { MAX_DESCRIPTION_LENGTH, MAX_IDEAS_PER_IMPORT, MAX_TITLE_LENGTH } from '../lib/specImport'
import { SpecImportDialog } from './SpecImportDialog'

const SPEC = [
  '# Spezifikation Login',
  'Vorwort, das keine Karte werden soll.',
  '',
  '## Anmeldung',
  'Nutzer meldet sich mit E-Mail an.',
  '',
  '## Registrierung',
  'Neue Nutzer legen ein Konto an.',
].join('\n')

function renderDialog(
  overrides: Partial<{ open: boolean; fileName: string; markdown: string }> = {},
  onImport: (ideas: Array<{ title: string; description: string | null }>) => Promise<void> = () =>
    Promise.resolve(),
  onClose: () => void = () => {},
) {
  return render(
    <SpecImportDialog
      open={overrides.open ?? true}
      fileName={overrides.fileName ?? 'spec.md'}
      markdown={overrides.markdown ?? SPEC}
      onClose={onClose}
      onImport={onImport}
    />,
  )
}

function checkbox(title: string): HTMLElement {
  return screen.getByRole('checkbox', { name: `Abschnitt „${title}“ einlesen` })
}

describe('SpecImportDialog', () => {
  it('rendert nichts, solange er geschlossen ist', () => {
    renderDialog({ open: false })

    expect(screen.queryByText('Spezifikation einlesen')).not.toBeInTheDocument()
  })

  it('zeigt Dateiname, Anzahl und die entstehenden Karten in der Vorschau', () => {
    renderDialog()

    expect(screen.getByText('spec.md')).toBeInTheDocument()
    expect(screen.getByText('2 von 2 Abschnitten ausgewählt.')).toBeInTheDocument()
    expect(screen.getByText('Anmeldung')).toBeInTheDocument()
    expect(screen.getByText('Nutzer meldet sich mit E-Mail an.')).toBeInTheDocument()
    expect(screen.getByText('Registrierung')).toBeInTheDocument()
    // Der Vorspann vor der ersten H2 wird keine Karte.
    expect(screen.queryByText('Spezifikation Login')).not.toBeInTheDocument()
  })

  it('stellt klar, dass die Datei nur im Browser gelesen wird', () => {
    renderDialog()

    expect(
      screen.getByText(/nur im Browser gelesen.*weder hochgeladen noch verändert oder gelöscht/i),
    ).toBeInTheDocument()
  })

  it('aktualisiert die Vorschau sofort beim Umschalten auf H1', () => {
    renderDialog()
    expect(screen.getByText('Anmeldung')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Trennende Überschriftenebene'), {
      target: { value: '1' },
    })

    expect(screen.getByText('Spezifikation Login')).toBeInTheDocument()
    expect(screen.queryByText('Anmeldung')).not.toBeInTheDocument()
    expect(screen.getByText('1 von 1 Abschnitten ausgewählt.')).toBeInTheDocument()
  })

  it('legt abgewählte Abschnitte nicht an', async () => {
    const onImport = vi.fn().mockResolvedValue(undefined)
    renderDialog({}, onImport)

    fireEvent.click(checkbox('Anmeldung'))
    expect(screen.getByText('1 von 2 Abschnitten ausgewählt.')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '1 Idee anlegen' }))

    await waitFor(() =>
      expect(onImport).toHaveBeenCalledWith([
        { title: 'Registrierung', description: 'Neue Nutzer legen ein Konto an.' },
      ]),
    )
  })

  it('nimmt eine Abwahl wieder zurück', () => {
    renderDialog()

    fireEvent.click(checkbox('Anmeldung'))
    fireEvent.click(checkbox('Anmeldung'))

    expect(screen.getByText('2 von 2 Abschnitten ausgewählt.')).toBeInTheDocument()
  })

  it('setzt eine Abwahl beim Ebenenwechsel zurück, damit keine fremde Position nachwirkt', () => {
    renderDialog()

    fireEvent.click(checkbox('Anmeldung'))
    fireEvent.change(screen.getByLabelText('Trennende Überschriftenebene'), {
      target: { value: '1' },
    })
    fireEvent.change(screen.getByLabelText('Trennende Überschriftenebene'), {
      target: { value: '2' },
    })

    expect(screen.getByText('2 von 2 Abschnitten ausgewählt.')).toBeInTheDocument()
  })

  it('legt die ausgewählten Abschnitte an und schließt danach', async () => {
    const onImport = vi.fn().mockResolvedValue(undefined)
    const onClose = vi.fn()
    renderDialog({}, onImport, onClose)

    fireEvent.click(screen.getByRole('button', { name: '2 Ideen anlegen' }))

    await waitFor(() => expect(onClose).toHaveBeenCalled())
    expect(onImport).toHaveBeenCalledWith([
      { title: 'Anmeldung', description: 'Nutzer meldet sich mit E-Mail an.' },
      { title: 'Registrierung', description: 'Neue Nutzer legen ein Konto an.' },
    ])
  })

  it('schickt eine leere Beschreibung als null und zeigt das in der Vorschau an', async () => {
    const onImport = vi.fn().mockResolvedValue(undefined)
    renderDialog({ markdown: '## Nur ein Titel' }, onImport)

    expect(screen.getByText('(keine Beschreibung)')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '1 Idee anlegen' }))

    await waitFor(() =>
      expect(onImport).toHaveBeenCalledWith([{ title: 'Nur ein Titel', description: null }]),
    )
  })

  it('sperrt den Anlegen-Knopf während des laufenden Imports', async () => {
    let release!: () => void
    const onImport = vi.fn().mockReturnValue(
      new Promise<void>((resolve) => {
        release = resolve
      }),
    )
    renderDialog({}, onImport)

    const button = screen.getByRole('button', { name: '2 Ideen anlegen' })
    fireEvent.click(button)
    await waitFor(() => expect(button).toBeDisabled())

    release()
    await waitFor(() => expect(onImport).toHaveBeenCalledTimes(1))
  })

  it('bleibt bei einem Fehler offen und meldet ihn', async () => {
    const onClose = vi.fn()
    renderDialog({}, () => Promise.reject(new Error('kaputt')), onClose)

    fireEvent.click(screen.getByRole('button', { name: '2 Ideen anlegen' }))

    expect(await screen.findByText(/konnten nicht angelegt werden/)).toBeInTheDocument()
    expect(onClose).not.toHaveBeenCalled()
  })

  it('erklärt eine leere Datei, statt eine Riesenkarte anzubieten', () => {
    renderDialog({ markdown: '   \n\n' })

    expect(screen.getByText('Die Datei ist leer — es gibt nichts einzulesen.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /anlegen/ })).not.toBeInTheDocument()
  })

  it('erklärt eine Datei ohne passende Überschrift und nennt die andere Ebene', () => {
    renderDialog({ markdown: '# Nur eine H1\nText dazu.' })

    expect(
      screen.getByText(/Keine Überschrift der Ebene H2 gefunden.*H1 umschalten/s),
    ).toBeInTheDocument()
  })

  it('nennt umgekehrt H2 als Ausweg, wenn auf H1 umgeschaltet keine Überschrift bleibt', () => {
    renderDialog({ markdown: '## Nur eine H2\nText dazu.' })

    fireEvent.change(screen.getByLabelText('Trennende Überschriftenebene'), {
      target: { value: '1' },
    })

    expect(
      screen.getByText(/Keine Überschrift der Ebene H1 gefunden.*H2 umschalten/s),
    ).toBeInTheDocument()
  })

  it('markiert gekürzte Titel und Beschreibungen in der Vorschau', () => {
    const title = 'T'.repeat(MAX_TITLE_LENGTH + 5)
    const body = 'B'.repeat(MAX_DESCRIPTION_LENGTH + 5)
    renderDialog({ markdown: `## ${title}\n${body}` })

    expect(screen.getByText('Titel gekürzt')).toBeInTheDocument()
    expect(screen.getByText('Beschreibung gekürzt')).toBeInTheDocument()
  })

  it('kürzt lange Beschreibungen in der Vorschau auf eine Zeile', () => {
    renderDialog({ markdown: `## Titel\n${'x'.repeat(300)}` })

    expect(screen.getByText(`${'x'.repeat(160)}…`)).toBeInTheDocument()
  })

  it('warnt vor dem Absenden, wenn mehr Abschnitte als erlaubt ausgewählt sind', () => {
    const markdown = Array.from(
      { length: MAX_IDEAS_PER_IMPORT + 1 },
      (_, i) => `## Abschnitt ${i}\nText`,
    ).join('\n')
    renderDialog({ markdown })

    expect(
      screen.getByText(
        `201 Abschnitte ausgewählt — es lassen sich höchstens ${MAX_IDEAS_PER_IMPORT} auf einmal anlegen. Bitte einzelne Abschnitte abwählen.`,
      ),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '201 Ideen anlegen' })).toBeDisabled()
  })

  it('lässt den Dialog über Abbrechen schließen', () => {
    const onClose = vi.fn()
    renderDialog({}, () => Promise.resolve(), onClose)

    fireEvent.click(screen.getByRole('button', { name: 'Abbrechen' }))

    expect(onClose).toHaveBeenCalled()
  })
})
