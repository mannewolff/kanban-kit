import { fireEvent, screen } from '@testing-library/react'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { renderMitSprache } from '../i18n/renderMitSprache'
import { DatumFeld, isoAusEingabe, eingabeAusIso } from './DatumFeld'

/** Hält den Wert wie ein Formular, damit das Feld ihn zurückbekommt. */
function Gehalten({ anfangs, onChange }: Readonly<{ anfangs: string; onChange: (wert: string) => void }>) {
  const [wert, setWert] = useState(anfangs)
  return (
    <DatumFeld
      label="Datum"
      value={wert}
      onChange={(neu) => {
        setWert(neu)
        onChange(neu)
      }}
    />
  )
}

describe('isoAusEingabe', () => {
  it('liest TT.MM.JJJJ auf Deutsch und MM/DD/YYYY auf Englisch', () => {
    expect(isoAusEingabe('01.08.2026', 'de')).toBe('2026-08-01')
    expect(isoAusEingabe('1.8.2026', 'de')).toBe('2026-08-01')
    expect(isoAusEingabe('08/01/2026', 'en')).toBe('2026-08-01')
    expect(isoAusEingabe('8/1/2026', 'en')).toBe('2026-08-01')
  })

  it('nimmt Leerraum am Rand hin', () => {
    expect(isoAusEingabe(' 01.08.2026 ', 'de')).toBe('2026-08-01')
  })

  it('liefert leer für eine leere Eingabe', () => {
    expect(isoAusEingabe('', 'de')).toBe('')
    expect(isoAusEingabe('   ', 'en')).toBe('')
  })

  it('weist ungültige Daten und die fremde Schreibweise ab', () => {
    expect(isoAusEingabe('31.02.2026', 'de')).toBeNull()
    expect(isoAusEingabe('00.08.2026', 'de')).toBeNull()
    expect(isoAusEingabe('01.13.2026', 'de')).toBeNull()
    expect(isoAusEingabe('02/30/2026', 'en')).toBeNull()
    expect(isoAusEingabe('08/01/2026', 'de')).toBeNull()
    expect(isoAusEingabe('01.08.2026', 'en')).toBeNull()
    expect(isoAusEingabe('2026-08-01', 'de')).toBeNull()
    expect(isoAusEingabe('x01.08.2026', 'de')).toBeNull()
    expect(isoAusEingabe('01.08.2026x', 'de')).toBeNull()
    expect(isoAusEingabe('01.08.26', 'de')).toBeNull()
  })

  it('kennt den Schalttag', () => {
    expect(isoAusEingabe('29.02.2028', 'de')).toBe('2028-02-29')
    expect(isoAusEingabe('29.02.2026', 'de')).toBeNull()
  })
})

describe('eingabeAusIso', () => {
  it('schreibt einen ISO-Wert in der Schreibweise der Sprache', () => {
    expect(eingabeAusIso('2026-08-01', 'de')).toBe('01.08.2026')
    expect(eingabeAusIso('2026-08-01', 'en')).toBe('08/01/2026')
  })

  it('lässt einen leeren oder fremden Wert leer', () => {
    expect(eingabeAusIso('', 'de')).toBe('')
    expect(eingabeAusIso('kaputt', 'en')).toBe('')
  })
})

