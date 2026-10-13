import { act, fireEvent, render, renderHook, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { languageApi } from '../api/language'
import { de } from './de'
import { en } from './en'
import { renderMitSprache } from './renderMitSprache'
import { SpracheProvider, useSprache, useT } from './SpracheContext'

function Anzeige() {
  const { sprache, setzeSprache } = useSprache()
  const t = useT()
  return (
    <div>
      <p>{`${sprache}: ${t.APP_NAME}`}</p>
      <button type="button" onClick={() => setzeSprache(sprache === 'de' ? 'en' : 'de')}>
        umschalten
      </button>
    </div>
  )
}

beforeEach(() => {
  document.documentElement.lang = 'de'
  document.title = 'KI-Leitstand'
})

afterEach(() => vi.restoreAllMocks())

describe('SpracheContext ohne Provider', () => {
  it('liefert Deutsch und das deutsche Wörterbuch, ohne zu werfen', () => {
    const { result } = renderHook(() => ({ sprache: useSprache().sprache, t: useT() }))
    expect(result.current.sprache).toBe('de')
    expect(result.current.t).toBe(de)
  })

  it('nimmt setzeSprache ohne Wirkung hin', () => {
    const { result } = renderHook(() => useSprache())
    act(() => result.current.setzeSprache('en'))
    expect(result.current.sprache).toBe('de')
  })
})

describe('SpracheProvider', () => {
  it('zeigt bis zur Antwort nur den Ladezustand', async () => {
    let antworten: (sprache: 'de' | 'en') => void = () => {}
    vi.spyOn(languageApi, 'lesen').mockReturnValue(
      new Promise((resolve) => {
        antworten = resolve
      }),
    )
    render(
      <SpracheProvider>
        <Anzeige />
      </SpracheProvider>,
    )
    expect(screen.getByRole('progressbar')).toBeInTheDocument()
    expect(screen.queryByText(/KI-Leitstand|AI Control Room/)).not.toBeInTheDocument()
    await act(async () => antworten('en'))
    expect(screen.getByText(`en: ${en.APP_NAME}`)).toBeInTheDocument()
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument()
  })

  it('lädt Englisch und setzt lang und Fenstertitel', async () => {
    vi.spyOn(languageApi, 'lesen').mockResolvedValue('en')
    render(
      <SpracheProvider>
        <Anzeige />
      </SpracheProvider>,
    )
    expect(await screen.findByText('en: AI Control Room')).toBeInTheDocument()
    expect(document.documentElement.lang).toBe('en')
    expect(document.title).toBe('AI Control Room')
  })

  it('fällt bei einem Fehler auf Deutsch zurück', async () => {
    document.documentElement.lang = 'xx'
    document.title = 'leer'
    vi.spyOn(languageApi, 'lesen').mockRejectedValue(new Error('offline'))
    render(
      <SpracheProvider>
        <Anzeige />
      </SpracheProvider>,
    )
    expect(await screen.findByText('de: KI-Leitstand')).toBeInTheDocument()
    expect(document.documentElement.lang).toBe('de')
    expect(document.title).toBe('KI-Leitstand')
  })

  it('lädt ohne eigenen Mock über die Vorgabe aus dem Test-Setup Deutsch', async () => {
    render(
      <SpracheProvider>
        <Anzeige />
      </SpracheProvider>,
    )
    expect(await screen.findByText('de: KI-Leitstand')).toBeInTheDocument()
  })

  it('übernimmt eine gesetzte Sprache sofort, samt lang und Titel', async () => {
    vi.spyOn(languageApi, 'lesen').mockResolvedValue('de')
    render(
      <SpracheProvider>
        <Anzeige />
      </SpracheProvider>,
    )
    await screen.findByText('de: KI-Leitstand')
    fireEvent.click(screen.getByRole('button', { name: 'umschalten' }))
    expect(screen.getByText('en: AI Control Room')).toBeInTheDocument()
    expect(document.documentElement.lang).toBe('en')
    expect(document.title).toBe('AI Control Room')
  })

  it('lädt mit fester Anfangssprache nicht und rendert sofort', () => {
    const lesen = vi.spyOn(languageApi, 'lesen')
    render(
      <SpracheProvider anfangs="en">
        <Anzeige />
      </SpracheProvider>,
    )
    expect(screen.getByText('en: AI Control Room')).toBeInTheDocument()
    expect(lesen).not.toHaveBeenCalled()
  })
})

describe('renderMitSprache', () => {
  it('rendert ohne Angabe Deutsch', () => {
    renderMitSprache(<Anzeige />)
    expect(screen.getByText('de: KI-Leitstand')).toBeInTheDocument()
  })

  it('rendert mit Angabe Englisch', () => {
    renderMitSprache(<Anzeige />, 'en')
    expect(screen.getByText('en: AI Control Room')).toBeInTheDocument()
  })
})
