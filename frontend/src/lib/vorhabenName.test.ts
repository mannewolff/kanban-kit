import { describe, expect, it } from 'vitest'
import { KIT_NAMEN } from '../test/kitNamen'
import { vorhabenNameVorschlag } from './vorhabenName'

const PRAEFIXE = KIT_NAMEN.titelPraefixe

describe('vorhabenNameVorschlag (Issue #1932)', () => {
  it('ersetzt [Requirement] durch [Epic]', () => {
    expect(vorhabenNameVorschlag(PRAEFIXE, '[Requirement] Export')).toBe('[Epic] Export')
  })

  it('ersetzt [Fachlich] durch [Epic]', () => {
    expect(vorhabenNameVorschlag(PRAEFIXE, '[Fachlich] Export')).toBe('[Epic] Export')
  })

  it('erkennt das Präfix in beliebiger Schreibung und nach führendem Leerraum', () => {
    expect(vorhabenNameVorschlag(PRAEFIXE, '  [requirement] Export')).toBe('[Epic] Export')
  })

  it('lässt einen Titel ohne Präfix unverändert', () => {
    expect(vorhabenNameVorschlag(PRAEFIXE, 'Export')).toBe('Export')
  })

  it('lässt ein Präfix einer anderen Art unverändert', () => {
    expect(vorhabenNameVorschlag(PRAEFIXE, '[Plan] Export')).toBe('[Plan] Export')
  })

  it('ersetzt nur ein führendes Präfix', () => {
    expect(vorhabenNameVorschlag(PRAEFIXE, 'Export [Requirement]')).toBe('Export [Requirement]')
  })

  it('lässt den Titel unverändert, solange die Präfixe nicht geladen sind', () => {
    expect(vorhabenNameVorschlag(undefined, '[Requirement] Export')).toBe('[Requirement] Export')
  })

  it('lässt den Titel unverändert, wenn das Register keine Art FACHLICH führt', () => {
    const ohne = PRAEFIXE.filter((a) => a.art !== 'FACHLICH')
    expect(vorhabenNameVorschlag(ohne, '[Requirement] Export')).toBe('[Requirement] Export')
  })
})
