import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, test } from 'vitest'
import type { AbschlussView, LaufendView, VergleichView, VorhabenKostenView } from '../../api/vorhabenKosten'
import { renderMitSprache } from '../../i18n/renderMitSprache'
import { VorhabenKostenZeile } from './VorhabenKostenZeile'

/** Die Zeile „Voraussichtliche Kosten“ eines Vorhabens (Issue #2006, Plan #1990, fachlich #1954). */

const laufend = (teil: Partial<LaufendView> = {}): VorhabenKostenView => ({
  epicId: 42,
  number: 1782,
  archiviert: false,
  laufend: {
    hatDokumentOderPaket: true,
    bisher: 0,
    unvollstaendig: false,
    voraussage: { art: 'ANFORDERUNG', typisch: 14, unten: 11, oben: 19.5, paketzahl: 6, ohneStufe: 0 },
    zuWenigDaten: null,
    ...teil,
  },
  abschluss: null,
})

const vergleich = (typisch: number, abweichungProzent: number | null): VergleichView => ({
  beziffert: true,
  art: 'ANFORDERUNG',
  typisch,
  unten: typisch - 2,
  oben: typisch + 3,
  abweichungProzent,
})

const KEINE: VergleichView = { beziffert: false, art: null, typisch: null, unten: null, oben: null, abweichungProzent: null }

const abgeschlossen = (teil: Partial<AbschlussView> = {}, archiviert = false): VorhabenKostenView => ({
  epicId: 42,
  number: 1782,
  archiviert,
  laufend: null,
  abschluss: {
    abgeschlossenAt: '2026-11-01T06:00:00Z',
    tatsaechlich: 18,
    unvollstaendig: false,
    anforderung: vergleich(16, 13),
    pakete: { ...vergleich(20, -10), art: 'PAKETE' },
    ...teil,
  },
})

const zeige = (kosten: VorhabenKostenView, immer = false, sprache: 'de' | 'en' = 'de') =>
  renderMitSprache(<VorhabenKostenZeile kosten={kosten} immer={immer} />, sprache)

const zeile = () => screen.getByTestId('kosten-1782')

