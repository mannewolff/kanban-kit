import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { SnackbarProvider, useSnackbar } from './SnackbarProvider'

function Trigger({ message }: { message: string }) {
  const notify = useSnackbar()
  return <button onClick={() => notify(message)}>auslösen</button>
}

describe('SnackbarProvider', () => {
  it('zeigt eine Meldung nach notify(...)', async () => {
    render(
      <SnackbarProvider>
        <Trigger message="Board weg" />
      </SnackbarProvider>,
    )
    await userEvent.click(screen.getByText('auslösen'))
    expect(await screen.findByText('Board weg')).toBeInTheDocument()
  })

  it('schließt die Meldung über den Schließen-Button', async () => {
    render(
      <SnackbarProvider>
        <Trigger message="Board weg" />
      </SnackbarProvider>,
    )
    await userEvent.click(screen.getByText('auslösen'))
    expect(await screen.findByText('Board weg')).toBeInTheDocument()
    await userEvent.click(screen.getByLabelText('Close'))
    expect(screen.queryByText('Board weg')).not.toBeInTheDocument()
  })

  it('ist ohne Provider ein No-op (kein Crash)', async () => {
    render(<Trigger message="Board weg" />)
    await userEvent.click(screen.getByText('auslösen'))
    expect(screen.queryByText('Board weg')).not.toBeInTheDocument()
  })
})
