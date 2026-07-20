# Rollen & Rechte

kanban-kit hat **zwei** Rollen-Ebenen. Sie werden oft verwechselt — deshalb zuerst die Abgrenzung:

| Ebene | Werte | Gültigkeit | Wo sichtbar |
|------|-------|------------|-------------|
| **Plattform-Rolle** | `USER`, `ADMIN` | instanzweit (ganzes System) | „Admin"-Bereich / Seitenleiste |
| **Projekt-Rolle** | `OWNER`, `ADMIN`, `MEMBER`, `VIEWER` | pro Projekt (Mitgliedschaft) | Mitglieder-Seite eines Projekts |

Ein Nutzer kann also plattformweit **USER** sein und in *seinem* Projekt trotzdem **OWNER** — beides ist
unabhängig. Die Mitglieder-Seite zeigt ausschließlich die **Projekt-Rolle**.

## Projekt-Rollen: Rechte-Matrix

| Recht | VIEWER | MEMBER | ADMIN | OWNER |
|-------|:------:|:------:|:-----:|:-----:|
| Boards & Karten lesen (inkl. Dashboard) | ✓ | ✓ | ✓ | ✓ |
| Karten anlegen / verschieben / bearbeiten | – | ✓ | ✓ | ✓ |
| Zuständige, Fälligkeit & Labels an Karten setzen | – | ✓ | ✓ | ✓ |
| Board-Labels verwalten (anlegen / ändern / löschen) | – | ✓ | ✓ | ✓ |
| Karte in den Papierkorb legen / wiederherstellen | – | ✓ | ✓ | ✓ |
| Karte endgültig löschen (Papierkorb) | – | – | ✓ | ✓ |
| Kommentare schreiben | – | ✓ | ✓ | ✓ |
| Anhänge hochladen | – | ✓ | ✓ | ✓ |
| Spalten bearbeiten | – | – | ✓ | ✓ |
| Boards anlegen / löschen | – | – | ✓ | ✓ |
| Mitglieder einladen / entfernen | – | – | ✓ | ✓ |
| Projekt umbenennen / löschen | – | – | – | ✓ |

Dieselbe Übersicht ist in der App unter **`/roles`** erreichbar (Link „Rollen & Rechte" auf der
Mitglieder-Seite).

## Plattform-Rollen

- **USER** — sieht und bearbeitet nur eigene Projekte bzw. Projekte, in denen er Mitglied ist.
- **ADMIN** — **Super-User**: Vollzugriff auf **alle** Projekte (unabhängig von der Mitgliedschaft) und
  **Nutzerverwaltung**.

## Admin-Bereich (`/admin`)

Nur für Plattform-Admins sichtbar (Eintrag „Admin" in der Seitenleiste):

- **Nutzerliste** (E-Mail, Name, verifiziert, Rolle, Status).
- **Plattform-Rolle umschalten** (USER ↔ ADMIN).
- **Konto sperren / entsperren:** über den Button „Sperren" bzw. „Entsperren" (Status-Chip „Aktiv" /
  „Gesperrt"). Ein gesperrtes Konto kann sich nicht mehr anmelden („Konto gesperrt") und wird auch aus
  bestehenden Sitzungen abgewiesen — das gilt für die Web-Anmeldung **und** für API-/Ingest-Tokens.
- **Schutz:** der **letzte** Plattform-Admin kann nicht degradiert werden (kein Aussperren); man kann
  sich zudem **nicht selbst** sperren.

Den ersten Admin richtet man über den Bootstrap-Token oder direkt in der DB ein — siehe
[Betrieb → Den ersten Admin einrichten](betrieb.md#den-ersten-admin-einrichten).

> **Nicht zu verwechseln:** Der Sidebar-Eintrag **„Administration"** (`/administration`) ist für
> **alle** angemeldeten Nutzer da und enthält den [Editiermodus](nutzung.md#editiermodus)-Schalter.
> Der **„Admin"-Bereich** (`/admin`) oben ist ausschließlich für Plattform-Admins. Der Editiermodus
> vergibt keine Rechte — er blendet nur die Bearbeiten-Symbole ein, sofern die Rolle das Recht
> ohnehin hat.

## API-Tokens

Persönliche API-Tokens (für die Kanban-Compat-API / CLIs) erzeugt und widerruft man unter
**Administration → API-Tokens** (siehe [Dogfooding](dogfooding.md)). Ein **board-gebundenes** Token
darf nur anlegen, wer auf dem betreffenden Board das Recht hat, **Karten anzulegen** (`TICKET_CREATE`)
— ein VIEWER also nicht. Damit kann ein Token nie mehr als sein Ersteller: das Board über die API zu
treiben (Karten anlegen/verschieben) entspricht genau dem Recht, das man dafür ohnehin bräuchte.
