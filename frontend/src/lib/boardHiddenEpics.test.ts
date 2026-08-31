import { describe, expect, it } from 'vitest'
import { hiddenEpicsStorageKey } from './boardHiddenEpics'

describe('hiddenEpicsStorageKey', () => {
  it('bildet den Schlüssel je Board im bestehenden manban-Namensraum', () => {
    expect(hiddenEpicsStorageKey(1)).toBe('manban.boardHiddenEpics.1')
  })

  it('trennt die Boards voneinander', () => {
    expect(hiddenEpicsStorageKey(7)).not.toBe(hiddenEpicsStorageKey(1))
  })
})
