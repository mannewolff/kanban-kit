import { defineConfig } from "vitepress";

// VitePress-Setup analog zu docs.mwolff.org (claude-workflow-kit).
// Die Markdown-Inhalte liegen im Repo unter ../docs (getrennt vom Build-Setup).
export default defineConfig({
  title: "kanban-kit",
  description:
    "Selbst-hostbares Kanban-Board mit Projekten, Boards, Epics, Anhängen und rollenbasierter Rechteverwaltung — Benutzer- und Betriebsdokumentation.",
  appearance: false,
  // Ausgeliefert von Spring Boot unter /docs/ (in die App gebündelt, #314); alle Asset-Pfade
  // müssen deshalb unter /docs/ auflösen.
  base: "/docs/",
  // Dev liest direkt aus ../docs (Live-Reload). Der statische Build läuft über die nach content/
  // kopierten Quellen (VITEPRESS_SRC=content), weil ein srcDir außerhalb des Projektroots den
  // Build bricht (VitePress-Issue #2713, siehe copy-docs.mjs).
  srcDir: process.env.VITEPRESS_SRC ?? "../docs",
  outDir: ".vitepress/dist",

  themeConfig: {
    nav: [
      { text: "Überblick", link: "/" },
      { text: "Betrieb", link: "/betrieb" },
      { text: "Nutzung", link: "/nutzung" },
      { text: "Rollen & Rechte", link: "/rollen-und-rechte" },
      { text: "Dogfooding", link: "/dogfooding" },
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
          { text: "Karten-Detail", link: "/nutzung#karten-detail" },
          { text: "Labels", link: "/nutzung#labels" },
          { text: "Papierkorb", link: "/nutzung#papierkorb" },
          { text: "Listen-Ansicht", link: "/nutzung#listen-ansicht" },
          { text: "Dashboard (Kennzahlen)", link: "/nutzung#dashboard-kennzahlen" },
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
      {
        text: "Dogfooding",
        items: [
          { text: "Eigenes Board anbinden", link: "/dogfooding" },
        ],
      },
    ],

    socialLinks: [
      { icon: "github", link: "https://github.com/mannewolff/kanban-kit" },
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
