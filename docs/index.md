# kanban-kit — Dokumentation

kanban-kit ist ein eigenständiges, selbst-hostbares Kanban-Board (Trello-Alternative) mit
Projekten, mehreren Boards, dynamischen Spalten, Karten (mit Zuständigen, Fälligkeit und Labels),
Vorhaben, Kommentaren, Anhängen, einem Papierkorb, einem KPI-Dashboard und einer rollenbasierten
Rechteverwaltung.

Diese Dokumentation ist die **Benutzer- und Betriebsdokumentation**. Sie wird laufend mit dem
Funktionsstand nachgezogen.

## Inhalt

- [Betrieb & Installation](betrieb.md) — Start via Docker, Umgebungsvariablen, erster Admin, E-Mail/Verifikation,
  [Meldeweg der interaktiven Sitzungen](betrieb.md#meldeweg-der-interaktiven-sitzungen) (Token,
  Hook, Erfassungsbeginn, Aufbewahrungsgrenzen, Worktree-Einschränkung).
- [Produktions-Deployment (Hostinger)](deployment-hostinger.md) — öffentlicher Betrieb hinter Traefik unter `kanban.mwolff.org`.
- [Nutzung](nutzung.md) — Registrieren, Projekte, Boards, Karten (Zuständige, Fälligkeit, Labels),
  Papierkorb, Listen-Ansicht, Ideen-Pool, Nachtlauf,
  [Verbrauch im Leitstand](nutzung.md#verbrauch-leitstand) (Gattungen, „ohne Karte", Lebenszeit-Summe,
  Erfassungslücken), Dashboard, Vorhaben, Mitglieder, Editiermodus.
- [Rollen & Rechte](rollen-und-rechte.md) — Plattform- vs. Projekt-Rollen, Rechte-Matrix, Admin-Bereich (inkl. Konten sperren).
- [Befund: Verbrauchsangaben, Hook-Ereignisse und Worktrees](befund-interaktive-sitzungen.md) —
  was das Sitzungsprotokoll an Verbrauch führt, welche Hook-Ereignisse es gibt und was ein frischer
  Worktree mitbekommt.

## Kurzüberblick der Ebenen

| Ebene | Beispiele | Wo |
|------|-----------|----|
| **Plattform-Rolle** | USER, ADMIN (Super-User) | instanzweit, `app_user.platform_role` |
| **Projekt-Rolle** | OWNER, ADMIN, MEMBER, VIEWER | pro Projekt, Mitgliedschaft |
| **Struktur** | Projekt → Board → Spalte → Karte | Karten können einem **Vorhaben** zugeordnet sein; was aus einer zugeordneten Karte entsteht, gehört automatisch dazu |
