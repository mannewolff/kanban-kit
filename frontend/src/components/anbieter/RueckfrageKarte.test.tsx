import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { AuslieferungZuletzt } from '../../api/auslieferung'
import { ApiError } from '../../api/client'
import type { AntwortAuftrag, Rueckfrage, RueckfrageEintrag } from '../../api/rueckfragen'
import { renderMitSprache } from '../../i18n/renderMitSprache'
import { RueckfrageKarte } from './RueckfrageKarte'

function frage(nr: number, optionen: string[], empfohlen: number, ueber: Partial<RueckfrageEintrag> = {}): RueckfrageEintrag {
  return {
    nr,
    art: 'FRAGE',
    frage: `Frage ${nr}?`,
    empfehlung: optionen[empfohlen - 1] ?? `Empfehlung ${nr}`,
    grund: `Grund ${nr}`,
    einarbeitung: '',
    optionen: optionen.map((text, i) => ({ text, hinweis: null, empfohlen: i + 1 === empfohlen })),
    zustand: 'OFFEN',
    wahl: null,
    text: null,
    empfehlungUebernommen: false,
    ...ueber,
  }
}

function rueckfrage(eintraege: RueckfrageEintrag[], ueber: Partial<Rueckfrage> = {}): Rueckfrage {
  return {
    kommentarId: 55,
    angelegtAm: '2026-11-22T10:00:00Z',
    zustand: 'OFFEN',
    vorschau: null,
    commit: null,
    eintraege,
    ...ueber,
  }
}

const DREI = [
  frage(1, ['Blau', 'Rot'], 1),
  frage(2, ['Klein', 'Mittel', 'Groß'], 2),
  frage(3, ['Ja', 'Nein'], 2),
]

function apiMit(antwort: Rueckfrage | Error) {
  return {
    antworten: vi.fn<(cardId: number, kommentarId: number, auftrag: AntwortAuftrag) => Promise<Rueckfrage>>(() =>
      antwort instanceof Error ? Promise.reject(antwort) : Promise.resolve(antwort),
    ),
  }
}

/** Die Gruppe der Möglichkeiten einer Frage. */
function gruppe(nr: number) {
  return screen.getByRole('group', { name: `Möglichkeiten zu: Frage ${nr}?` })
}

