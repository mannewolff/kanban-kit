# Sonar S2589: tote Bedingungen in BootstrapService entfernen

## Kontext
Sonar S2589 (MAJOR) in `BootstrapService`: Zeile 36 „always false", Zeile 63 „always true" —
redundante/tote Bedingungen.

## Aufgabe
- `BootstrapService.java` Z.36/63: die konstant-wahr/-falsch Bedingungen prüfen und entfernen bzw.
  korrigieren, falls ein Bug dahintersteckt. Verhalten erhalten; Tests grün halten/anpassen.

## Akzeptanzkriterium
- S2589 an beiden Stellen weg; Verhalten unverändert. `mvn verify` grün + PIT 100 %.

## Abhängigkeiten
Keine.
