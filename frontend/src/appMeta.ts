/**
 * Anzeigename der Anwendung, an einer Stelle.
 *
 * Der technische Name des Projekts bleibt vorerst `manban` beziehungsweise `kanban-kit` — Repo,
 * Java-Paket, Umgebungsvariablen, Docker-Images und der `localStorage`-Namensraum sind unberührt.
 * Das vollständige Umbenennen ist ein eigenes Vorhaben (#618) und ausdrücklich vertagt: Es braucht
 * einen Migrationspfad für gespeicherte Ansichtseinstellungen, sonst verlieren alle Nutzer sie
 * stillschweigend.
 *
 * **Deshalb steht der Name hier und nicht viermal im Code verteilt.** Vor dieser Konstante trugen
 * ihn die Kopfzeile, die Anmeldekarte, der Lizenzhinweis und der Browser-Tab jeweils als
 * eigenes Literal; ein späterer Rename hätte sie einzeln finden müssen. Der Titel in
 * `index.html` bleibt als Literal bestehen — statisches HTML kann kein Modul lesen.
 */
export const APP_NAME = 'KI-Leitstand'
