import { theme } from '../theme'

/**
 * Chrome der Dialog-Kopfzeile (#652, E4/E7): eine untere Haarlinie aus der Palette, keine
 * getoente Flaeche. Ein gemeinsames Objekt statt sechs gleichlautender `sx`-Blocks — so kann eine
 * Kopfzeile nicht unbemerkt anders aussehen als die anderen fuenf.
 */
export const dialogTitleSx = { borderBottom: `1px solid ${theme.palette.divider}` } as const
