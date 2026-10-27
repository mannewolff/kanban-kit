import Button from '@mui/material/Button'
import { fireEvent, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renderMitSprache } from '../i18n/renderMitSprache'
import { VorschauGesperrt } from './VorschauGesperrt'

const auth = vi.hoisted(() => ({ vorschau: null as null | Record<string, string> }))
vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ vorschau: auth.vorschau }),
}))

const vorschau = { kennung: 'vk-1', zielName: 'Grace', adminName: 'Ada', laeuftAbAm: '2026-10-27T10:30:00Z' }

function renderGesperrt(onClick: () => void, sprache: 'de' | 'en' = 'de') {
  return renderMitSprache(
    <VorschauGesperrt>
      <Button onClick={onClick}>Karte anlegen</Button>
    </VorschauGesperrt>,
    sprache,
  )
}

describe('VorschauGesperrt', () => {
  beforeEach(() => {
    auth.vorschau = null
  })

  it('reicht das Element außerhalb der Vorschau unverändert und bedienbar durch', async () => {
    const onClick = vi.fn()
    renderGesperrt(onClick)

    const knopf = screen.getByRole('button', { name: 'Karte anlegen' })
    expect(knopf).toBeEnabled()
    await userEvent.click(knopf)
    expect(onClick).toHaveBeenCalledTimes(1)
  })

  it('sperrt das Element in der Vorschau und gibt ihm den zugänglichen Namen „Vorschau“', () => {
    auth.vorschau = vorschau
    const onClick = vi.fn()
    renderGesperrt(onClick)

    const knopf = screen.getByRole('button', { name: 'Vorschau' })
    expect(knopf).toBeDisabled()
    fireEvent.click(knopf)
    expect(onClick).not.toHaveBeenCalled()
    expect(screen.queryByRole('button', { name: 'Karte anlegen' })).not.toBeInTheDocument()
  })

  it('zeigt in der Vorschau den Hinweis „Vorschau“ als Tooltip', async () => {
    auth.vorschau = vorschau
    renderGesperrt(vi.fn())

    // Das gesperrte Element selbst nimmt keinen Zeiger an; das Ereignis steigt zum Umschlag auf.
    fireEvent.mouseOver(screen.getByRole('button', { name: 'Vorschau' }))

    expect(await screen.findByRole('tooltip')).toHaveTextContent('Vorschau')
  })

  it('nennt den Hinweis auf Englisch', () => {
    auth.vorschau = vorschau
    renderGesperrt(vi.fn(), 'en')

    expect(screen.getByRole('button', { name: 'Preview' })).toBeDisabled()
  })
})
