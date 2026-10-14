import { renderHook } from '@testing-library/react'
import { createElement, type ReactNode } from 'react'
import { describe, expect, it } from 'vitest'
import { setzeMedien } from '../test/setup'
import { TABLET_QUER_QUERY, TabletQuerKontext, TabletQuerProvider, useTabletQuer } from './tabletQuer'

/** Hülle mit dem Provider, der die Query abfragt — so, wie `AppShell` ihn um die Seiten legt. */
const mitAbfrage = ({ children }: { children: ReactNode }) => createElement(TabletQuerProvider, null, children)

describe('tabletQuer (Plan #1707, E1)', () => {
  it('erkennt das Tablet an Finger, fehlendem Hover, Querformat und Mindestbreite', () => {
    expect(TABLET_QUER_QUERY).toContain('hover: none')
    expect(TABLET_QUER_QUERY).toContain('pointer: coarse')
    expect(TABLET_QUER_QUERY).toContain('orientation: landscape')
    expect(TABLET_QUER_QUERY).toContain('min-width: 1000px')
  })

  it('liefert ohne Provider den Desktop', () => {
    expect(renderHook(() => useTabletQuer()).result.current).toBe(false)
  })

  it('reicht den Wert des Kontexts durch', () => {
    const wrapper = ({ children }: { children: ReactNode }) =>
      createElement(TabletQuerKontext.Provider, { value: true }, children)
    expect(renderHook(() => useTabletQuer(), { wrapper }).result.current).toBe(true)
  })

  it('fragt im Provider die Query ab: am Desktop aus', () => {
    expect(renderHook(() => useTabletQuer(), { wrapper: mitAbfrage }).result.current).toBe(false)
  })

  it('fragt im Provider die Query ab: auf dem Tablet quer an', () => {
    setzeMedien({ tabletQuer: true })
    expect(renderHook(() => useTabletQuer(), { wrapper: mitAbfrage }).result.current).toBe(true)
  })

  it('setzt die Attrappe nach jedem Test auf den Desktop zurück', () => {
    expect(window.matchMedia(TABLET_QUER_QUERY).matches).toBe(false)
  })
})
