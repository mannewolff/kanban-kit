import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { EditModeProvider, useEditMode } from './EditModeContext'

function Probe() {
  const { editMode, setEditMode, toggleEditMode } = useEditMode()
  return (
    <div>
      <span data-testid="mode">{String(editMode)}</span>
      <button onClick={toggleEditMode}>toggle</button>
      <button onClick={() => setEditMode(true)}>on</button>
      <button onClick={() => setEditMode(false)}>off</button>
    </div>
  )
}

describe('EditModeContext', () => {
  it('ist standardmäßig aus', () => {
    render(
      <EditModeProvider>
        <Probe />
      </EditModeProvider>,
    )
    expect(screen.getByTestId('mode')).toHaveTextContent('false')
  })

  it('toggleEditMode kippt den Wert', async () => {
    render(
      <EditModeProvider>
        <Probe />
      </EditModeProvider>,
    )
    await userEvent.click(screen.getByRole('button', { name: 'toggle' }))
    expect(screen.getByTestId('mode')).toHaveTextContent('true')
    await userEvent.click(screen.getByRole('button', { name: 'toggle' }))
    expect(screen.getByTestId('mode')).toHaveTextContent('false')
  })

  it('setEditMode setzt den Wert explizit', async () => {
    render(
      <EditModeProvider>
        <Probe />
      </EditModeProvider>,
    )
    await userEvent.click(screen.getByRole('button', { name: 'on' }))
    expect(screen.getByTestId('mode')).toHaveTextContent('true')
    await userEvent.click(screen.getByRole('button', { name: 'off' }))
    expect(screen.getByTestId('mode')).toHaveTextContent('false')
  })

  it('liefert ohne Provider einen No-op-Default (editMode false, kein Fehler)', async () => {
    render(<Probe />)
    expect(screen.getByTestId('mode')).toHaveTextContent('false')
    // No-op-Setter dürfen nicht werfen und ändern nichts.
    await userEvent.click(screen.getByRole('button', { name: 'toggle' }))
    await userEvent.click(screen.getByRole('button', { name: 'on' }))
    expect(screen.getByTestId('mode')).toHaveTextContent('false')
  })
})
