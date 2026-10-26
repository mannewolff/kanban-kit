import { describe, expect, it } from 'vitest'
import { restHoehe } from './restHoehe'

describe('restHoehe', () => {
  it('reicht von der Oberkante bis zum unteren Fensterrand', () => {
    expect(restHoehe(180, 900, 320)).toBe(720)
  })

  it('bleibt beim Minimum, wenn der Rest kleiner wäre', () => {
    expect(restHoehe(700, 900, 320)).toBe(320)
  })

  it('bleibt beim Minimum, wenn die Oberkante unter dem Fensterrand liegt', () => {
    expect(restHoehe(1000, 900, 320)).toBe(320)
  })

  it('nimmt genau das Minimum, wenn der Rest ihm gleicht', () => {
    expect(restHoehe(580, 900, 320)).toBe(320)
  })
})
