# Coverage-Lücke schließen: ProjectStartNumberController-Test (SonarCloud 100 %)

> Tracker war beim Anlegen im Deploy/flappte — Datei zum Nachziehen.

## Kontext
SonarCloud zeigt 99,9 % Coverage — die 8 nicht abgedeckten Zeilen liegen alle in
`ProjectStartNumberController` (#390-Endpoint). Lokal grün, weil das JaCoCo-Gate nur
`application.*`/`domain.*` erzwingt; der Web-Controller wird von SonarCloud gemessen, hat aber nur
einen IT (in `CardIT`), keinen dedizierten Controller-Test.

## Aufgabe
- Neu: `ProjectStartNumberControllerTest` — deckt GET + PUT sowie die Records
  `NextCardNumberRequest`/`NextCardNumberView` ab (Erfolg; 400 bei zu kleinem Wert bzw. `@Min`;
  403/404 für Nichtmitglied), sodass keine Controller-Zeile ungedeckt bleibt. Der Endpoint-IT in
  `CardIT` bleibt.

## Akzeptanzkriterium
- `ProjectStartNumberController` 0 uncovered lines; SonarCloud-Coverage zurück auf 100 %.
- `mvn verify` grün (JaCoCo 100/100, ArchUnit, ITs) + PIT 100 %.

## Abhängigkeiten
Keine.
