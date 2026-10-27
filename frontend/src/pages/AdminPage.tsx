import EditIcon from '@mui/icons-material/Edit'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import IconButton from '@mui/material/IconButton'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { useCallback, useEffect, useState } from 'react'
import { adminApi as defaultAdminApi, type AdminApi, type AdminUser } from '../api/admin'
import { ApiError, apiErrorMessage, startseiteNeuLaden } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { BackupStatusSection } from '../components/BackupStatusSection'
import { DataTable, type DataTableColumn } from '../components/DataTable'
import { OverloadRejectionsSection } from '../components/OverloadRejectionsSection'
import { VorschauEintraege } from '../components/VorschauEintraege'
import { useT } from '../i18n/SpracheContext'
import { useEditMode } from '../lib/EditModeContext'

interface Props {
  api?: AdminApi
}

export function AdminPage({ api = defaultAdminApi }: Readonly<Props>) {
  const { user: currentUser } = useAuth()
  const { editMode } = useEditMode()
  const t = useT()
  const ta = t.ADMIN_SEITE
  const [users, setUsers] = useState<AdminUser[]>([])
  const [error, setError] = useState<string | null>(null)
  const [editing, setEditing] = useState<{ id: number; name: string } | null>(null)

  const { KEIN_ZUGRIFF, LADEN_FEHLGESCHLAGEN } = ta
  const reload = useCallback(() => {
    api
      .listUsers()
      .then(setUsers)
      .catch((e) => setError(e instanceof ApiError && e.status === 403 ? KEIN_ZUGRIFF : LADEN_FEHLGESCHLAGEN))
  }, [api, KEIN_ZUGRIFF, LADEN_FEHLGESCHLAGEN])

  useEffect(() => {
    reload()
  }, [reload])

  const toggleRole = async (u: AdminUser) => {
    setError(null)
    const next = u.platformRole === 'ADMIN' ? 'USER' : 'ADMIN'
    try {
      await api.setRole(u.id, next)
      reload()
    } catch (e) {
      setError(apiErrorMessage(e, ta.ROLLE_FEHLGESCHLAGEN))
    }
  }

  const approve = async (u: AdminUser) => {
    setError(null)
    try {
      await api.approve(u.id)
      reload()
    } catch (e) {
      setError(apiErrorMessage(e, ta.FREIGABE_FEHLGESCHLAGEN))
    }
  }

  const saveDisplayName = async () => {
    if (!editing || editing.name.trim().length === 0) {
      return
    }
    setError(null)
    try {
      await api.setDisplayName(editing.id, editing.name)
      setEditing(null)
      reload()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : ta.NAME_FEHLGESCHLAGEN)
    }
  }

  const toggleDisabled = async (u: AdminUser) => {
    setError(null)
    try {
      await (u.disabled ? api.enable(u.id) : api.disable(u.id))
      reload()
    } catch (e) {
      setError(apiErrorMessage(e, u.disabled ? ta.ENTSPERREN_FEHLGESCHLAGEN : ta.SPERREN_FEHLGESCHLAGEN))
    }
  }

  // Nach dem Start lädt die Oberfläche auf `/` neu; `StartRedirect` wählt dort mit der
  // Selbstauskunft von XY dessen Startseite (Plan #1914, E13).
  const ansichtStarten = async (u: AdminUser) => {
    setError(null)
    try {
      await api.vorschauStarten(u.id)
      startseiteNeuLaden()
    } catch (e) {
      setError(apiErrorMessage(e, ta.ANSICHT_FEHLGESCHLAGEN))
    }
  }

  // In Frage kommt jedes Konto außer dem eigenen und den Plattform-Admins, auch gesperrte und
  // wartende: Die Ansicht zeigt, was XY nach dem Entsperren bzw. der Freigabe sähe (AK 1, E11).
  const ansichtMoeglich = (u: AdminUser) => u.id !== currentUser?.userId && u.platformRole !== 'ADMIN'

  const nameCell = (u: AdminUser) =>
    editing?.id === u.id ? (
      <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
        <TextField
          size="small"
          value={editing.name}
          onChange={(e) => setEditing({ id: u.id, name: e.target.value })}
          slotProps={{ htmlInput: { maxLength: 120, 'aria-label': ta.ANZEIGENAME_ARIA(u.email) } }}
        />
        <Button size="small" aria-label={ta.NAMEN_SPEICHERN} onClick={saveDisplayName}>
          {t.ALLGEMEIN.SPEICHERN}
        </Button>
        <Button size="small" aria-label={ta.BEARBEITEN_ABBRECHEN} onClick={() => setEditing(null)}>
          {t.ALLGEMEIN.ABBRECHEN}
        </Button>
      </Box>
    ) : (
      <Box sx={{ display: 'flex', gap: 0.5, alignItems: 'center' }}>
        {u.displayName}
        {editMode && (
          <IconButton
            size="small"
            aria-label={ta.NAMEN_BEARBEITEN_ARIA(u.displayName)}
            onClick={() => setEditing({ id: u.id, name: u.displayName })}
          >
            <EditIcon fontSize="inherit" />
          </IconButton>
        )}
      </Box>
    )

  const actionsCell = (u: AdminUser) => (
    <>
      {ansichtMoeglich(u) && (
        <Button
          size="small"
          aria-label={ta.ANSICHT_ALS_ARIA(u.displayName)}
          onClick={() => void ansichtStarten(u)}
          sx={{ mr: 1 }}
        >
          {ta.ANSICHT_ALS}
        </Button>
      )}
      {!u.approvedAt && (
        <Button
          size="small"
          aria-label={ta.FREIGEBEN_ARIA(u.displayName)}
          onClick={() => approve(u)}
          sx={{ mr: 1 }}
        >
          {ta.FREIGEBEN}
        </Button>
      )}
      <Button size="small" aria-label={ta.ROLLE_UMSCHALTEN_ARIA(u.displayName)} onClick={() => toggleRole(u)}>
        {ta.ZU_ROLLE(u.platformRole === 'ADMIN' ? 'USER' : 'ADMIN')}
      </Button>
      {u.id !== currentUser?.userId && (
        <Button
          size="small"
          color={u.disabled ? 'success' : 'error'}
          aria-label={u.disabled ? ta.ENTSPERREN_ARIA(u.displayName) : ta.SPERREN_ARIA(u.displayName)}
          onClick={() => toggleDisabled(u)}
          sx={{ ml: 1 }}
        >
          {u.disabled ? ta.ENTSPERREN : ta.SPERREN}
        </Button>
      )}
    </>
  )

  const columns: DataTableColumn<AdminUser>[] = [
    { key: 'name', header: ta.NAME, render: nameCell, defaultWidth: 180, minWidth: 120, hideable: true, resizable: true },
    { key: 'email', header: ta.EMAIL, render: (u) => u.email, defaultWidth: 200, minWidth: 120, hideable: true, resizable: true },
    { key: 'verified', header: ta.VERIFIZIERT, render: (u) => (u.emailVerified ? ta.JA : ta.NEIN), defaultWidth: 100, hideable: true, resizable: true },
    {
      key: 'approval',
      header: ta.FREIGABE,
      render: (u) => (
        <Chip
          label={u.approvedAt ? ta.FREIGEGEBEN : ta.WARTET_AUF_FREIGABE}
          size="small"
          color={u.approvedAt ? 'success' : 'warning'}
        />
      ),
      defaultWidth: 160,
      hideable: true,
      resizable: true,
    },
    {
      key: 'status',
      header: ta.STATUS,
      render: (u) => (
        <Chip label={u.disabled ? ta.GESPERRT : ta.AKTIV} size="small" color={u.disabled ? 'error' : 'success'} />
      ),
      defaultWidth: 110,
      hideable: true,
      resizable: true,
    },
    {
      key: 'role',
      header: t.ALLGEMEIN.ROLLE,
      render: (u) => (
        <Chip label={u.platformRole} size="small" color={u.platformRole === 'ADMIN' ? 'primary' : 'default'} />
      ),
      defaultWidth: 100,
      hideable: true,
      resizable: true,
    },
    {
      key: 'ansichten',
      header: ta.ANSICHTEN,
      render: (u) => <AnsichtenZelle user={u} api={api} />,
      defaultWidth: 220,
      hideable: true,
      resizable: true,
    },
    { key: 'actions', header: ta.AKTION, render: actionsCell, align: 'right', defaultWidth: 360 },
  ]

  return (
    <Box>
      <Typography variant="h5" gutterBottom>
        {ta.TITEL}
      </Typography>
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

      {/* Eigener Abruf über die eigene Prop (Issue #833): Die `AdminApi` oben bleibt unberührt. */}
      <BackupStatusSection />

      <DataTable
        columns={columns}
        rows={users}
        getRowKey={(u) => u.id}
        getRowTestId={(u) => `admin-user-${u.id}`}
        storageKey="admin-users"
      />

      <OverloadRejectionsSection api={api} />
    </Box>
  )
}

/** Aufklappbare Einträge der Ansichten als dieses Konto (AK 8); sie laden erst beim Aufklappen. */
function AnsichtenZelle({ user, api }: Readonly<{ user: AdminUser; api: AdminApi }>) {
  const ta = useT().ADMIN_SEITE
  const [offen, setOffen] = useState(false)
  const laden = useCallback(() => api.vorschauProtokoll(user.id), [api, user.id])
  return (
    <Box>
      <Button
        size="small"
        aria-expanded={offen}
        aria-label={ta.EINTRAEGE_ARIA(user.displayName)}
        onClick={() => setOffen(!offen)}
      >
        {ta.EINTRAEGE}
      </Button>
      {offen && <VorschauEintraege laden={laden} />}
    </Box>
  )
}
