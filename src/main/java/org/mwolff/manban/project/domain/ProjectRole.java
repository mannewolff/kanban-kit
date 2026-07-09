package org.mwolff.manban.project.domain;

/** Rolle eines Benutzers innerhalb eines Projekts (Projekt-RBAC). */
public enum ProjectRole {
    OWNER,
    ADMIN,
    MEMBER,
    VIEWER
}
