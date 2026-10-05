import { apiFetch } from './client'

/**
 * Maschinenlesbare Beschreibung der Schnittstelle (OpenAPI 3, Issue #1410; Plan #1400 E15).
 *
 * Die Spezifikation läuft über {@link apiFetch} statt über Swagger UIs eigenes Laden per `url`:
 * Nur so greifen der anwendungsweite 401-Haken und die `ApiError`-Fehleranzeige wie bei den
 * übrigen API-Modulen. Die Download-Links nutzen die Pfade direkt.
 */

/** Pfad der Beschreibung als JSON. */
export const OPENAPI_JSON_PFAD = '/api/openapi'

/** Pfad der Beschreibung als YAML. */
export const OPENAPI_YAML_PFAD = '/api/openapi.yaml'

export const openapiApi = {
  /** Holt die JSON-Spezifikation; der Inhalt geht unverändert an Swagger UI. */
  lade: () => apiFetch<object>(OPENAPI_JSON_PFAD),
}

export type OpenapiApi = typeof openapiApi
