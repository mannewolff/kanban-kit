package org.mwolff.manban.project.application;

/**
 * Anwendungs-Event: ein Projekt wurde angelegt (inkl. Owner-Mitgliedschaft). Wird von {@link
 * ProjectService#create} über den Spring-{@code ApplicationEventPublisher} publiziert, damit andere
 * Module reagieren können, ohne dass {@code project} sie kennen muss (Vermeidung eines
 * Modul-Zyklus). Der Board-seitige {@code DefaultBoardCreator} legt darauf synchron — im selben
 * Transaktions-Scope — das Default-Board an; scheitert das, rollt die Projektanlage atomar mit
 * zurück.
 *
 * @param projectId das neu angelegte Projekt
 * @param ownerUserId der als OWNER eingetragene Nutzer
 */
public record ProjectCreatedEvent(long projectId, long ownerUserId) {}
