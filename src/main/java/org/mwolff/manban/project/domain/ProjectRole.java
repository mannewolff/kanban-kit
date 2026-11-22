package org.mwolff.manban.project.domain;

/** Rolle eines Benutzers innerhalb eines Projekts (Projekt-RBAC). */
public enum ProjectRole {
  OWNER,
  ADMIN,
  MEMBER,
  VIEWER,
  /**
   * Kunde eines Anbieters (Plan #2206, E1): die Rechte eines Mitglieds, aber ohne {@link
   * Permission#REPAIR_READ} — Störungen beim Anbieter bleiben ihm verborgen. Hält wie OWNER und
   * ADMIN einen eigenen App-Zugang ({@code PermissionChecker#istEchterAppInhaber}).
   */
  CUSTOMER
}
