import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import { abnahmeKommentar, type Szenario } from '../lib/abnahmeSzenarien'
import { MAX_TEXT_LENGTH, tooLongMessage } from '../lib/textLimits'
import { AbnahmeSzenarienDialog } from './AbnahmeSzenarienDialog'

const SZENARIEN: Szenario[] = [
  { name: 'Anmelden', schritte: ['Angenommen ein Konto', 'Wenn ich mich anmelde', 'Dann sehe ich das Board'] },
  { name: 'Abmelden', schritte: ['Angenommen ich bin angemeldet', 'Wenn ich mich abmelde', 'Dann sehe ich die Anmeldung'] },
  { name: 'Passwort vergessen', schritte: ['Wenn ich den Link öffne', 'Dann kommt eine Mail'] },
]

const KNOPF = 'Ergebnis an den Fachplan schreiben'

function renderDialog(
  szenarien: Szenario[] = SZENARIEN,
  onAbschicken: (text: string) => Promise<void> = vi.fn(() => Promise.resolve()),
) {
  const onClose = vi.fn()
  const user = userEvent.setup()
  const view = render(
    <AbnahmeSzenarienDialog open titel="Login" szenarien={szenarien} onClose={onClose} onAbschicken={onAbschicken} />,
  )
  return { onClose, onAbschicken, user, view }
}

const hauptdialog = () => screen.getByRole('dialog', { name: 'Abnahme: Login' })
const szenario = (name: string) => screen.getByRole('region', { name })
const vermerkGruppe = (nummer: number) => screen.getByRole('group', { name: `Vermerk zu Szenario ${nummer}` })
const status = () => screen.getByRole('status')

async function vermerke(user: ReturnType<typeof userEvent.setup>, nummer: number, urteil: 'okay' | 'nicht okay') {
  await user.click(within(vermerkGruppe(nummer)).getByRole('button', { name: urteil }))
}

