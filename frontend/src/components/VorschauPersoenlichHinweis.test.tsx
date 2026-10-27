import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { renderMitSprache } from '../i18n/renderMitSprache'
import { VorschauPersoenlichHinweis } from './VorschauPersoenlichHinweis'

describe('VorschauPersoenlichHinweis', () => {
  it('sagt, dass der Bereich in der Vorschau nicht gezeigt wird', () => {
    renderMitSprache(<VorschauPersoenlichHinweis />)
    expect(screen.getByRole('note')).toHaveTextContent('Dieser Bereich wird in der Vorschau nicht gezeigt.')
  })

  it('zeigt den Hinweis auf Englisch', () => {
    renderMitSprache(<VorschauPersoenlichHinweis />, 'en')
    expect(screen.getByRole('note')).toHaveTextContent('This area is not shown in the preview.')
  })
})
