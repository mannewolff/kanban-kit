import Visibility from '@mui/icons-material/Visibility'
import VisibilityOff from '@mui/icons-material/VisibilityOff'
import IconButton from '@mui/material/IconButton'
import InputAdornment from '@mui/material/InputAdornment'
import TextField, { type TextFieldProps } from '@mui/material/TextField'
import { useState } from 'react'
import { useT } from '../i18n/SpracheContext'

/**
 * Passwort-Eingabefeld mit Augensymbol zum Ein-/Ausblenden des Klartexts. Verhält sich wie ein
 * MUI-`TextField` (alle Props werden durchgereicht), nur das `type`-Feld steuert die Komponente
 * selbst. Der Umschalter belegt das End-Adornment des Feldes.
 */
export function PasswordField({ slotProps, ...props }: Readonly<Omit<TextFieldProps, 'type'>>) {
  const t = useT()
  const [show, setShow] = useState(false)
  return (
    <TextField
      {...props}
      type={show ? 'text' : 'password'}
      slotProps={{
        ...slotProps,
        input: {
          ...slotProps?.input,
          endAdornment: (
            <InputAdornment position="end">
              <IconButton
                aria-label={show ? t.AUTH.PASSWORT_VERBERGEN : t.AUTH.PASSWORT_ANZEIGEN}
                onClick={() => setShow((s) => !s)}
                // Klick auf das Auge soll den Feldfokus nicht stehlen.
                onMouseDown={(e) => e.preventDefault()}
                edge="end"
                // Aus dem Tab-Fluss: Fokus springt vom Feld direkt zum nächsten Bedienelement.
                tabIndex={-1}
              >
                {show ? <VisibilityOff /> : <Visibility />}
              </IconButton>
            </InputAdornment>
          ),
        },
      }}
    />
  )
}
