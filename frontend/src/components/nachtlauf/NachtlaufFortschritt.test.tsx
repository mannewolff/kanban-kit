import { ThemeProvider } from '@mui/material/styles'
import { fireEvent, render, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type {
  CardRefView,
  ChainProgressView,
  NightRunProgressView,
  PackageProgressView,
} from '../../api/nightRuns'
import type { FortschrittArt } from '../../lib/laufFortschritt'
import { nachtlaufTheme } from '../../nachtlaufDesign'
import { NachtlaufFortschritt } from './NachtlaufFortschritt'

/**
 * Der Fortschritt eines Laufs in der aufgeklappten Kachel (Issue #1377, Plan #1372 E12): Wegleiste
 * je Kette, Kartennummern als Verweise, „unbekannt" und der Hinweis auf eine offene Frage.
 */

const karte = (number: number, title = `Karte ${number}`): CardRefView => ({
  number,
  title,
  boardId: 7,
})

const paket = (number: number, zustand: PackageProgressView['zustand']): PackageProgressView => ({
  karte: karte(number),
  zustand,
})

const kette = (teil: Partial<ChainProgressView> = {}): ChainProgressView => ({
  anforderung: karte(100, 'Die Anforderung'),
  plan: karte(101, 'Der Plan'),
  pakete: [],
  stufen: [
    { stufe: 'PLAN', zustand: 'ERREICHT' },
    { stufe: 'REVIEW', zustand: 'LAEUFT' },
    { stufe: 'PAKETE', zustand: 'OFFEN' },
    { stufe: 'ABDECKUNG', zustand: 'OFFEN' },
  ],
  aktuelleStufe: 'REVIEW',
  endeErreicht: false,
  ...teil,
})

const fortschritt = (teil: Partial<NightRunProgressView> = {}): NightRunProgressView => ({
  zuordnung: 'OK',
  ketten: [],
  pakete: [],
  unbekannt: [],
  offeneFragen: [],
  ...teil,
})

const zeige = (
  f: NightRunProgressView,
  { art = 'KETTE', laeuft = true }: { art?: FortschrittArt; laeuft?: boolean } = {},
) => {
  const onKarteOeffnen = vi.fn()
  const { container } = render(
    <ThemeProvider theme={nachtlaufTheme}>
      <NachtlaufFortschritt
        fortschritt={f}
        art={art}
        laeuft={laeuft}
        onKarteOeffnen={onKarteOeffnen}
      />
    </ThemeProvider>,
  )
  return { onKarteOeffnen, container }
}

const stufenliste = () => within(screen.getByRole('list', { name: 'Weg der Kette #100' }))

describe('NachtlaufFortschritt', () => {
  it('zeigt die Wegleiste einer Kette mit jedem Zustand als Text', () => {
    zeige(fortschritt({ ketten: [kette()] }))

    const stufen = stufenliste().getAllByRole('listitem')
    expect(stufen.map((s) => s.textContent)).toEqual([
      'Plan: erreicht',
      'Prüfung: läuft',
      'Arbeitspakete: offen',
      'Abdeckung: offen',
    ])
  })

  it('markiert allein die aktuelle Stufe mit aria-current="step"', () => {
    zeige(fortschritt({ ketten: [kette()] }))

    const stufen = stufenliste().getAllByRole('listitem')
    expect(stufen.map((s) => s.getAttribute('aria-current'))).toEqual([null, 'step', null, null])
  })

  it('zeigt die Umsetzung als Stufe, wenn die Kette sie trägt', () => {
    zeige(
      fortschritt({
        ketten: [
          kette({
            stufen: [
              { stufe: 'PLAN', zustand: 'ERREICHT' },
              { stufe: 'REVIEW', zustand: 'ERREICHT' },
              { stufe: 'PAKETE', zustand: 'ERREICHT' },
              { stufe: 'ABDECKUNG', zustand: 'ERREICHT' },
              { stufe: 'UMSETZUNG', zustand: 'LAEUFT' },
            ],
            aktuelleStufe: 'UMSETZUNG',
          }),
        ],
      }),
    )

    const stufen = stufenliste().getAllByRole('listitem')
    expect(stufen[4]).toHaveTextContent('Umsetzung: läuft')
    expect(stufen[4]).toHaveAttribute('aria-current', 'step')
    expect(screen.queryByText('Ende des Wegs erreicht')).not.toBeInTheDocument()
  })

  it('zeigt in Variante A nach der Abdeckung „Ende des Wegs erreicht" statt einer offenen Umsetzung', () => {
    zeige(
      fortschritt({
        ketten: [
          kette({
            stufen: [
              { stufe: 'PLAN', zustand: 'ERREICHT' },
              { stufe: 'REVIEW', zustand: 'ERREICHT' },
              { stufe: 'PAKETE', zustand: 'ERREICHT' },
              { stufe: 'ABDECKUNG', zustand: 'ERREICHT' },
            ],
            aktuelleStufe: null,
            endeErreicht: true,
          }),
        ],
      }),
    )

    expect(stufenliste().getByText('Ende des Wegs erreicht')).toBeInTheDocument()
    expect(screen.queryByText(/Umsetzung/)).not.toBeInTheDocument()
    expect(
      stufenliste()
        .getAllByRole('listitem')
        .filter((s) => s.hasAttribute('aria-current')),
    ).toHaveLength(0)
  })

  it('nennt Anforderung, Plan und Pakete mit Nummer und Paketzustand als Text', () => {
    zeige(
      fortschritt({
        ketten: [
          kette({
            pakete: [
              paket(201, 'ANGELEGT'),
              paket(202, 'GEZOGEN'),
              paket(203, 'IN_UMSETZUNG'),
              paket(204, 'FERTIG'),
              paket(205, 'ZURUECKGESTELLT'),
            ],
          }),
        ],
      }),
    )

    expect(screen.getByRole('button', { name: 'Anforderung #100 Die Anforderung' })).toBeVisible()
    expect(screen.getByRole('button', { name: 'Plan #101 Der Plan' })).toBeVisible()
    const pakete = within(screen.getByRole('list', { name: 'Pakete der Kette #100' }))
    expect(pakete.getAllByRole('listitem').map((p) => p.textContent)).toEqual([
      'Paket#201angelegt',
      'Paket#202gezogen',
      'Paket#203in Umsetzung',
      'Paket#204fertig',
      'Paket#205zurückgestellt',
    ])
  })

  it('benennt einen fehlenden Plan und fehlende Pakete, statt sie wegzulassen', () => {
    zeige(fortschritt({ ketten: [kette({ plan: null, pakete: [] })] }))

    const block = screen.getByTestId('fortschritt-kette-100')
    expect(block).toHaveTextContent('noch kein Plan')
    expect(block).toHaveTextContent('noch keine Pakete')
    expect(within(block).queryByRole('button', { name: /^Plan / })).not.toBeInTheDocument()
  })

  it('ruft beim Klick auf eine Nummer onKarteOeffnen mit der Nummer', () => {
    const { onKarteOeffnen } = zeige(
      fortschritt({ ketten: [kette({ pakete: [paket(203, 'IN_UMSETZUNG')] })] }),
    )

    fireEvent.click(screen.getByRole('button', { name: 'Anforderung #100 Die Anforderung' }))
    fireEvent.click(screen.getByRole('button', { name: 'Plan #101 Der Plan' }))
    fireEvent.click(screen.getByRole('button', { name: 'Paket #203 Karte 203' }))

    expect(onKarteOeffnen.mock.calls).toEqual([[100], [101], [203]])
  })

  it('zeigt mehrere Ketten je mit eigener Wegleiste', () => {
    zeige(
      fortschritt({
        ketten: [kette(), kette({ anforderung: karte(300, 'Zweite'), plan: null })],
      }),
    )

    expect(screen.getByRole('list', { name: 'Weg der Kette #100' })).toBeInTheDocument()
    expect(screen.getByRole('list', { name: 'Weg der Kette #300' })).toBeInTheDocument()
  })

  it('zeigt in der Umsetzungsnacht nur die Umsetzung und die Paketliste', () => {
    zeige(
      fortschritt({ pakete: [paket(401, 'IN_UMSETZUNG'), paket(402, 'FERTIG')] }),
      { art: 'UMSETZUNGSNACHT' },
    )

    const weg = within(screen.getByRole('list', { name: 'Weg der Umsetzungsnacht' }))
    const stufen = weg.getAllByRole('listitem')
    expect(stufen.map((s) => s.textContent)).toEqual(['Umsetzung: läuft'])
    expect(stufen[0]).toHaveAttribute('aria-current', 'step')
    expect(screen.queryByText(/Plan|Prüfung|Abdeckung|Anforderung/)).not.toBeInTheDocument()
    const pakete = within(screen.getByRole('list', { name: 'Pakete der Umsetzungsnacht' }))
    expect(pakete.getAllByRole('listitem').map((p) => p.textContent)).toEqual([
      'Paket#401in Umsetzung',
      'Paket#402fertig',
    ])
  })

  it('benennt eine Umsetzungsnacht ohne Pakete', () => {
    zeige(fortschritt(), { art: 'UMSETZUNGSNACHT' })

    expect(screen.getByTestId('fortschritt-umsetzungsnacht')).toHaveTextContent('noch keine Pakete')
  })

  it('listet nicht zuordenbare Karten unter „unbekannt" als Verweise', () => {
    const { onKarteOeffnen } = zeige(
      fortschritt({ ketten: [kette()], unbekannt: [karte(501), karte(502)] }),
    )

    const unbekannt = within(screen.getByRole('list', { name: 'unbekannt' }))
    expect(unbekannt.getAllByRole('button').map((b) => b.textContent)).toEqual([
      'unbekannt#501',
      'unbekannt#502',
    ])
    fireEvent.click(unbekannt.getByRole('button', { name: 'unbekannt #502 Karte 502' }))
    expect(onKarteOeffnen).toHaveBeenCalledWith(502)
  })

  it('lässt den Abschnitt „unbekannt" weg, wenn jede Karte zugeordnet ist', () => {
    zeige(fortschritt({ ketten: [kette()] }))

    expect(screen.queryByRole('list', { name: 'unbekannt' })).not.toBeInTheDocument()
  })

  it('zeigt bei unbekannter Zuordnung einen Hinweis statt der Wegleiste', () => {
    zeige(fortschritt({ zuordnung: 'UNBEKANNT', ketten: [kette()], unbekannt: [karte(601)] }))

    expect(screen.getByTestId('fortschritt-zuordnung-unbekannt')).toHaveTextContent(
      'Welche Karten zu diesem Lauf gehören, lässt sich nicht feststellen',
    )
    expect(screen.queryByRole('list', { name: /^Weg der/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('list', { name: /^Pakete der/ })).not.toBeInTheDocument()
    expect(screen.getByRole('list', { name: 'unbekannt' })).toBeInTheDocument()
  })

  it('zeigt den Hinweis auf eine offene Frage mit der Karte als Status', () => {
    const { onKarteOeffnen } = zeige(
      fortschritt({ ketten: [kette()], offeneFragen: [karte(701, 'Die Frage')] }),
    )

    const status = screen.getByRole('status')
    expect(status).toHaveTextContent('Offene Frage an den Menschen')
    fireEvent.click(within(status).getByRole('button', { name: 'Frage #701 Die Frage' }))
    expect(onKarteOeffnen).toHaveBeenCalledWith(701)
  })

  it('zeigt ohne offene Frage keinen Status', () => {
    zeige(fortschritt({ ketten: [kette()] }))

    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('zeigt bei abgeschlossenem Lauf nur noch den Frage-Hinweis', () => {
    zeige(
      fortschritt({
        ketten: [kette()],
        unbekannt: [karte(501)],
        offeneFragen: [karte(701, 'Die Frage')],
      }),
      { laeuft: false },
    )

    expect(screen.getByRole('status')).toHaveTextContent('#701')
    expect(screen.queryByRole('list')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Anforderung/ })).not.toBeInTheDocument()
  })

  it('rendert bei abgeschlossenem Lauf ohne offene Frage nichts', () => {
    const { container } = zeige(fortschritt({ ketten: [kette()] }), { laeuft: false })

    expect(container).toBeEmptyDOMElement()
  })
})
