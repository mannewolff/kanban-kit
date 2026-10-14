import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { TabletQuerProvider } from '../lib/tabletQuer'
import { setzeMedien } from '../test/setup'
import { Erklaerung, ErklaerungOffenProvider } from './Erklaerung'

const tablet = (ui: ReactNode, mitProvider = true) => {
  setzeMedien({ tabletQuer: true })
  return render(
    <TabletQuerProvider>{mitProvider ? <ErklaerungOffenProvider>{ui}</ErklaerungOffenProvider> : ui}</TabletQuerProvider>,
  )
}

afterEach(() => {
  vi.useRealTimers()
})

describe('Erklaerung am Desktop', () => {
  it('rendert das Tooltip mit den Props des Aufrufers und öffnet es beim Darüberfahren', async () => {
    render(
      <TabletQuerProvider>
        <Erklaerung title="Gesperrt, weil ein Lauf läuft" describeChild placement="right">
          <span>Station</span>
        </Erklaerung>
      </TabletQuerProvider>,
    )

    const element = screen.getByText('Station')
    await userEvent.hover(element)

    const tooltip = await screen.findByRole('tooltip')
    expect(tooltip).toHaveTextContent('Gesperrt, weil ein Lauf läuft')
    expect(tooltip).toHaveAttribute('data-popper-placement', expect.stringMatching(/^right/))
    expect(element).toHaveAttribute('aria-describedby', tooltip.id)
  })

  it('hält ein vom Aufrufer gesteuertes Tooltip offen, ohne Maus', async () => {
    render(
      <Erklaerung title="Text" open>
        <span>Station</span>
      </Erklaerung>,
    )

    expect(await screen.findByRole('tooltip')).toHaveTextContent('Text')
  })

  it('rendert in der title-Variante das native title und kein Tooltip', async () => {
    render(
      <Erklaerung nativ title="Verbrauch der letzten Nacht">
        <span>42</span>
      </Erklaerung>,
    )

    const element = screen.getByText('42')
    expect(element).toHaveAttribute('title', 'Verbrauch der letzten Nacht')
    await userEvent.hover(element)
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument()
  })

  it('reagiert am Desktop nicht auf Antippen mit eigenem Zustand', () => {
    const elternKlick = vi.fn()
    render(
      // eslint-disable-next-line jsx-a11y/click-events-have-key-events, jsx-a11y/no-static-element-interactions -- Attrappe der Karte, nur ihr Klick zählt
      <div onClick={elternKlick}>
        <Erklaerung title="Text">
          <span>Label</span>
        </Erklaerung>
      </div>,
    )

    fireEvent.click(screen.getByText('Label'))

    expect(elternKlick).toHaveBeenCalledTimes(1)
  })
})

