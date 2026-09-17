import { theme } from '../theme'

/**
 * Chrome der Dialog-Kopfzeile (#652, E4/E7): eine untere Haarlinie aus der Palette, keine
 * getoente Flaeche. Ein gemeinsames Objekt statt sechs gleichlautender `sx`-Blocks — so kann eine
 * Kopfzeile nicht unbemerkt anders aussehen als die anderen fuenf.
 *
 * Das Objekt entsteht einmal beim Modulstart. Es liest deshalb `theme.vars` und nicht
 * `theme.palette`: Letzteres ist der helle Wert, nur der Variablen-Verweis schaltet mit dem
 * Erscheinungsbild um (#952).
 */
export const dialogTitleSx = { borderBottom: `1px solid ${theme.vars.palette.divider}` } as const
