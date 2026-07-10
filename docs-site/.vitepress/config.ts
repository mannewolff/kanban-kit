import { defineConfig } from "vitepress";

// VitePress-Setup analog zu docs.mwolff.org (claude-workflow-kit).
// Die Markdown-Inhalte liegen im Repo unter ../docs (getrennt vom Build-Setup).
export default defineConfig({
  title: "kanban-kit",
  description:
    "Selbst-hostbares Kanban-Board mit Projekten, Boards, Epics, Anhängen und rollenbasierter Rechteverwaltung — Benutzer- und Betriebsdokumentation.",
  appearance: false,
  srcDir: "../docs",
  outDir: ".vitepress/dist",

  themeConfig: {
    nav: [
      { text: "Überblick", link: "/" },
      { text: "Betrieb", link: "/betrieb" },
      { text: "Nutzung", link: "/nutzung" },
      { text: "Rollen & Rechte", link: "/rollen-und-rechte" },
    ],

    sidebar: [
      {
        text: "Einstieg",
        items: [{ text: "Überblick", link: "/" }],
      },
      {
        text: "Betrieb & Installation",
        items: [
          { text: "Start & Aufruf", link: "/betrieb" },
          { text: "Umgebungsvariablen", link: "/betrieb#umgebungsvariablen" },
          { text: "E-Mail-Bestätigung", link: "/betrieb#e-mail-bestatigung-ohne-mailserver" },
          { text: "Ersten Admin einrichten", link: "/betrieb#den-ersten-admin-einrichten" },
        ],
      },
      {
        text: "Nutzung",
        items: [
          { text: "Registrieren & Anmelden", link: "/nutzung#registrieren-anmelden" },
          { text: "Projekte, Boards, Karten", link: "/nutzung#karten" },
          { text: "Listen-Ansicht", link: "/nutzung#listen-ansicht" },
          { text: "Epics", link: "/nutzung#epics" },
          { text: "Mitglieder", link: "/nutzung#mitglieder" },
        ],
      },
      {
        text: "Rollen & Rechte",
        items: [
          { text: "Projekt- & Plattform-Rollen", link: "/rollen-und-rechte" },
          { text: "Rechte-Matrix", link: "/rollen-und-rechte#projekt-rollen-rechte-matrix" },
          { text: "Admin-Bereich", link: "/rollen-und-rechte#admin-bereich-admin" },
        ],
      },
    ],

    socialLinks: [
      { icon: "github", link: "https://github.com/mannewolff/manban" },
    ],

    footer: {
      message: "kanban-kit",
      copyright: "© Manfred Wolff · mwolff.org",
    },

    search: {
      provider: "local",
    },
  },
});
