# Sonar S7467: ungenutztes catch-e durch Unnamed-Pattern ersetzen (beide Event-Registries)

## Kontext
Sonar S7467: der `catch (… e)` in `BoardEventRegistry` (Z.63) nutzt `e` nicht → Java-21-Unnamed-
Pattern `_`. Dieselbe Kopie steckt in `ProjectIdeaEventRegistry` (Ideen-Pool-SSE).

## Aufgabe
- `BoardEventRegistry` und `ProjectIdeaEventRegistry`:
  `catch (IOException | IllegalStateException e)` → `catch (IOException | IllegalStateException _)`.

## Akzeptanzkriterium
- S7467 in beiden Registries weg; `mvn verify` grün + PIT 100 %.

## Abhängigkeiten
Keine.
