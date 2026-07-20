import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { EditModeProvider, useEditMode } from '../lib/EditModeContext'
import { AdministrationPage } from './AdministrationPage'

function renderPage() {
  return render(
    <EditModeProvider>
      <AdministrationPage />
      <ModeProbe />
    </EditModeProvider>,
  )
}

// Spiegelt den Editiermodus, damit der Test den Context-Effekt des Schalters beobachten kann.
function ModeProbe() {
  const { editMode } = useEditMode()
  return <span data-testid="mode">{String(editMode)}</span>
}

describe('AdministrationPage', () => {
  it('rendert den Editiermodus-Schalter, Default aus', () => {
    renderPage()
    const toggle = screen.getByRole('checkbox', { name: 'Editiermodus aktivieren' })
    expect(toggle).not.toBeChecked()
    expect(screen.getByTestId('mode')).toHaveTextContent('false')
  })

  it('schaltet den Editiermodus über den Context ein und wieder aus', async () => {
    renderPage()
    const toggle = screen.getByRole('checkbox', { name: 'Editiermodus aktivieren' })

    await userEvent.click(toggle)
    expect(toggle).toBeChecked()
    expect(screen.getByTestId('mode')).toHaveTextContent('true')

    await userEvent.click(toggle)
    expect(toggle).not.toBeChecked()
    expect(screen.getByTestId('mode')).toHaveTextContent('false')
  })
})