describe('RueckfrageKarte', () => {
  it('Szenario: Fragen der KI per Klick beantworten', async () => {
    const beantwortet = rueckfrage(
      DREI.map((f) => ({
        ...f,
        zustand: 'BEANTWORTET' as const,
        wahl: f.optionen.findIndex((o) => o.empfohlen) + 1,
        empfehlungUebernommen: true,
      })),
      { zustand: 'BEANTWORTET' },
    )
    const api = apiMit(beantwortet)
    const onBeantwortet = vi.fn()
    renderMitSprache(<RueckfrageKarte cardId={100} rueckfrage={rueckfrage(DREI)} api={api} onBeantwortet={onBeantwortet} />)

    // Angenommen: drei Fragen mit Empfehlung, Begründung und Knöpfen je Möglichkeit.
    for (const nr of [1, 2, 3]) {
      expect(screen.getByText(`Frage ${nr}?`)).toBeInTheDocument()
      expect(screen.getByText(`Begründung: Grund ${nr}`)).toBeInTheDocument()
    }
    expect(screen.getByText('Empfehlung: Mittel')).toBeInTheDocument()
    expect(within(gruppe(2)).getAllByRole('button')).toHaveLength(3)

    // Wenn der Kunde „Alles übernehmen“ drückt …
    await userEvent.click(screen.getByRole('button', { name: 'Alles übernehmen' }))

    // … steht an der Karte ein Kommentar „Antwort“ (der Server schreibt ihn aus diesem Auftrag) …
    expect(api.antworten).toHaveBeenCalledWith(100, 55, { ja: true })
    expect(onBeantwortet).toHaveBeenCalledWith(beantwortet)
    // … die Karte zeigt zu jeder Frage die Empfehlung als gewählt …
    expect(await screen.findByText('Antwort übermittelt, die KI arbeitet daran')).toBeInTheDocument()
    expect(within(gruppe(1)).getByRole('button', { name: 'Blau' })).toHaveAttribute('aria-pressed', 'true')
    expect(within(gruppe(1)).getByRole('button', { name: 'Rot' })).toHaveAttribute('aria-pressed', 'false')
    expect(within(gruppe(2)).getByRole('button', { name: 'Mittel' })).toHaveAttribute('aria-pressed', 'true')
    expect(within(gruppe(3)).getByRole('button', { name: 'Nein' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getAllByText('Empfehlung übernommen')).toHaveLength(3)
    // … und es gibt nichts mehr zu klicken.
    expect(screen.queryByRole('button', { name: 'Alles übernehmen' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Blau' })).toBeDisabled()
  })

  it('wählt eine Möglichkeit einer Frage per Knopf', async () => {
    const api = apiMit(rueckfrage([{ ...DREI[0], zustand: 'BEANTWORTET', wahl: 2 }, DREI[1], DREI[2]]))
    renderMitSprache(<RueckfrageKarte cardId={100} rueckfrage={rueckfrage(DREI)} api={api} />)

    await userEvent.click(within(gruppe(1)).getByRole('button', { name: 'Rot' }))

    expect(api.antworten).toHaveBeenCalledWith(100, 55, { antworten: [{ nr: 1, wahl: 2 }] })
    expect(await screen.findByText('Gewählt: Rot')).toBeInTheDocument()
    expect(within(gruppe(1)).getByRole('button', { name: 'Rot' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByText('Antwort übermittelt, die KI arbeitet daran')).toBeInTheDocument()
    // Die Rückfrage bleibt offen: Die übrigen Fragen lassen sich weiter beantworten.
    expect(within(gruppe(2)).getByRole('button', { name: 'Klein' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Alles übernehmen' })).toBeInTheDocument()
  })

  it('stellt eine Frage mit „Später“ zurück, die Rückfrage bleibt offen', async () => {
    const api = apiMit(rueckfrage([DREI[0], { ...DREI[1], zustand: 'ZURUECKGESTELLT' }, DREI[2]]))
    renderMitSprache(<RueckfrageKarte cardId={100} rueckfrage={rueckfrage(DREI)} api={api} />)

    const zweite = screen.getByTestId('rueckfrage-eintrag-2')
    await userEvent.click(within(zweite).getByRole('button', { name: 'Später' }))

    expect(api.antworten).toHaveBeenCalledWith(100, 55, { antworten: [{ nr: 2, zurueckstellen: true }] })
    expect(await within(zweite).findByText(/^Zurückgestellt/)).toBeInTheDocument()
    expect(within(gruppe(2)).getByRole('button', { name: 'Mittel' })).toBeEnabled()
    expect(within(gruppe(2)).getByRole('button', { name: 'Mittel' })).toHaveAttribute('aria-pressed', 'false')
  })

  it('schickt mit „Anders entscheiden“ eine eigene Antwort', async () => {
    const api = apiMit(rueckfrage([{ ...DREI[0], zustand: 'BEANTWORTET', text: 'Lieber grün' }, DREI[1], DREI[2]]))
    renderMitSprache(<RueckfrageKarte cardId={100} rueckfrage={rueckfrage(DREI)} api={api} />)

    const erste = screen.getByTestId('rueckfrage-eintrag-1')
    await userEvent.click(within(erste).getByRole('button', { name: 'Anders entscheiden' }))
    const absenden = within(erste).getByRole('button', { name: 'Absenden' })
    // Ohne Text gibt es nichts abzusenden.
    expect(absenden).toBeDisabled()
    await userEvent.type(within(erste).getByLabelText('Eigene Antwort'), '  Lieber grün ')
    await userEvent.click(absenden)

    expect(api.antworten).toHaveBeenCalledWith(100, 55, { antworten: [{ nr: 1, text: 'Lieber grün' }] })
    expect(await within(erste).findByText('Eigene Antwort: Lieber grün')).toBeInTheDocument()
    expect(within(erste).queryByLabelText('Eigene Antwort')).not.toBeInTheDocument()
  })

  it('schließt das Feld der eigenen Antwort mit „Abbrechen“, ohne etwas zu senden', async () => {
    const api = apiMit(rueckfrage(DREI))
    renderMitSprache(<RueckfrageKarte cardId={100} rueckfrage={rueckfrage(DREI)} api={api} />)

    const erste = screen.getByTestId('rueckfrage-eintrag-1')
    await userEvent.click(within(erste).getByRole('button', { name: 'Anders entscheiden' }))
    await userEvent.click(within(erste).getByRole('button', { name: 'Abbrechen' }))

    expect(within(erste).queryByLabelText('Eigene Antwort')).not.toBeInTheDocument()
    expect(api.antworten).not.toHaveBeenCalled()
  })

  it('lässt bei einem Weg genau eine Wahl', async () => {
    const weg: RueckfrageEintrag = { ...frage(1, ['Kleine Lösung', 'Große Lösung'], 1), art: 'WEG', frage: 'Welcher Weg?' }
    const api = apiMit(rueckfrage([{ ...weg, zustand: 'BEANTWORTET', wahl: 2 }], { zustand: 'BEANTWORTET' }))
    renderMitSprache(<RueckfrageKarte cardId={100} rueckfrage={rueckfrage([weg])} api={api} />)

    expect(screen.getByText('Genau ein Weg ist zu wählen.')).toBeInTheDocument()
    // Ein Weg kennt keine eigene Antwort.
    expect(screen.queryByRole('button', { name: 'Anders entscheiden' })).not.toBeInTheDocument()
    const wege = screen.getByRole('group', { name: 'Möglichkeiten zu: Welcher Weg?' })
    await userEvent.click(within(wege).getByRole('button', { name: 'Große Lösung' }))

    expect(api.antworten).toHaveBeenCalledWith(100, 55, { weg: 2 })
    await waitFor(() =>
      expect(within(wege).getByRole('button', { name: 'Große Lösung' })).toHaveAttribute('aria-pressed', 'true'),
    )
    expect(within(wege).getByRole('button', { name: 'Kleine Lösung' })).toHaveAttribute('aria-pressed', 'false')
  })

  it('bietet bei einer Frage ohne Möglichkeiten nur eigene Antwort und „Später“', () => {
    renderMitSprache(<RueckfrageKarte cardId={100} rueckfrage={rueckfrage([frage(1, [], 0, { empfehlung: 'So' })])} api={apiMit(new Error())} />)

    expect(screen.queryByRole('group', { name: 'Möglichkeiten zu: Frage 1?' })).not.toBeInTheDocument()
    expect(screen.getByText('Empfehlung: So')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Anders entscheiden' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Später' })).toBeInTheDocument()
  })

  it('nennt den Grund, wenn die Antwort scheitert, und lässt die Rückfrage offen', async () => {
    const api = apiMit(new ApiError(400, 'Bad Request', undefined, 'Die Antwort passt nicht.'))
    renderMitSprache(<RueckfrageKarte cardId={100} rueckfrage={rueckfrage(DREI)} api={api} />)

    await userEvent.click(screen.getByRole('button', { name: 'Alles übernehmen' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Die Antwort ließ sich nicht übermitteln: Die Antwort passt nicht.')
    expect(screen.queryByText('Antwort übermittelt, die KI arbeitet daran')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Alles übernehmen' })).toBeEnabled()
  })

  it('zeigt an einer beantworteten Frage ohne Wahl und ohne eigene Antwort keinen Satz', () => {
    renderMitSprache(
      <RueckfrageKarte
        cardId={100}
        rueckfrage={rueckfrage([{ ...DREI[0], zustand: 'BEANTWORTET' }], { zustand: 'BEANTWORTET' })}
        api={apiMit(new Error())}
      />,
    )

    expect(screen.queryByText(/^Gewählt/)).not.toBeInTheDocument()
    expect(screen.queryByText('Empfehlung übernommen')).not.toBeInTheDocument()
    expect(screen.queryByRole('group', { name: 'Möglichkeiten zu: Frage 1?' })).not.toBeInTheDocument()
  })

  it('sperrt alle Knöpfe in der Ansicht als jemand anderes', () => {
    renderMitSprache(<RueckfrageKarte cardId={100} rueckfrage={rueckfrage(DREI)} gesperrt api={apiMit(new Error())} />)

    expect(screen.getByRole('button', { name: 'Alles übernehmen' })).toBeDisabled()
    expect(within(gruppe(1)).getByRole('button', { name: 'Blau' })).toBeDisabled()
    expect(screen.getAllByRole('button', { name: 'Später' })[0]).toBeDisabled()
    expect(screen.getAllByRole('button', { name: 'Anders entscheiden' })[0]).toBeDisabled()
  })

  describe('Freigabe zum Veröffentlichen', () => {
    const FREIGABE = rueckfrage([frage(1, ['Freigeben', 'Noch nicht'], 1, { frage: 'Soll der Stand veröffentlicht werden?' })], {
      vorschau: 'https://vorschau.example.org/42',
      commit: 'abc1234',
    })

    it('zeigt „Vorschau ansehen“ und gibt per Knopf genau diesen Stand frei: „Freigabe erteilt, wird veröffentlicht“', async () => {
      const api = apiMit({
        ...FREIGABE,
        zustand: 'BEANTWORTET',
        eintraege: FREIGABE.eintraege.map((e) => ({ ...e, zustand: 'BEANTWORTET' as const, wahl: 1, empfehlungUebernommen: true })),
      })
      renderMitSprache(<RueckfrageKarte cardId={100} rueckfrage={FREIGABE} api={api} />)

      const link = screen.getByRole('link', { name: 'Vorschau ansehen' })
      expect(link).toHaveAttribute('href', 'https://vorschau.example.org/42')
      expect(link).toHaveAttribute('target', '_blank')
      expect(link).toHaveAttribute('rel', 'noopener noreferrer')
      expect(screen.getByText('Soll der Stand veröffentlicht werden?')).toBeInTheDocument()
      // Zur Freigabe gibt es genau einen Knopf, keine Einzelwahl.
      expect(screen.queryByRole('button', { name: 'Alles übernehmen' })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Später' })).not.toBeInTheDocument()

      await userEvent.click(screen.getByRole('button', { name: 'Veröffentlichen freigeben' }))

      expect(api.antworten).toHaveBeenCalledWith(100, 55, { ja: true, freigeben: true })
      expect(await screen.findByText('Freigabe erteilt, wird veröffentlicht')).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Veröffentlichen freigeben' })).not.toBeInTheDocument()
      expect(screen.queryByText('Antwort übermittelt, die KI arbeitet daran')).not.toBeInTheDocument()
    })

    it('zeigt bei einer überholten Rückfrage „Neuere Vorschau vorhanden“ ohne Knopf', () => {
      renderMitSprache(<RueckfrageKarte cardId={100} rueckfrage={{ ...FREIGABE, zustand: 'UEBERHOLT' }} api={apiMit(new Error())} />)

      expect(screen.getByText('Neuere Vorschau vorhanden')).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Veröffentlichen freigeben' })).not.toBeInTheDocument()
      expect(screen.queryByText('Freigabe erteilt, wird veröffentlicht')).not.toBeInTheDocument()
    })

    it('zeigt „Neuere Vorschau vorhanden“, wenn der Server die Freigabe als überholt abweist', async () => {
      const api = apiMit(new ApiError(409, 'Conflict', undefined, 'überholt'))
      renderMitSprache(<RueckfrageKarte cardId={100} rueckfrage={FREIGABE} api={api} />)

      await userEvent.click(screen.getByRole('button', { name: 'Veröffentlichen freigeben' }))

      expect(await screen.findByText('Neuere Vorschau vorhanden')).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Veröffentlichen freigeben' })).not.toBeInTheDocument()
      expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    })

    it('nennt einen anderen Fehler der Freigabe und bietet den Knopf weiter an', async () => {
      const api = apiMit(new ApiError(500, 'Server Error', undefined, 'Kaputt.'))
      renderMitSprache(<RueckfrageKarte cardId={100} rueckfrage={FREIGABE} api={api} />)

      await userEvent.click(screen.getByRole('button', { name: 'Veröffentlichen freigeben' }))

      expect(await screen.findByRole('alert')).toHaveTextContent('Kaputt.')
      expect(screen.getByRole('button', { name: 'Veröffentlichen freigeben' })).toBeEnabled()
      expect(screen.queryByText('Neuere Vorschau vorhanden')).not.toBeInTheDocument()
    })

    it('deutet ein 409 ohne Freigabe nicht als überholt', async () => {
      const api = apiMit(new ApiError(409, 'Conflict', undefined, 'Doppelt.'))
      renderMitSprache(<RueckfrageKarte cardId={100} rueckfrage={rueckfrage(DREI)} api={api} />)

      await userEvent.click(screen.getByRole('button', { name: 'Alles übernehmen' }))

      expect(await screen.findByRole('alert')).toHaveTextContent('Doppelt.')
    })

    it('zeigt nach der Veröffentlichung „Veröffentlicht unter …“ mit der Adresse', () => {
      const zuletzt: AuslieferungZuletzt = {
        vorschau: null,
        veroeffentlichung: { url: 'https://www.example.org', revision: 'abc1234', finishedAt: '2026-11-22T12:00:00Z' },
      }
      renderMitSprache(
        <RueckfrageKarte cardId={100} rueckfrage={{ ...FREIGABE, zustand: 'BEANTWORTET' }} zuletzt={zuletzt} api={apiMit(new Error())} />,
      )

      expect(screen.getByText(/Veröffentlicht unter/)).toBeInTheDocument()
      expect(screen.getByRole('link', { name: 'https://www.example.org' })).toHaveAttribute('href', 'https://www.example.org')
      expect(screen.queryByText('Freigabe erteilt, wird veröffentlicht')).not.toBeInTheDocument()
    })

    it('zeigt eine Veröffentlichung eines anderen Stands nicht an der Freigabe', () => {
      const zuletzt: AuslieferungZuletzt = {
        vorschau: null,
        veroeffentlichung: { url: 'https://www.example.org', revision: 'fff0000', finishedAt: '2026-11-22T12:00:00Z' },
      }
      renderMitSprache(
        <RueckfrageKarte cardId={100} rueckfrage={{ ...FREIGABE, zustand: 'BEANTWORTET' }} zuletzt={zuletzt} api={apiMit(new Error())} />,
      )

      expect(screen.queryByText(/Veröffentlicht unter/)).not.toBeInTheDocument()
      expect(screen.getByText('Freigabe erteilt, wird veröffentlicht')).toBeInTheDocument()
    })

    it('zeigt eine Veröffentlichung ohne Adresse nicht als Link', () => {
      const zuletzt: AuslieferungZuletzt = {
        vorschau: null,
        veroeffentlichung: { url: null, revision: 'abc1234', finishedAt: '2026-11-22T12:00:00Z' },
      }
      renderMitSprache(
        <RueckfrageKarte cardId={100} rueckfrage={{ ...FREIGABE, zustand: 'BEANTWORTET' }} zuletzt={zuletzt} api={apiMit(new Error())} />,
      )

      expect(screen.queryByText(/Veröffentlicht unter/)).not.toBeInTheDocument()
      expect(screen.getByText('Freigabe erteilt, wird veröffentlicht')).toBeInTheDocument()
    })
  })

  it('spricht englisch, wenn die Oberfläche englisch ist', async () => {
    const eintraege = [
      { ...DREI[0], zustand: 'BEANTWORTET' as const, wahl: 2 },
      { ...DREI[1], zustand: 'BEANTWORTET' as const, text: 'Tiny' },
      DREI[2],
    ]
    const api = apiMit(new ApiError(400, 'Bad Request', undefined, 'Nope.'))
    renderMitSprache(<RueckfrageKarte cardId={100} rueckfrage={rueckfrage(eintraege)} api={api} />, 'en')

    expect(screen.getByText('Recommendation: Blau')).toBeInTheDocument()
    expect(screen.getByText('Reason: Grund 1')).toBeInTheDocument()
    expect(screen.getByRole('group', { name: 'Options for: Frage 3?' })).toBeInTheDocument()
    expect(screen.getByText('Chosen: Rot')).toBeInTheDocument()
    expect(screen.getByText('Own answer: Tiny')).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: 'Later' })).toHaveLength(3)

    await userEvent.click(screen.getByRole('button', { name: 'Accept all' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('The answer could not be sent: Nope.')
  })
})
