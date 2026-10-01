# Rollen & Rechte

kanban-kit hat **zwei** Rollen-Ebenen. Sie werden oft verwechselt — deshalb zuerst die Abgrenzung:

| Ebene | Werte | Gültigkeit | Wo sichtbar |
|------|-------|------------|-------------|
| **Plattform-Rolle** | `USER`, `ADMIN` | instanzweit (ganzes System) | „Admin"-Bereich / Seitenleiste |
| **Projekt-Rolle** | `OWNER`, `ADMIN`, `MEMBER`, `VIEWER` | pro Projekt (Mitgliedschaft) | Mitglieder-Seite eines Projekts |

Ein Nutzer kann also plattformweit **USER** sein und in *seinem* Projekt trotzdem **OWNER** — beides ist
unabhängig. Die Mitglieder-Seite zeigt ausschließlich die **Projekt-Rolle**.

## Projekt-Rollen: Rechte-Matrix

Die Rechte sind granular als **CRUD-Matrix pro Ressource** modelliert (je Ressource und Operation ein
Recht). Der Schlüssel in der zweiten Spalte ist der technische Name des Rechts — er taucht so in der
`/roles`-Ansicht der App auf. Für die vier eingebauten Rollen sind die Rechte fest gesetzt.

**Jede Zeile der Tabelle trägt einen Schlüssel** (Issue #1165). Das ist keine Formalie: Die
`/roles`-Ansicht rendert ausschließlich, was `GET /api/roles/matrix` liefert, und dort steht nur, was
einen Schlüssel hat. Eine Zeile ohne Schlüssel wäre ein Recht, das die App nie zeigt — ein Test hält
die Regel (siehe [Woher die Matrix kommt](#woher-die-matrix-kommt)). Was an keiner Projekt-Rolle
hängt, gehört deshalb nicht in die Tabelle, sondern unter [Sonderregeln](#sonderregeln) bzw.
[Plattform-Rollen](#plattform-rollen).

| Recht | Schlüssel | VIEWER | MEMBER | ADMIN | OWNER |
|-------|-----------|:------:|:------:|:-----:|:-----:|
| Boards und Spalten lesen | `BOARD_READ` | ✓ | ✓ | ✓ | ✓ |
| Board anlegen | `BOARD_CREATE` | – | – | ✓ | ✓ |
| Board umbenennen, Spalten und Board-Labels verwalten | `BOARD_UPDATE` | – | – | ✓ | ✓ |
| Board archivieren / löschen, Karte endgültig löschen | `BOARD_DELETE` | – | – | ✓ | ✓ |
| Vorhaben lesen | `EPIC_READ` | ✓ | ✓ | ✓ | ✓ |
| Vorhaben anlegen | `EPIC_CREATE` | – | ✓ | ✓ | ✓ |
| Vorhaben bearbeiten | `EPIC_UPDATE` | – | ✓ | ✓ | ✓ |
| Vorhaben archivieren / in den Papierkorb legen | `EPIC_DELETE` | – | ✓ | ✓ | ✓ |
| Karten lesen (inkl. Dashboard, Papierkorb) | `TICKET_READ` | ✓ | ✓ | ✓ | ✓ |
| Karte anlegen, Spezifikation einlesen | `TICKET_CREATE` | – | ✓ | ✓ | ✓ |
| Karte bearbeiten (Titel, Text, Zuständige, Fälligkeit, Labels) | `TICKET_UPDATE` | – | ✓ | ✓ | ✓ |
| Karte archivieren, in den Papierkorb legen, wiederherstellen | `TICKET_DELETE` | – | ✓ | ✓ | ✓ |
| Karte verschieben — Spalte, **anderes Board desselben Projekts**; Spalte nach Kartennummer sortieren; Status eines Arbeitspakets setzen (`PUT /api/cards/{cardId}/status`) | `CARD_MOVE` | – | ✓ | ✓ | ✓ |
| **Karte in ein anderes Projekt verschieben** — OWNER in **beiden** Projekten | `CARD_MOVE_PROJECT` | – | – | – | ✓ |
| Kommentare lesen | `COMMENT_READ` | ✓ | ✓ | ✓ | ✓ |
| Kommentar schreiben | `COMMENT_CREATE` | – | ✓ | ✓ | ✓ |
| Kommentar bearbeiten *(nur eigenen)* | `COMMENT_UPDATE` | – | ✓ | ✓ | ✓ |
| Kommentar löschen *(Moderation)* | `COMMENT_DELETE` | – | – | ✓ | ✓ |
| Anhänge lesen / herunterladen | `ATTACHMENT_READ` | ✓ | ✓ | ✓ | ✓ |
| Anhang hochladen | `ATTACHMENT_CREATE` | – | ✓ | ✓ | ✓ |
| Anhang löschen | `ATTACHMENT_DELETE` | – | ✓ | ✓ | ✓ |
| Mitglied einladen | `MEMBER_INVITE` | – | – | ✓ | ✓ |
| Mitglied entfernen, Rolle oder Anzeigename ändern | `MEMBER_REMOVE` | – | – | ✓ | ✓ |
| Projekt umbenennen, Karten-Startnummer setzen | `PROJECT_EDIT` | – | – | – | ✓ |
| Eigentümerschaft übertragen | `PROJECT_OWNER_TRANSFER` | – | – | – | ✓ |
| **Auswertung der Läufe lesen** — Plattform-Admin auch ohne OWNER, aber nur bei Teilnahme am Plattform-Leitstand | `NIGHT_RUN_READ` | – | – | – | ✓ |
| **Protokoll eines Laufs hineingeben** — der Plattform-Admin immer | `NIGHT_RUN_SUBMIT` | – | – | – | ✓ |

Dieselbe Übersicht ist in der App unter **`/roles`** erreichbar (Link „Rollen & Rechte" auf der
Mitglieder-Seite).

### Sonderregeln

Einige Aktionen folgen nicht allein dem Recht aus der Tabelle:

- **Karte auf ein anderes Board verschieben:** Innerhalb *desselben* Projekts genügt `CARD_MOVE`, also
  ab MEMBER. Der Weg in ein *anderes* Projekt trägt `CARD_MOVE_PROJECT` und verlangt die Rolle **OWNER
  in beiden** Projekten — im Quell- **und** im Zielprojekt. Das Recht steht in der Matrix, die
  Bedingung „in beiden" ist die Zusatzbedingung dazu und kein zweites Recht. Grund: Projektgrenzen sind
  die Vertraulichkeitsgrenze; die Karte bekommt dabei eine neue projektweite Nummer, Abhängigkeiten und
  Zuständige entfallen.
- **Teilnahme am Plattform-Leitstand schalten:** kein Recht der Matrix, sondern die **echte
  Projekt-Rolle** `OWNER` oder `ADMIN` — und bewusst **nicht** der Plattform-Admin, obwohl der sonst
  jede Prüfung passiert. Die Teilnahme ist die Einwilligung des Projekts in die Einsicht durch den
  Betreiber, und wer sie einholt, erteilt sie sich nicht selbst. Genau deshalb hat sie keinen
  Schlüssel: Ein Häkchen in einer Rollenspalte müsste für den Plattform-Admin anders gelten als für
  alle anderen.
- **Karte endgültig löschen** (aus dem Papierkorb) hängt bewusst an `BOARD_DELETE`, ist also erst ab
  ADMIN erlaubt — anders als das Legen in den Papierkorb (`TICKET_DELETE`, ab MEMBER). Wiederherstellen
  darf, wer löschen darf: so kann ein MEMBER einen eigenen Fehlgriff selbst korrigieren.
- **Kommentar bearbeiten:** `COMMENT_UPDATE` reicht nicht allein — bearbeiten darf **nur der Autor**.
  Auch ADMIN und OWNER können fremde Kommentare nicht ändern, nur löschen.
- **Lesen** wird serverseitig über die **Mitgliedschaft** im Projekt geprüft, nicht über das jeweilige
  `*_READ`-Recht. Das Ergebnis ist dasselbe, weil alle vier Rollen sämtliche Leserechte haben; die
  Lese-Rechte in der Matrix beschreiben die Absicht und sind für konfigurierbare Rollen (siehe unten)
  vorgesehen.
- **Board-gebundene API-Tokens** siehe [API-Tokens](#api-tokens).

### Rollen im Detail

- **VIEWER:** darf ausschließlich lesen — Boards, Spalten, Vorhaben, Karten, Kommentare, Anhänge.
- **MEMBER:** zusätzlich Karten und Vorhaben anlegen, bearbeiten, verschieben und in den Papierkorb legen,
  Kommentare schreiben (und eigene bearbeiten) sowie Anhänge hochladen und löschen.
- **ADMIN:** zusätzlich Boards, Spalten und Board-Labels verwalten, Karten endgültig löschen,
  Kommentare löschen (Moderation) und Mitglieder einladen/entfernen.
- **OWNER:** alle Projekt-Rechte, dazu Projekt umbenennen, Karten projektübergreifend verschieben,
  die Eigentümerschaft an ein anderes Mitglied übertragen (dabei wird der bisherige Owner zum ADMIN)
  und die [Auswertung der Läufe](nutzung.md#nachtlauf) lesen sowie Protokolle hineingeben. Das alles
  kann bewusst **nur** der Owner, nicht der Projekt-Admin.

  Neben dem Owner passiert allerdings auch der **Plattform-Admin** diese Prüfung — als Super-User
  hat er auf alle Projekte Vollzugriff, siehe [Plattform-Rollen](#plattform-rollen). „Nur der Owner"
  grenzt also gegen die *Projekt*-Rollen ab, nicht gegen die Plattform-Ebene. Bei den Läufen gilt das
  nur eingeschränkt: Der Plattform-Admin liest die Auswertung der Läufe, die Anläufe und den
  Verbrauch eines Projekts ohne eigene Mitgliedschaft **nur, wenn das Projekt am
  Plattform-Leitstand teilnimmt**. Das Protokoll-Hineingeben (Ingest) bleibt davon unberührt.

## Plattform-Rollen

- **USER** — sieht und bearbeitet nur eigene Projekte bzw. Projekte, in denen er Mitglied ist.
- **ADMIN** — **Super-User**: Vollzugriff auf **alle** Projekte (unabhängig von der Mitgliedschaft) und
  **Nutzerverwaltung**.

### Nur mit der Plattform-Rolle ADMIN

Zwei Dinge gewährt **keine** Projekt-Rolle — auch OWNER nicht. Sie stehen deshalb hier und nicht als
Zeile der [Rechte-Matrix](#projekt-rollen-rechte-matrix) oben; eine Zeile, die in jeder Rollenspalte
leer bliebe, sähe aus wie ein vergessenes Häkchen und lüde dazu ein, es einer Rolle zu geben.

- **Plattform-Leitstand ansehen** — die Übersicht über die Läufe aller teilnehmenden Projekte, siehe
  [Plattform-Leitstand](nutzung.md#plattform-leitstand).
- **Projekt anlegen und löschen** — siehe den Absatz darunter.

**Projekte legt ausschließlich der Plattform-Admin an und löscht sie auch nur er.** Beim Anlegen
bestimmt er den **Owner** per E-Mail; dadurch wird er selbst nicht Mitglied, hat als Plattform-Admin
aber ohnehin Vollzugriff. Beim Löschen gibt es eine Sicherheitsabfrage — Boards, Vorhaben und Karten des
Projekts werden mitgelöscht (Kaskade). Dasselbe gilt beim Löschen eines Boards für dessen Vorhaben und
Karten.

## Admin-Bereich (`/admin`)

Nur für Plattform-Admins sichtbar (Eintrag „Admin" in der Seitenleiste):

- **Nutzerliste** (E-Mail, Name, verifiziert, Rolle, Status).
- **Plattform-Rolle umschalten** (USER ↔ ADMIN).
- **Konto sperren / entsperren:** über den Button „Sperren" bzw. „Entsperren" (Status-Chip „Aktiv" /
  „Gesperrt"). Ein gesperrtes Konto kann sich nicht mehr anmelden („Konto gesperrt") und wird auch aus
  bestehenden Sitzungen abgewiesen — das gilt für die Web-Anmeldung **und** für API-/Ingest-Tokens.
- **Schutz:** der letzte **nicht gesperrte** Plattform-Administrator kann weder herabgestuft noch
  gesperrt werden (kein Aussperren). Konten mit Administrator-Rolle zählen dabei **nicht** mit,
  solange sie gesperrt sind — ein gesperrter Admin hält die Plattform also nicht offen. Daneben gilt
  unverändert: man kann sich **nicht selbst** sperren. Analog kann der letzte OWNER eines Projekts
  weder degradiert noch entfernt werden.

Den ersten Admin richtet man über den Bootstrap-Token oder direkt in der DB ein — siehe
[Betrieb → Den ersten Admin einrichten](betrieb.md#den-ersten-admin-einrichten).

> **Nicht zu verwechseln:** Der Sidebar-Eintrag **„Administration"** (`/administration`) ist für
> **alle** angemeldeten Nutzer da und enthält den [Editiermodus](nutzung.md#editiermodus)-Schalter.
> Der **„Admin"-Bereich** (`/admin`) oben ist ausschließlich für Plattform-Admins. Der Editiermodus
> vergibt keine Rechte — er blendet nur die Bearbeiten-Symbole ein, sofern die Rolle das Recht
> ohnehin hat.

## Woher die Matrix kommt

Die Matrix ist die einzige Quelle der Wahrheit und wird serverseitig geliefert
(`GET /api/roles/matrix`: Rollen, Rechte mit Ressource/Operation, Grants je Rolle). Die
`/roles`-Ansicht rendert sie als **Checkbox-Grid**: Spalten = einzelne Rechte, Zeilen = Rollen. Für die
vier eingebauten Rollen sind die Haken **read-only**.

Diese Seite hier ist die einzige Rechte-Übersicht im Repository; ein Test hält sie an das
`Permission`-Enum gekoppelt, damit Code und Doku nicht auseinanderlaufen — in **beide** Richtungen:
Jedes Recht des Enums braucht eine Zeile, und jede Zeile der Matrix braucht einen Schlüssel
(`RightsDocumentationTest`, Issue #1165).

**Ausblick:** Frei konfigurierbare Zusatzrollen sind vorgesehen — dieselbe Matrix, weitere Zeilen mit
aktivierbaren Haken, ohne Änderung an den vier eingebauten Rollen.

## API-Tokens

Persönliche API-Tokens (für die Kanban-Compat-API / CLIs) erzeugt und widerruft man unter
**Administration → API-Tokens** (siehe [Dogfooding](dogfooding.md)). Ein **board-gebundenes** Token
darf nur anlegen, wer auf dem betreffenden Board das Recht hat, **Karten anzulegen** (`TICKET_CREATE`)
— ein VIEWER also nicht. Damit kann ein Token nie mehr als sein Ersteller: das Board über die API zu
treiben (Karten anlegen/verschieben) entspricht genau dem Recht, das man dafür ohnehin bräuchte.

Ein **board-gebundenes** Token kann ausschließlich die Kanban-Compat-API (`/api/kanban/**`) seines
gebundenen Boards bedienen; jeder andere `/api/**`-Zugriff wird mit **403** abgewiesen — unabhängig
von den Rollen des Erstellers, auch bei einem Plattform-Admin. Ein **ungebundenes** Token bleibt in
seinem bisherigen Umfang nutzbar.
