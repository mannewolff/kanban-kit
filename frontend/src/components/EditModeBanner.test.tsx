import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { EditModeBanner } from './EditModeBanner'
import { renderMitSprache } from '../i18n/renderMitSprache'

const state = vi.hoisted(() => ({ editMode: true, setEditMode: vi.fn() }))
vi.mock('../lib/EditModeContext', () => ({
  useEditMode: () => ({
    editMode: state.editMode,
    setEditMode: state.setEditMode,
    toggleEditMode: vi.fn(),
  }),
}))

describe('EditModeBanner', () => {
  it('rendert nichts im Ansichtsmodus', () => {
    state.editMode = false
    const { container } = render(<EditModeBanner />)
    expect(container).toBeEmptyDOMElement()
  })

  it('zeigt den Hinweis und verlässt den Editiermodus per Toggle', () => {
    state.editMode = true
    state.setEditMode = vi.fn()
    render(<EditModeBanner />)

    expect(screen.getByText('Achtung, Du befindest Dich im Editiermodus')).toBeInTheDocument()
    fireEvent.click(screen.getByLabelText('Editiermodus verlassen'))
    expect(state.setEditMode).toHaveBeenCalledWith(false)
  })

  it('schreibt den Warnhinweis in der kontrastreichen Variante auf das Amber', () => {
    // Weiss auf warning.main ergaebe 3,11:1 und verfehlte AA fuer Fliesstext (#653, E6).
    state.editMode = true
    render(<EditModeBanner />)

    expect(screen.getByRole('region', { name: 'Editiermodus-Hinweis' })).toHaveStyle({
      color: 'rgb(0, 0, 0)',
    })
  })
})

describe('EditModeBanner auf Englisch', () => {
  it('zeigt Hinweis, Bereich und Schalter auf Englisch', () => {
    state.editMode = true
    state.setEditMode = vi.fn()
    renderMitSprache(<EditModeBanner />, 'en')

    expect(screen.getByRole('region', { name: 'Edit mode notice' })).toBeInTheDocument()
    expect(screen.getByText('Note: you are in edit mode')).toBeInTheDocument()
    fireEvent.click(screen.getByLabelText('Leave edit mode'))
    expect(state.setEditMode).toHaveBeenCalledWith(false)
  })
})