describe('AbnahmeSzenarienDialog', () => {
  it('trägt den Titel „Abnahme: <titel>“', () => {
    renderDialog()
    expect(hauptdialog()).toBeInTheDocument()
  })

  it('rendert nichts, solange er geschlossen ist', () => {
    render(
      <AbnahmeSzenarienDialog
        open={false}
        titel="Login"
        szenarien={SZENARIEN}
        onClose={vi.fn()}
        onAbschicken={vi.fn(() => Promise.resolve())}
      />,
    )
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('zeigt die Szenarien nummeriert und ihre Schritte in Reihenfolge', () => {
    renderDialog()
    const ueberschriften = within(hauptdialog())
      .getAllByRole('heading', { level: 3 })
      .map((h) => h.textContent)
    expect(ueberschriften).toEqual(['1. Anmelden', '2. Abmelden', '3. Passwort vergessen'])
    const schritte = within(szenario('1. Anmelden'))
      .getAllByRole('listitem')
      .map((li) => li.textContent)
    expect(schritte).toEqual(['Angenommen ein Konto', 'Wenn ich mich anmelde', 'Dann sehe ich das Board'])
  })

  it('hebt das Schlüsselwort eines Schritts hervor', () => {
    renderDialog()
    const erster = within(szenario('1. Anmelden')).getAllByRole('listitem')[0]
    const wort = within(erster).getByText('Angenommen')
    expect(wort.tagName).toBe('STRONG')
    expect(wort.textContent).toBe('Angenommen')
  })

  it('zeigt einen Schritt ohne Rest nur mit dem Schlüsselwort', () => {
    renderDialog([{ name: 'Kurz', schritte: ['Dann'] }])
    const item = within(szenario('1. Kurz')).getByRole('listitem')
    expect(item.textContent).toBe('Dann')
    expect(within(item).getByText('Dann').tagName).toBe('STRONG')
  })

  it('setzt den Vermerk über die Knopfgruppe des Szenarios', async () => {
    const { user } = renderDialog()
    const okay = within(vermerkGruppe(1)).getByRole('button', { name: 'okay' })
    expect(okay).toHaveAttribute('aria-pressed', 'false')
    await user.click(okay)
    expect(okay).toHaveAttribute('aria-pressed', 'true')
    expect(within(vermerkGruppe(2)).getByRole('button', { name: 'okay' })).toHaveAttribute('aria-pressed', 'false')
  })

  it('behält den Vermerk, wenn der gewählte Knopf erneut gedrückt wird', async () => {
    const { user } = renderDialog()
    await vermerke(user, 1, 'okay')
    await vermerke(user, 1, 'okay')
    expect(within(vermerkGruppe(1)).getByRole('button', { name: 'okay' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('zeigt das Notizfeld nur bei „nicht okay“, einzeilig und auf 500 Zeichen begrenzt', async () => {
    const { user } = renderDialog()
    expect(screen.queryByRole('textbox', { name: 'Notiz (optional)' })).not.toBeInTheDocument()
    await vermerke(user, 2, 'nicht okay')
    const notiz = within(szenario('2. Abmelden')).getByRole('textbox', { name: 'Notiz (optional)' })
    expect(notiz.tagName).toBe('INPUT')
    expect(notiz).toHaveAttribute('maxLength', '500')
    expect(within(szenario('1. Anmelden')).queryByRole('textbox')).not.toBeInTheDocument()
    await vermerke(user, 2, 'okay')
    expect(screen.queryByRole('textbox', { name: 'Notiz (optional)' })).not.toBeInTheDocument()
  })

  it('behält die Notiz beim Hin- und Herschalten des Vermerks', async () => {
    const { user } = renderDialog()
    await vermerke(user, 1, 'nicht okay')
    await user.type(within(szenario('1. Anmelden')).getByRole('textbox'), 'hakt')
    await vermerke(user, 1, 'okay')
    await vermerke(user, 1, 'nicht okay')
    expect(within(szenario('1. Anmelden')).getByRole('textbox')).toHaveValue('hakt')
  })

  it('fasst den Stand zusammen und nennt offene Szenarien', async () => {
    const { user } = renderDialog()
    expect(status()).toHaveTextContent('0 von 3 okay — noch offen: 3')
    await vermerke(user, 1, 'okay')
    expect(status()).toHaveTextContent('1 von 3 okay — noch offen: 2')
    await vermerke(user, 3, 'nicht okay')
    expect(status()).toHaveTextContent('1 von 3 okay — nicht okay: 3. Passwort vergessen — noch offen: 1')
    await vermerke(user, 2, 'nicht okay')
    expect(status().textContent).toBe('1 von 3 okay — nicht okay: 2. Abmelden, 3. Passwort vergessen')
  })

  it('meldet „Alle Szenarien okay“, wenn alle okay sind', async () => {
    const { user } = renderDialog()
    for (const nummer of [1, 2, 3]) await vermerke(user, nummer, 'okay')
    expect(status().textContent).toBe('Alle Szenarien okay')
  })

  it('gibt den Knopf erst frei, wenn jedes Szenario einen Vermerk trägt', async () => {
    const { user } = renderDialog()
    const knopf = screen.getByRole('button', { name: KNOPF })
    expect(knopf).toBeDisabled()
    await vermerke(user, 1, 'okay')
    await vermerke(user, 2, 'nicht okay')
    expect(knopf).toBeDisabled()
    await vermerke(user, 3, 'okay')
    expect(knopf).toBeEnabled()
  })

  it('schickt genau den Text aus abnahmeKommentar ab und schließt danach', async () => {
    const onAbschicken = vi.fn(() => Promise.resolve())
    const { user, onClose } = renderDialog(SZENARIEN, onAbschicken)
    await vermerke(user, 1, 'okay')
    await vermerke(user, 2, 'nicht okay')
    await user.type(within(szenario('2. Abmelden')).getByRole('textbox'), 'Knopf fehlt')
    await vermerke(user, 3, 'okay')
    await user.click(screen.getByRole('button', { name: KNOPF }))
    expect(onAbschicken).toHaveBeenCalledTimes(1)
    expect(onAbschicken).toHaveBeenCalledWith(
      abnahmeKommentar(SZENARIEN, [
        { urteil: 'okay', notiz: '' },
        { urteil: 'nicht okay', notiz: 'Knopf fehlt' },
        { urteil: 'okay', notiz: '' },
      ]),
    )
    await waitFor(() => expect(onClose).toHaveBeenCalledTimes(1))
  })

  it('sperrt Knopf und Schließen während des Sendens', async () => {
    let erfuellen: () => void = () => undefined
    const onAbschicken = vi.fn(() => new Promise<void>((resolve) => (erfuellen = resolve)))
    const { user, onClose } = renderDialog([SZENARIEN[0]], onAbschicken)
    await vermerke(user, 1, 'okay')
    const knopf = screen.getByRole('button', { name: KNOPF })
    await user.click(knopf)
    expect(knopf).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Schließen' })).toBeDisabled()
    fireEvent.keyDown(hauptdialog(), { key: 'Escape' })
    expect(screen.queryByRole('dialog', { name: 'Vermerke verwerfen?' })).not.toBeInTheDocument()
    expect(onClose).not.toHaveBeenCalled()
    erfuellen()
    await waitFor(() => expect(onClose).toHaveBeenCalledTimes(1))
  })

  it('setzt die Vermerke nach dem Abschicken zurück', async () => {
    const { user, view } = renderDialog([SZENARIEN[0]])
    await vermerke(user, 1, 'okay')
    await user.click(screen.getByRole('button', { name: KNOPF }))
    await waitFor(() => expect(screen.getByRole('button', { name: KNOPF })).toBeDisabled())
    view.rerender(
      <AbnahmeSzenarienDialog
        open
        titel="Login"
        szenarien={[SZENARIEN[0]]}
        onClose={vi.fn()}
        onAbschicken={vi.fn(() => Promise.resolve())}
      />,
    )
    expect(within(vermerkGruppe(1)).getByRole('button', { name: 'okay' })).toHaveAttribute('aria-pressed', 'false')
  })

  it('zeigt einen Fehler beim Senden als Alert und lässt die Vermerke stehen', async () => {
    const onAbschicken = vi.fn(() => Promise.reject(new ApiError(403, 'Forbidden', undefined, 'Keine Berechtigung.')))
    const { user, onClose } = renderDialog([SZENARIEN[0]], onAbschicken)
    await vermerke(user, 1, 'okay')
    await user.click(screen.getByRole('button', { name: KNOPF }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Keine Berechtigung.')
    expect(onClose).not.toHaveBeenCalled()
    expect(within(vermerkGruppe(1)).getByRole('button', { name: 'okay' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: KNOPF })).toBeEnabled()
  })

  it('nennt ohne Detail den Ersatztext und räumt den Fehler beim nächsten Versuch', async () => {
    let versuch = 0
    const onAbschicken = vi.fn(() => (versuch++ === 0 ? Promise.reject(new Error('Netz')) : Promise.resolve()))
    const { user, onClose } = renderDialog([SZENARIEN[0]], onAbschicken)
    await vermerke(user, 1, 'okay')
    await user.click(screen.getByRole('button', { name: KNOPF }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Das Ergebnis konnte nicht geschrieben werden.')
    await user.click(screen.getByRole('button', { name: KNOPF }))
    await waitFor(() => expect(onClose).toHaveBeenCalledTimes(1))
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('sperrt den Knopf bei zu langem Kommentar und zeigt die Längenmeldung', async () => {
    const lang: Szenario[] = [{ name: 'x'.repeat(MAX_TEXT_LENGTH), schritte: ['Dann geht es'] }]
    const { user } = renderDialog(lang)
    const vorher = abnahmeKommentar(lang, [undefined])
    expect(screen.getByText(tooLongMessage(vorher.length))).toBeInTheDocument()
    await vermerke(user, 1, 'okay')
    const text = abnahmeKommentar(lang, [{ urteil: 'okay', notiz: '' }])
    expect(screen.getByText(tooLongMessage(text.length))).toBeInTheDocument()
    expect(screen.getByRole('button', { name: KNOPF })).toBeDisabled()
  })

  it('zeigt bei passender Länge keine Längenmeldung', () => {
    renderDialog()
    expect(screen.queryByText(/ Zeichen$/)).not.toBeInTheDocument()
  })

  it('schließt ohne Vermerke direkt, ohne Rückfrage', async () => {
    const { user, onClose } = renderDialog()
    await user.click(screen.getByRole('button', { name: 'Schließen' }))
    expect(onClose).toHaveBeenCalledTimes(1)
    expect(screen.queryByRole('dialog', { name: 'Vermerke verwerfen?' })).not.toBeInTheDocument()
  })

  it('schließt ohne Vermerke auch über Escape direkt', async () => {
    const { user, onClose } = renderDialog()
    await user.keyboard('{Escape}')
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('fragt beim Schließen mit Vermerken nach, Fokus auf Abbrechen, Escape bricht ab', async () => {
    const { user, onClose } = renderDialog()
    await vermerke(user, 1, 'okay')
    await user.click(screen.getByRole('button', { name: 'Schließen' }))
    const rueckfrage = await screen.findByRole('dialog', { name: 'Vermerke verwerfen?' })
    await waitFor(() => expect(within(rueckfrage).getByRole('button', { name: 'Abbrechen' })).toHaveFocus())
    await user.keyboard('{Escape}')
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Vermerke verwerfen?' })).not.toBeInTheDocument())
    expect(onClose).not.toHaveBeenCalled()
    expect(within(vermerkGruppe(1)).getByRole('button', { name: 'okay' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('bricht die Rückfrage über „Abbrechen“ ab', async () => {
    const { user, onClose } = renderDialog()
    await vermerke(user, 1, 'nicht okay')
    await user.click(screen.getByRole('button', { name: 'Schließen' }))
    const rueckfrage = await screen.findByRole('dialog', { name: 'Vermerke verwerfen?' })
    await user.click(within(rueckfrage).getByRole('button', { name: 'Abbrechen' }))
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Vermerke verwerfen?' })).not.toBeInTheDocument())
    expect(onClose).not.toHaveBeenCalled()
  })

  it('verwirft nach Bestätigung die Vermerke und schließt', async () => {
    const { user, onClose, view } = renderDialog()
    await vermerke(user, 1, 'okay')
    fireEvent.keyDown(hauptdialog(), { key: 'Escape' })
    const rueckfrage = await screen.findByRole('dialog', { name: 'Vermerke verwerfen?' })
    expect(rueckfrage).toHaveAccessibleDescription('Die Vermerke dieser Abnahme gehen verloren.')
    await user.click(within(rueckfrage).getByRole('button', { name: 'Verwerfen' }))
    expect(onClose).toHaveBeenCalledTimes(1)
    view.rerender(
      <AbnahmeSzenarienDialog
        open
        titel="Login"
        szenarien={SZENARIEN}
        onClose={vi.fn()}
        onAbschicken={vi.fn(() => Promise.resolve())}
      />,
    )
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Vermerke verwerfen?' })).not.toBeInTheDocument())
    expect(within(vermerkGruppe(1)).getByRole('button', { name: 'okay' })).toHaveAttribute('aria-pressed', 'false')
  })

  it('lässt sich über die Tastatur bedienen', async () => {
    const onAbschicken = vi.fn(() => Promise.resolve())
    const { user } = renderDialog([SZENARIEN[0]], onAbschicken)
    await user.tab()
    await user.tab()
    expect(within(vermerkGruppe(1)).getByRole('button', { name: 'nicht okay' })).toHaveFocus()
    await user.keyboard('{Enter}')
    await user.tab()
    expect(screen.getByRole('textbox', { name: 'Notiz (optional)' })).toHaveFocus()
    await user.keyboard('zu langsam')
    await user.tab()
    await user.tab()
    expect(screen.getByRole('button', { name: KNOPF })).toHaveFocus()
    await user.keyboard('{Enter}')
    expect(onAbschicken).toHaveBeenCalledWith(abnahmeKommentar([SZENARIEN[0]], [{ urteil: 'nicht okay', notiz: 'zu langsam' }]))
    await waitFor(() => expect(screen.getByRole('button', { name: KNOPF })).toBeDisabled())
  })
})
