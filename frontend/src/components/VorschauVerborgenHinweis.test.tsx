import { screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renderMitSprache } from '../i18n/renderMitSprache'
import { VorschauVerborgenHinweis } from './VorschauVerborgenHinweis'

const auth = vi.hoisted(() => ({ vorschau: null as null | Record<string, string> }))
vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ vorschau: auth.vorschau }),
}))

const vorschau = { kennung: 'vk-1', zielName: 'Grace', adminName: 'Ada', laeuftAbAm: '2026-10-27T10:30:00Z' }

describe('VorschauVerborgenHinweis', () => {
  beforeEach(() => {
    auth.vorschau = vorschau
  })

  it('nennt XY mit Namen: XY sieht den Bereich, die Vorschau zeigt ihn nicht', () => {
    renderMitSprache(<VorschauVerborgenHinweis />)
    expect(screen.getByRole('note')).toHaveTextContent('Grace sieht diesen Bereich, die Vorschau zeigt ihn nicht.')
  })

  it('rendert außerhalb der Vorschau nichts', () => {
    auth.vorschau = null
    const { container } = renderMitSprache(<VorschauVerborgenHinweis />)
    expect(container).toBeEmptyDOMElement()
  })

  it('zeigt den Hinweis auf Englisch', () => {
    renderMitSprache(<VorschauVerborgenHinweis />, 'en')
    expect(screen.getByRole('note')).toHaveTextContent('Grace sees this area, the preview does not show it.')
  })
})
