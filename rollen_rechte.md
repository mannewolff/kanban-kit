# Spezifikation Rollen und Rechte

## Rollen

Es gibt drei Ebenen von Rollen:

1. **Systemebene (Plattform-Rolle):** `ADMIN` und `USER`. Jeder User kann zum Admin
   gemacht werden, aber nur von einem anderen Admin. Der System-Admin ist Super-User:
   er sieht und bearbeitet alle Projekte und legt Projekte an bzw. löscht sie.
2. **Projekt-Rollen (fertige Rollen mit festen Rechten):** `Viewer`, `Member`, `Admin`,
   `Owner`. Sie gelten je Projekt (eine Mitgliedschaft = ein User + eine Rolle je Projekt).
3. **Zusatzrollen (Version 2.0, noch nicht umsetzen):** Ein Admin kann weitere Rollen
   anlegen und ihnen Rechte über dieselbe Matrix zuweisen. Diese hängen als weitere
   Zeile hinter Viewer/Member/Admin/Owner.

## Rechte

Die Rechte sind granular als **CRUD-Matrix pro Ressource** modelliert (je Operation ein
Recht). Für die vier Standardrollen sind sie statisch gesetzt; ab 2.0 sind sie für
Zusatzrollen konfigurierbar (dieselben Spalten, dann als aktivierbare Haken).

Ressourcen und Operationen:
- **Board:** Create, Read, Update, Delete (Update umfasst auch das Bearbeiten der Spalten)
- **Epic:** Create, Read, Update, Delete
- **Ticket:** Create, Read, Update, Delete
- **Karten verschieben (Move):** eigene Operation
- **Kommentar:** Create, Read, Update, Delete (mit Sonderregeln, s. u.)
- **Anhang:** Create, Read, Delete
- **Verwaltung:** Mitglieder einladen/entfernen, Projekt umbenennen

### Rechte-Matrix (Standardrollen)

| Recht | Viewer | Member | Admin | Owner |
|---|:--:|:--:|:--:|:--:|
| Board – Read | ✓ | ✓ | ✓ | ✓ |
| Board – Create / Update / Delete | – | – | ✓ | ✓ |
| Epic – Read | ✓ | ✓ | ✓ | ✓ |
| Epic – Create / Update / Delete | – | ✓ | ✓ | ✓ |
| Ticket – Read | ✓ | ✓ | ✓ | ✓ |
| Ticket – Create / Update / Delete | – | ✓ | ✓ | ✓ |
| Karten verschieben (Move) | – | ✓ | ✓ | ✓ |
| Kommentar – Read | ✓ | ✓ | ✓ | ✓ |
| Kommentar – Create | – | ✓ | ✓ | ✓ |
| Kommentar – Update *(nur eigener)* | – | ✓ | ✓ | ✓ |
| Kommentar – Delete | – | – | ✓ | ✓ |
| Anhang – Read | ✓ | ✓ | ✓ | ✓ |
| Anhang – Create | – | ✓ | ✓ | ✓ |
| Anhang – Delete | – | ✓ | ✓ | ✓ |
| Mitglieder einladen / entfernen | – | – | ✓ | ✓ |
| Projekt umbenennen | – | – | – | ✓ |
| **Projekt anlegen / löschen** | **nur System-Admin (Plattform-Ebene, keine Projekt-Rolle)** | | | |

### Rollen im Detail

- **Viewer:** darf ausschließlich lesen (Boards, Epics, Tickets, Kommentare, Anhänge).
- **Member:** zusätzlich Tickets und Epics anlegen, bearbeiten, löschen und verschieben,
  Kommentare schreiben (und eigene bearbeiten) sowie Anhänge hochladen und löschen.
- **Admin:** zusätzlich Boards und Spalten verwalten, Kommentare löschen (Moderation)
  und Mitglieder einladen/entfernen.
- **Owner:** alle Projekt-Rechte inklusive Projekt umbenennen.

## Projekte

Projekte werden **ausschließlich vom System-Admin** angelegt. Beim Anlegen bestimmt der
Admin den **Owner** des Projekts per E-Mail (der genannte User wird OWNER; der Admin ist
dadurch nicht automatisch Mitglied, hat aber als Plattform-Admin ohnehin Vollzugriff).
Löschen darf ebenfalls nur der System-Admin. Es gibt eine Sicherheitsabfrage; beim Löschen
werden alle zugehörigen Boards, Epics und Tickets mitgelöscht (Kaskade).

## Boards

**Owner und Projekt-Admins** können Boards anlegen, umbenennen (Update, inkl. Spalten) und
löschen. Beim Löschen gibt es eine Sicherheitsabfrage; alle zugehörigen Epics und Tickets
werden mitgelöscht (Kaskade).

## Tickets / Epics

Ab **Member** dürfen Tickets und Epics angelegt, bearbeitet und gelöscht sowie Karten auf
dem Board verschoben werden. Read gilt für alle Rollen (inkl. Viewer).

## Kommentare

Kommentare sind voll CRUD, aber mit Eigentums-/Rollenregeln:
- **Create:** ab Member.
- **Read:** alle Rollen.
- **Update:** **nur der Ersteller** des Kommentars (auch ein Admin/Owner darf fremde
  Kommentare nicht bearbeiten).
- **Delete:** **nur Projekt-Admin/Owner** (Moderation); der Autor allein löscht nicht.

## Anhänge

Ab **Member** dürfen Anhänge hochgeladen (Create) und gelöscht (Delete) werden; Read gilt
für alle Rollen.

## Anzeige der Rechte-Matrix

Die Matrix ist die einzige Quelle der Wahrheit und wird serverseitig geliefert
(`GET /api/roles/matrix`: Rollen, Rechte mit Ressource/Operation, Grants je Rolle). Die
`/roles`-Ansicht rendert sie als **Checkbox-Grid**: Spalten = einzelne Rechte (je Operation
eine Spalte), Zeilen = Rollen. Für die vier eingebauten Rollen sind die Haken **disabled**
(read-only). In 2.0 wird eine neue Rolle einfach als weitere Zeile angehängt, deren Haken
dann **aktivierbar** sind.

## Navigation / Sichtbarkeit

Es wird nichts angezeigt, was man nicht auswählen kann:
- „Projekte" erscheint links nur, wenn der Nutzer **≥ 2** Projekte sehen kann.
- „Boards" erscheint nur, wenn das Projekt **≥ 2** Boards hat.
- Bei genau einem Projekt bzw. Board wird direkt dorthin geleitet; die Zwischenebene
  entfällt. Anlege-Einstiege bleiben für Berechtigte erreichbar (Owner/Admin: „+ neues
  Board", System-Admin: „+ neues Projekt").

## Ausblick Version 2.0

Zusätzliche, frei konfigurierbare Rollen: dieselbe Matrix, weitere Zeilen, aktivierbare
Haken pro Recht — ohne Änderung an den bestehenden vier Standardrollen.