describe('Erklaerung auf dem Tablet im Querformat (Plan #1707, E7/E8)', () => {
  it('öffnet beim Antippen und schließt beim erneuten Antippen', async () => {
    tablet(
      <Erklaerung title="Die Erklärung" describeChild>
        <span>Label</span>
      </Erklaerung>,
    )

    const element = screen.getByText('Label')
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument()

    fireEvent.click(element)
    const tooltip = await screen.findByRole('tooltip')
    expect(tooltip).toHaveTextContent('Die Erklärung')
    expect(element).toHaveAttribute('aria-describedby', tooltip.id)

    fireEvent.click(element)
    await waitFor(() => expect(screen.queryByRole('tooltip')).not.toBeInTheDocument())
  })

  it('schließt beim Antippen daneben, per Klick und per Berührung', async () => {
    tablet(
      <Erklaerung title="Die Erklärung">
        <span>Label</span>
      </Erklaerung>,
    )

    fireEvent.click(screen.getByText('Label'))
    await screen.findByRole('tooltip')
    // Der ClickAwayListener greift erst nach seinem Aktivierungs-Takt.
    await act(() => new Promise((fertig) => setTimeout(fertig, 0)))
    fireEvent.click(document.body)
    await waitFor(() => expect(screen.queryByRole('tooltip')).not.toBeInTheDocument())

    fireEvent.click(screen.getByText('Label'))
    await screen.findByRole('tooltip')
    await act(() => new Promise((fertig) => setTimeout(fertig, 0)))
    fireEvent.touchEnd(document.body)
    await waitFor(() => expect(screen.queryByRole('tooltip')).not.toBeInTheDocument())
  })

  it('reicht das Antippen nicht an die Eltern weiter, ruft aber den eigenen Klick des Kindes', async () => {
    const elternKlick = vi.fn()
    const eigenerKlick = vi.fn()
    tablet(
      // eslint-disable-next-line jsx-a11y/click-events-have-key-events, jsx-a11y/no-static-element-interactions -- Attrappe der Karte, nur ihr Klick zählt
      <div onClick={elternKlick}>
        <Erklaerung title="Die Erklärung">
          {/* eslint-disable-next-line jsx-a11y/click-events-have-key-events, jsx-a11y/no-static-element-interactions -- Attrappe eines Kindes mit eigenem Klick */}
          <span onClick={eigenerKlick}>Label</span>
        </Erklaerung>
      </div>,
    )

    fireEvent.click(screen.getByText('Label'))

    expect(await screen.findByRole('tooltip')).toBeInTheDocument()
    expect(eigenerKlick).toHaveBeenCalledTimes(1)
    expect(elternKlick).not.toHaveBeenCalled()
  })

  it('öffnet nichts beim Darüberfahren und beim langen Berühren', async () => {
    vi.useFakeTimers()
    // `onOpen` meldet, ob MUI selbst öffnen wollte — das gesteuerte `open` allein verdeckte das.
    const onOpen = vi.fn()
    tablet(
      <Erklaerung title="Die Erklärung" describeChild onOpen={onOpen}>
        <button type="button">Knopf</button>
      </Erklaerung>,
    )
    const element = screen.getByRole('button', { name: 'Knopf' })

    fireEvent.mouseOver(element)
    // Langes Berühren: 700 ms Berührungsverzögerung, danach die Öffnungsverzögerung von MUI.
    fireEvent.touchStart(element)
    act(() => {
      vi.advanceTimersByTime(700)
    })
    act(() => {
      vi.advanceTimersByTime(200)
    })

    expect(onOpen).not.toHaveBeenCalled()
    expect(document.body.style.getPropertyValue('-webkit-user-select')).toBe('')
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument()
  })

  it('öffnet nichts beim Fokus mit der Tastatur', async () => {
    const onOpen = vi.fn()
    tablet(
      <Erklaerung title="Die Erklärung" describeChild onOpen={onOpen}>
        <button type="button">Knopf</button>
      </Erklaerung>,
    )

    await userEvent.tab()

    expect(screen.getByRole('button', { name: 'Knopf' })).toHaveFocus()
    expect(onOpen).not.toHaveBeenCalled()
  })

  it('hält höchstens eine Erklärung offen', async () => {
    tablet(
      <>
        <Erklaerung title="Erste Erklärung">
          <span>Erstes</span>
        </Erklaerung>
        <Erklaerung title="Zweite Erklärung">
          <span>Zweites</span>
        </Erklaerung>
      </>,
    )

    fireEvent.click(screen.getByText('Erstes'))
    expect(await screen.findByRole('tooltip')).toHaveTextContent('Erste Erklärung')

    fireEvent.click(screen.getByText('Zweites'))
    await waitFor(() => expect(screen.getAllByRole('tooltip')).toHaveLength(1))
    expect(screen.getByRole('tooltip')).toHaveTextContent('Zweite Erklärung')
  })

  it('arbeitet auch ohne Provider mit eigenem Zustand', async () => {
    tablet(
      <Erklaerung title="Die Erklärung">
        <span>Label</span>
      </Erklaerung>,
      false,
    )

    fireEvent.click(screen.getByText('Label'))
    expect(await screen.findByRole('tooltip')).toHaveTextContent('Die Erklärung')

    fireEvent.click(screen.getByText('Label'))
    await waitFor(() => expect(screen.queryByRole('tooltip')).not.toBeInTheDocument())
  })

  it('öffnet die title-Variante beim Antippen als Tooltip statt als natives title', async () => {
    tablet(
      <Erklaerung nativ title="Verbrauch der letzten Nacht">
        <span>42</span>
      </Erklaerung>,
    )

    const element = screen.getByText('42')
    expect(element).not.toHaveAttribute('title')
    fireEvent.click(element)

    expect(await screen.findByRole('tooltip')).toHaveTextContent('Verbrauch der letzten Nacht')
  })

  it('lässt ein vom Aufrufer gesteuertes Tooltip gesteuert', () => {
    tablet(
      <Erklaerung title="Text" open={false}>
        <span>Option</span>
      </Erklaerung>,
    )

    fireEvent.click(screen.getByText('Option'))

    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument()
  })
})