describe('VorhabenKostenZeile', () => {
  test('Szenario: Voraussage aus der Anforderung', async () => {
    zeige(laufend())
    expect(zeile()).toHaveTextContent('Voraussichtliche Kosten: typisch 14,00 $ (11,00 $–19,50 $)')
    expect(zeile()).toHaveTextContent('aus der Anforderung geschätzt')
    expect(zeile()).toHaveTextContent('bisher 0,00 $')
    await userEvent.hover(screen.getByTestId('kosten-hinweis-1782'))
    const hinweis = await screen.findByRole('tooltip')
    expect(hinweis).toHaveTextContent('Schätzung')
    expect(hinweis).toHaveTextContent('letzten zwölf Wochen')
  })

  test('Szenario: Voraussage aus den Paketen', () => {
    zeige(laufend({ bisher: 4, voraussage: { art: 'PAKETE', typisch: 16, unten: 12, oben: 24, paketzahl: 8, ohneStufe: 0 } }))
    expect(zeile()).toHaveTextContent('Voraussichtliche Kosten: typisch 16,00 $ (12,00 $–24,00 $)')
    expect(zeile()).toHaveTextContent('aus 8 Paketen')
    expect(zeile()).toHaveTextContent('bisher 4,00 $')
    expect(zeile()).not.toHaveTextContent('geschätzt')
  })

  test('Szenario: Zu wenig Daten', () => {
    zeige(laufend({ voraussage: null, zuWenigDaten: 'ZU_WENIG_VORHABEN' }))
    expect(zeile()).toHaveTextContent('Noch zu wenig Daten für eine Voraussage')
    expect(zeile()).not.toHaveTextContent('typisch')
    expect(zeile()).not.toHaveTextContent('Voraussichtliche Kosten:')
  })

  test('Szenario: Voraussage und tatsächliche Kosten', () => {
    zeige(abgeschlossen())
    expect(zeile()).toHaveTextContent('tatsächlich 18,00 $ · aus der Anforderung 16,00 $ (+13 %) · aus den Paketen 20,00 $ (−10 %)')
  })

  it('nennt „vor dem Schnitt geschätzt“ bei einem Vorhaben ohne Anforderung', () => {
    zeige(laufend({ voraussage: { art: 'VOR_DEM_SCHNITT', typisch: 9, unten: 7, oben: 12, paketzahl: 6, ohneStufe: 0 } }))
    expect(zeile()).toHaveTextContent('vor dem Schnitt geschätzt')
  })

  it('sagt „aus 1 Paket“ in der Einzahl', () => {
    zeige(laufend({ voraussage: { art: 'PAKETE', typisch: 2, unten: 1, oben: 3, paketzahl: 1, ohneStufe: 0 } }))
    expect(zeile()).toHaveTextContent('aus 1 Paket')
    expect(zeile()).not.toHaveTextContent('Paketen')
  })

  it('kennzeichnet fehlende Messungen als „unvollständig“', () => {
    zeige(laufend({ bisher: 0.5, unvollstaendig: true }))
    expect(zeile()).toHaveTextContent('bisher 0,50 $ (unvollständig)')
  })

  it('nennt im Hinweis die Pakete ohne Aufgabenstufe und die Grundlage aus den Paketen', async () => {
    zeige(laufend({ voraussage: { art: 'PAKETE', typisch: 16, unten: 12, oben: 24, paketzahl: 8, ohneStufe: 2 } }))
    await userEvent.hover(screen.getByTestId('kosten-hinweis-1782'))
    const hinweis = await screen.findByRole('tooltip')
    expect(hinweis).toHaveTextContent('Aufgabenstufe')
    expect(hinweis).toHaveTextContent('2 Pakete ohne Aufgabenstufe')
  })

  it('nennt im Hinweis ein einzelnes Paket ohne Aufgabenstufe in der Einzahl', async () => {
    zeige(laufend({ voraussage: { art: 'ANFORDERUNG', typisch: 14, unten: 11, oben: 19.5, paketzahl: 6, ohneStufe: 1 } }))
    await userEvent.hover(screen.getByTestId('kosten-hinweis-1782'))
    expect(await screen.findByRole('tooltip')).toHaveTextContent('1 Paket ohne Aufgabenstufe rechnet mit dem Paketpreis')
  })

  it.each([
    [1, '1 package without a task level is priced'],
    [3, '3 packages without a task level are priced'],
  ])('nennt auf Englisch %i Pakete ohne Aufgabenstufe im Hinweis', async (ohneStufe, text) => {
    zeige(laufend({ voraussage: { art: 'PAKETE', typisch: 16, unten: 12, oben: 24, paketzahl: 8, ohneStufe } }), false, 'en')
    await userEvent.hover(screen.getByTestId('kosten-hinweis-1782'))
    expect(await screen.findByRole('tooltip')).toHaveTextContent(text)
  })

  it('nennt im Hinweis zu „zu wenig Daten“ den Grund', async () => {
    zeige(laufend({ voraussage: null, zuWenigDaten: 'STUFE_OHNE_PREIS' }))
    await userEvent.hover(screen.getByTestId('kosten-hinweis-1782'))
    expect(await screen.findByRole('tooltip')).toHaveTextContent('Preis')
  })

  it('zeigt ohne Anforderung, Plan und Paket keine Zeile', () => {
    const { container } = zeige(laufend({ hatDokumentOderPaket: false, voraussage: null }))
    expect(container).toBeEmptyDOMElement()
  })

  it('zeigt im Detail ohne Anforderung, Plan und Paket die bisherigen Kosten', () => {
    zeige(laufend({ hatDokumentOderPaket: false, voraussage: null, bisher: 1.25 }), true)
    expect(zeile()).toHaveTextContent('bisher 1,25 $')
    expect(zeile()).not.toHaveTextContent('Voraussichtliche')
  })

  it('zeigt „keine Voraussage“ ohne Abweichung, wenn keine beziffert festgehalten wurde', () => {
    zeige(abgeschlossen({ anforderung: KEINE, pakete: { ...vergleich(20, null), art: 'PAKETE' } }))
    expect(zeile()).toHaveTextContent('tatsächlich 18,00 $ · aus der Anforderung keine Voraussage · aus den Paketen 20,00 $')
    expect(zeile()).not.toHaveTextContent('%')
  })

  it('kennzeichnet unvollständige tatsächliche Kosten', () => {
    zeige(abgeschlossen({ unvollstaendig: true, anforderung: vergleich(16, null), pakete: KEINE }))
    expect(zeile()).toHaveTextContent('tatsächlich 18,00 $ (unvollständig)')
  })

  it('schreibt eine Abweichung von 0 ohne Vorzeichen', () => {
    zeige(abgeschlossen({ anforderung: vergleich(18, 0) }))
    expect(zeile()).toHaveTextContent('aus der Anforderung 18,00 $ (0 %)')
  })

  it('steht auf Englisch', () => {
    zeige(laufend({ bisher: 4, voraussage: { art: 'PAKETE', typisch: 16, unten: 12, oben: 24, paketzahl: 8, ohneStufe: 0 } }), false, 'en')
    expect(zeile()).toHaveTextContent('Expected cost: typically $16.00 ($12.00–$24.00) · from 8 packages · so far $4.00')
  })

  it('sagt auf Englisch „from 1 package“ und eine Abweichung von 0 ohne Vorzeichen', () => {
    const { unmount } = zeige(laufend({ voraussage: { art: 'PAKETE', typisch: 2, unten: 1, oben: 3, paketzahl: 1, ohneStufe: 0 } }), false, 'en')
    expect(zeile()).toHaveTextContent('from 1 package ·')
    unmount()
    zeige(abgeschlossen({ anforderung: vergleich(18, 0) }), false, 'en')
    expect(zeile()).toHaveTextContent('from the requirement $18.00 (0%)')
  })

  it('steht nach dem Abschluss auf Englisch', () => {
    zeige(abgeschlossen(), false, 'en')
    expect(zeile()).toHaveTextContent('actual $18.00 · from the requirement $16.00 (+13%) · from the packages $20.00 (−10%)')
  })
})
