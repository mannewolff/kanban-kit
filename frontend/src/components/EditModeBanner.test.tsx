import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { EditModeBanner } from './EditModeBanner'

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
})
