# kanban-kit — Dokumentation

kanban-kit ist ein eigenständiger, selbst-hostbarer **KI-Leitstand mit Kanban-Board**.

Auf dem **Board** wird Arbeit beschrieben: Projekte, mehrere Boards, dynamische Spalten, Karten
(mit Zuständigen, Fälligkeit und Labels), Vorhaben, Kommentare, Anhänge, ein Papierkorb und eine
rollenbasierte Rechteverwaltung.

Im **Leitstand** steht, was die KI daraus gemacht hat: die [Läufe](nutzung.md#nachtlauf) eines
Nacht-Runners mit ihrem Ausgang und dem Befund je Arbeitspaket, der
[Plattform-Leitstand](nutzung.md#plattform-leitstand) mit aktiven Läufen, beendeten Läufen und
Störungen über alle teilnehmenden Projekte, und die
[Verbrauchsauswertung](nutzung.md#verbrauch-leitstand) mit Token und Kosten je Zeitraum, Vorhaben
und Stufe der Kette.

Diese Dokumentation ist die **Benutzer- und Betriebsdokumentation**. Sie wird laufend mit dem
Funktionsstand nachgezogen; der Stand dieser Seite ist **Version 2.4.0** (21.09.2026).

Die Designsprache der Oberfläche — „Kupferwarte“, helles und dunkles Erscheinungsbild ohne
Schalter — steht in `CLAUDE-design.md` im Wurzelverzeichnis.

## Inhalt

- [Betrieb & Installation](betrieb.md) — Start via Docker, Umgebungsvariablen, erster Admin, E-Mail/Verifikation,
  [Meldeweg der interaktiven Sitzungen](betrieb.md#meldeweg-der-interaktiven-sitzungen) (Token,
  Hook, Erfassungsbeginn, Aufbewahrungsgrenzen, Worktree-Einschränkung).
- [Produktions-Deployment (Hostinger)](deployment-hostinger.md) — öffentlicher Betrieb hinter Traefik unter `kanban.mwolff.org`.
- [Nutzung](nutzung.md) — Registrieren, Projekte, Boards, Karten (Zuständige, Fälligkeit, Labels),
  Papierkorb, Listen-Ansicht, Ideen-Pool, [Läufe](nutzung.md#nachtlauf) (Laufarten, vier Zustände
  je Arbeitspaket, Übernahmetext, Aufbewahrung),
  [Verbrauch im Leitstand](nutzung.md#verbrauch-leitstand) (Gattungen, „ohne Karte", Lebenszeit-Summe,
  Kosten je Stufe der Kette, Erfassungslücken),
  [Plattform-Leitstand](nutzung.md#plattform-leitstand) (aktive Läufe, beendete Läufe mit ihrem
  Ausgang, Störungen, Stillefrist, Teilnahme), [Leitstand eines Boards](nutzung.md#leitstand)
  (Laufband, Kennzahl-Kacheln, Rumpf), Vorhaben, Mitglieder, Editiermodus.
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