describe('DatumFeld', () => {
  it('zeigt einen gespeicherten ISO-Wert auf Deutsch mit Muster als Platzhalter', () => {
    renderMitSprache(<DatumFeld label="Datum" value="2026-08-01" onChange={vi.fn()} />)

    const feld = screen.getByLabelText('Datum')
    expect(feld).toHaveValue('01.08.2026')
    expect(feld).toHaveAttribute('placeholder', 'TT.MM.JJJJ')
    expect(feld).not.toHaveAttribute('type', 'date')
  })

  it('zeigt einen gespeicherten ISO-Wert auf Englisch mit Muster als Platzhalter', () => {
    renderMitSprache(<DatumFeld label="Date" value="2026-08-01" onChange={vi.fn()} />, 'en')

    const feld = screen.getByLabelText('Date')
    expect(feld).toHaveValue('08/01/2026')
    expect(feld).toHaveAttribute('placeholder', 'MM/DD/YYYY')
  })

  it('gibt eine deutsche Eingabe als ISO weiter', () => {
    const onChange = vi.fn()
    renderMitSprache(<Gehalten anfangs="" onChange={onChange} />)

    fireEvent.change(screen.getByLabelText('Datum'), { target: { value: '01.08.2026' } })

    expect(onChange).toHaveBeenLastCalledWith('2026-08-01')
    expect(screen.getByLabelText('Datum')).toHaveValue('01.08.2026')
  })

  it('gibt eine englische Eingabe als ISO weiter', () => {
    const onChange = vi.fn()
    renderMitSprache(<Gehalten anfangs="" onChange={onChange} />, 'en')

    fireEvent.change(screen.getByLabelText('Datum'), { target: { value: '08/01/2026' } })

    expect(onChange).toHaveBeenLastCalledWith('2026-08-01')
  })

  it('lässt eine knappe Eingabe stehen, statt sie beim Tippen umzuschreiben', () => {
    renderMitSprache(<Gehalten anfangs="" onChange={vi.fn()} />)

    fireEvent.change(screen.getByLabelText('Datum'), { target: { value: '1.8.2026' } })

    expect(screen.getByLabelText('Datum')).toHaveValue('1.8.2026')
  })

  it('gibt beim Leeren einen leeren Wert weiter', () => {
    const onChange = vi.fn()
    renderMitSprache(<Gehalten anfangs="2026-08-01" onChange={onChange} />)

    fireEvent.change(screen.getByLabelText('Datum'), { target: { value: '' } })

    expect(onChange).toHaveBeenLastCalledWith('')
    expect(screen.getByLabelText('Datum')).toHaveValue('')
  })

  it('meldet ein ungültiges Datum erst nach dem Verlassen und gibt es nicht weiter', () => {
    const onChange = vi.fn()
    renderMitSprache(<Gehalten anfangs="2026-08-01" onChange={onChange} />)
    const feld = screen.getByLabelText('Datum')

    fireEvent.change(feld, { target: { value: '31.02.2026' } })
    expect(screen.queryByText('Kein gültiges Datum — Schreibweise TT.MM.JJJJ')).not.toBeInTheDocument()
    expect(feld).not.toHaveAttribute('aria-invalid', 'true')

    fireEvent.blur(feld)

    expect(screen.getByText('Kein gültiges Datum — Schreibweise TT.MM.JJJJ')).toBeInTheDocument()
    expect(feld).toHaveAttribute('aria-invalid', 'true')
    expect(onChange).not.toHaveBeenCalled()
    expect(feld).toHaveValue('31.02.2026')
  })

  it('meldet ein ungültiges Datum auf Englisch', () => {
    renderMitSprache(<Gehalten anfangs="" onChange={vi.fn()} />, 'en')
    const feld = screen.getByLabelText('Datum')

    fireEvent.change(feld, { target: { value: '13/01/2026' } })
    fireEvent.blur(feld)

    expect(screen.getByText('Not a valid date — use MM/DD/YYYY')).toBeInTheDocument()
  })

  it('nimmt die Meldung zurück, sobald die Eingabe gültig ist', () => {
    renderMitSprache(<Gehalten anfangs="" onChange={vi.fn()} />)
    const feld = screen.getByLabelText('Datum')

    fireEvent.change(feld, { target: { value: '31.02.2026' } })
    fireEvent.blur(feld)
    fireEvent.change(feld, { target: { value: '28.02.2026' } })

    expect(screen.queryByText('Kein gültiges Datum — Schreibweise TT.MM.JJJJ')).not.toBeInTheDocument()
    expect(feld).not.toHaveAttribute('aria-invalid', 'true')
  })

  it('meldet ein leeres Feld nach dem Verlassen nicht', () => {
    renderMitSprache(<Gehalten anfangs="" onChange={vi.fn()} />)
    const feld = screen.getByLabelText('Datum')

    fireEvent.blur(feld)

    expect(feld).not.toHaveAttribute('aria-invalid', 'true')
  })

  it('übernimmt einen von außen gesetzten Wert', () => {
    const { rerender } = renderMitSprache(<DatumFeld label="Datum" value="2026-08-01" onChange={vi.fn()} />)

    rerender(<DatumFeld label="Datum" value="2027-01-15" onChange={vi.fn()} />)
    expect(screen.getByLabelText('Datum')).toHaveValue('15.01.2027')

    rerender(<DatumFeld label="Datum" value="" onChange={vi.fn()} />)
    expect(screen.getByLabelText('Datum')).toHaveValue('')
  })
})
