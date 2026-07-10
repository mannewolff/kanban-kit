# kanban-kit — Dokumentation

kanban-kit ist ein eigenständiges, selbst-hostbares Kanban-Board (Trello-Alternative) mit
Projekten, mehreren Boards, dynamischen Spalten, Karten, Epics, Kommentaren, Anhängen und
einer rollenbasierten Rechteverwaltung.

Diese Dokumentation ist die **Benutzer- und Betriebsdokumentation**. Sie wird laufend mit dem
Funktionsstand nachgezogen.

## Inhalt

- [Betrieb & Installation](betrieb.md) — Start via Docker, Umgebungsvariablen, erster Admin, E-Mail/Verifikation.
- [Nutzung](nutzung.md) — Registrieren, Projekte, Boards, Karten, Listen-Ansicht, Anhänge, Epics, Mitglieder.
- [Rollen & Rechte](rollen-und-rechte.md) — Plattform- vs. Projekt-Rollen, Rechte-Matrix, Admin-Bereich.

## Kurzüberblick der Ebenen

| Ebene | Beispiele | Wo |
|------|-----------|----|
| **Plattform-Rolle** | USER, ADMIN (Super-User) | instanzweit, `app_user.platform_role` |
| **Projekt-Rolle** | OWNER, ADMIN, MEMBER, VIEWER | pro Projekt, Mitgliedschaft |
| **Struktur** | Projekt → Board → Spalte → Karte | Karten können einem **Epic** zugeordnet sein |
