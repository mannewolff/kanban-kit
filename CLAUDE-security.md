# CLAUDE-security.md — Security & Compliance

Verbindliche Sicherheits-Regeln für Spring-Boot-Backend und React-Frontend. Diese Datei hat in Konflikten Vorrang vor Komfort- oder Performance-Erwägungen.

---

## 🔒 Grundprinzipien (nicht verhandelbar)

1. **Keine Secrets im Frontend.** Nichts mit `VITE_*`-Prefix darf vertraulich sein.
2. **Keine Secrets im Repo.** `.env`, `application-*.yml` mit echten Passwörtern, Keys oder Tokens sind gitignored — `application.yml` enthält ausschließlich Defaults mit Platzhaltern (`${VAR:default}`).
3. **Server-seitig autorisieren.** Clientseitige Checks sind UX, keine Sicherheit.
4. **Validieren statt vertrauen.** Jeder Wert aus Header/Body/Query/Path ist untrusted, bis er via Bean Validation oder Type-Guard gegengeprüft wurde.
5. **Prepared Statements / Parameter-Bindung überall.** Keine Ausnahme.
6. **Generische Fehler nach außen.** Stacktraces, SQL-Texte, Klassenpfade bleiben intern. `GlobalExceptionHandler` antwortet mit strukturierten, knappen Fehler-DTOs.

---

## 🔐 Frontend-Security

### Secrets & Tokens

- **Niemals** echte Geheimnisse als `VITE_*`-Variable. Alles dort landet im Client-Bundle.
- Auth läuft über ein vom Spring-Backend gesetztes `HttpOnly`-Session-Cookie — **kein** Auth-Token in `localStorage`/`sessionStorage`. Das Frontend hält keinerlei Session-Token im JS-zugänglichen Speicher (Details unten).
- Session-Gültigkeit prüft das Backend; die UI darf darauf vertrauen, dass abgelaufene/ungültige Cookies mit `401` abgelehnt werden.

### Session-Cookie — Sicherheitsmodell

**Ist-Stand:** Nach dem Login setzt das Backend ein **signiertes, zustandsloses Session-Cookie** ([`SessionCookieManager`](src/main/java/org/mwolff/manban/auth/web/security/SessionCookieManager.java), [`SignedSessionTokens`](src/main/java/org/mwolff/manban/auth/infrastructure/security/SignedSessionTokens.java)). Der Cookie-Wert ist per HMAC (`AuthProperties.sessionSecret`) signiert; es gibt **keine** Server-Session und **kein** Token im JS-zugänglichen Storage. Ein externer Identity-Provider (Keycloak/OIDC) wird bewusst nicht eingesetzt.

**Eigenschaften des Cookies:**
- `HttpOnly` — per JavaScript nicht lesbar; ein XSS kann das Session-Token nicht exfiltrieren.
- `SameSite=Strict` — wird nie cross-site gesendet; damit ist der zustandslose Cookie-Ansatz CSRF-resistent, ein CSRF-Synchronizer-Token entfällt bewusst (siehe [`SecurityConfig`](src/main/java/org/mwolff/manban/auth/infrastructure/security/SecurityConfig.java)).
- `Secure` (Default `true` über `AuthProperties.cookieSecure`; TLS terminiert der Caddy-Reverse-Proxy) — nur über HTTPS.
- Begrenzte Gültigkeit über `AuthProperties.sessionTtl`; abgelaufene/ungültige Cookies werden vom `SessionAuthenticationFilter` mit `401` abgewiesen.
- **Sitzungs-Generation** — das Token trägt die kontogebundene Generation seiner Ausstellung ([`SessionGenerations`](src/main/java/org/mwolff/manban/auth/application/SessionGenerations.java)); weicht sie von der aktuellen Generation des Kontos ab, gilt das Token nicht mehr. Ein **Passwort-Reset zählt die Generation hoch und beendet damit alle Sitzungen des Kontos** — auf jedem Gerät, auch dem auslösenden. Wer sich mit dem alten Passwort angemeldet hatte, verliert den Zugang in dem Moment, in dem der rechtmäßige Besitzer ein neues Passwort vergibt. Das bloße Anfordern eines Resets beendet noch nichts; erst das Einlösen des Einmal-Tokens zählt hoch. **Projektgebundene Ingest-Access-Tokens bleiben davon unberührt** — sie hängen nicht an einer Anmeldesitzung (siehe *Access-Tokens (Ingest)* unten).

**Passwörter:** ausschließlich über Spring Securitys `PasswordEncoder` (**Argon2id**, `Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()`, siehe [`AuthConfig`](src/main/java/org/mwolff/manban/auth/infrastructure/AuthConfig.java)) gehasht — nie im Klartext gespeichert, übertragen oder geloggt.

**Registrierung / E-Mail-Verifikation / Passwort-Reset:** über einmalige, zeitlich begrenzte, nicht ratbare Tokens ([`SecureTokens`](src/main/java/org/mwolff/manban/common/SecureTokens.java)), per Mail zugestellt. Der erste Plattform-Admin wird über einen Bootstrap-Token angelegt.

**Access-Tokens (Ingest):** Der Kanban-kompatible Ingest ohne Login nutzt projektgebundene Access-Tokens (`accesstoken` + `kanbancompat`), getrennt vom Session-Login. Die Token-Verwaltung selbst ist nur per Cookie-Login erreichbar (Least Privilege), nicht per Access-Token.

### Vorschau als anderes Konto — Sicherheitsmodell

Die „Ansicht als …" (Issue #1910, Plan #1914) lässt einen Plattform-Admin die App nur lesend als ein anderes Konto (XY) sehen. Die Regeln dahinter:

- **Principal-Tausch (E1):** In der Vorschau setzt [`VorschauFilter`](src/main/java/org/mwolff/manban/auth/web/security/VorschauFilter.java) XY als Principal und legt den handelnden Admin als `VorschauKontext` daneben. Alle Dienste und Rechteprüfungen sehen damit genau die Sicht von XY; der Plattform-Admin-Bypass entfällt von selbst. Kein Dienst bekommt einen Parameter „sehen als".
- **Cookie mit Hash (E2):** Der Zustand liegt in der Tabelle `admin_vorschau`. Im Browser steht nur das Cookie `manban_vorschau` (`HttpOnly`, `SameSite=Strict`, `Secure` wie das Sitzungs-Cookie) mit einem Geheimnis aus `SecureTokens`; gespeichert wird nur dessen Hash. „Beenden" wirkt dadurch sofort in allen Tabs.
- **Bindung an die Sitzung (E3):** Das Cookie gilt nur zusammen mit einem gültigen Sitzungs-Cookie desselben Admins; die Zeile hält die Sitzungs-Generation beim Start fest, eine höhere beendet die Vorschau. Projekt-Tokens (`PatAuthenticationFilter`) werden nie zur Vorschau.
- **Zentrale Schreibsperre (E4):** [`VorschauSchreibsperreFilter`](src/main/java/org/mwolff/manban/auth/web/security/VorschauSchreibsperreFilter.java) weist in der Vorschau jede Methode außer GET, HEAD und OPTIONS mit 403 und Problem-Typ `vorschau-nur-lesend` ab. Ausgenommen sind nur `POST /api/auth/logout` und `DELETE /api/vorschau`. Ein neuer Endpunkt kann die Sperre nicht vergessen — schreibende Wege dürfen darum nie über GET laufen.
- **Persönliche Pfade (E5):** Derselbe Filter weist auch lesende Aufrufe auf `/api/access-tokens/**` und `/api/me/vorschau-protokoll` ab (403, `vorschau-persoenlich`). Die Farbwahl von XY wird weder gezeigt noch angewendet.
- **Schnittmenge beim Lauf-Zugriff (E6):** `PermissionChecker.requireNightRunAccess` prüft in der Vorschau zusätzlich den handelnden Admin (Teilnahme oder eigene Projektrolle); scheitert er, folgt `VorschauVerborgenException` → 403 mit `vorschau-verborgen`. Die Vorschau zeigt nie mehr, als der Admin selbst sehen darf.
- **Sperre des Ausprobierens (E7):** [`ApiAusprobierFilter`](src/main/java/org/mwolff/manban/auth/web/security/ApiAusprobierFilter.java) weist eine Anfrage mit `X-Api-Ausprobieren` und Cookie `manban_vorschau` mit 403 `vorschau-nur-lesend` ab — mit Session und mit Projekt-Token, auch lesend.
- **`X-Vorschau` (E8):** Jede Anfrage aus `api/client.ts` und `api/attachments.ts` trägt die öffentliche Kennung der Vorschau, die der Tab zu sehen glaubt, oder `keine` (fehlend gilt als `keine`). Weicht sie vom Stand am Server ab, antwortet er mit 409 `vorschau-gewechselt` und führt nichts aus. So läuft ein Änderungsaufruf aus einem alten Vorschau-Tab nie mit Admin-Rechten. Ausgenommen sind nur lesende Browser-Abrufe ohne Kopfzeile (Board-Ereignisse per SSE, Bild- und Download-Links) und `GET /api/me` ohne Unterpfade: Aus der Selbstauskunft erfährt ein frisch geladener Tab die Kennung erst, und hinge sie am Abgleich, lüde er nach dem Start endlos neu (Issue #1940). Sie verändert nichts und läuft mit dem Stand am Server. Ebenso ausgenommen ist `GET /api/language` ohne Unterpfade: Die Sprache der Installation lädt der Tab noch vor der Selbstauskunft; sie ist öffentlich, hängt an keinem Konto und verändert nichts.
- **Endfälle (E9):** Der `VorschauFilter` beendet die Vorschau beim nächsten Zugriff, wenn sie abgelaufen ist (30 Minuten), der Admin nicht mehr Plattform-Admin, gesperrt oder mit höherer Sitzungs-Generation ist oder das Ziel Plattform-Admin wurde bzw. fehlt; er schreibt `beendet_am` und `endgrund`, löscht das Cookie und antwortet mit 409. Dazu kommen `DELETE /api/vorschau` und das Abmelden (`ABMELDUNG`, beide Cookies gelöscht). Kein Aufräum-Job.
- **Gesperrte Ziele (E10):** `DisabledUserGuardFilter` prüft in der Vorschau den handelnden Admin statt XY; ein gesperrtes oder wartendes Zielkonto erscheint, wie es nach Entsperren bzw. Freigabe aussähe.

### Input & XSS

- React escapt Textknoten automatisch — das schützt vor XSS, **solange** kein `dangerouslySetInnerHTML` verwendet wird.
- `dangerouslySetInnerHTML` ist verboten, außer der Eingang ist nachweislich bereinigt (DOMPurify o. ä.) **und** im Code begründet dokumentiert.
- Nutzer-Input nie ungeprüft in `href`-/`src`-Attribute schreiben (Schema-Whitelist: `https:`, `mailto:` — kein `javascript:`).

### Autorisierung in der UI

- UI-Elemente, die nur Eingeloggte sehen sollen, prüfen Auth-Status — **diese Checks sind kein Ersatz** für die serverseitige Prüfung in Spring.
- Defensiv: alles, was sensitive Daten oder Aktionen ermöglicht, hängt nicht allein an einem UI-State-Flag.
- Ausprobieren in der API-Übersicht: Die Oberfläche bietet es nur Plattform-Admins an — das ist Komfort. Die Grenze zieht der Server: `ApiAusprobierFilter` weist jeden Aufruf mit dem Kennzeichen `X-Api-Ausprobieren` ab, der nicht von einem aktiven Plattform-Admin kommt. Das Kennzeichen ist **keine Rechtegrenze für Daten** — der Aufruf läuft danach mit den gewöhnlichen Rechten der Sitzung bzw. des Projekt-Tokens.

### Error Messages

- Keine technischen Fehlertexte (Stacktraces, Endpoints, Tokens) in der UI.
- Fehler aus `ApiError` (Wrapper in `frontend/src/api/client.ts`) sachlich anzeigen — Server-`message` kann gezeigt werden, Stacktrace-artige Inhalte nicht.

---

## 🍃 Spring-Boot-Backend-Security

### Konfiguration & Secrets

- DB-Passwort und sonstige Secrets ausschließlich über Umgebungsvariablen (`DB_PASSWORD`, …) oder externe Konfiguration (`SPRING_CONFIG_ADDITIONAL_LOCATION`).
- `application.yml` und `application-docker.yml` enthalten ausschließlich Defaults im Format `${VAR:default}` — keine Klartext-Geheimnisse.
- `.env` und `.env.local` sind gitignored. `.env.example` ist die Vorlage und enthält **keine** echten Werte.
- Spring Boot Actuator-Endpunkte werden auf das Minimum reduziert. Erlaubt aktuell: `health`, `info`. Niemals `env`, `configprops`, `beans` oder `mappings` ohne Auth in Produktion exponieren.
- `management.endpoint.health.show-details: when-authorized` (oder `never`) — nie `always` in Produktion.

### Input-Validation

- Eingehende DTOs sind Records mit Jakarta-Validation-Constraints (`@NotBlank`, `@Size`, `@Email`, …). Controller-Parameter mit `@Valid` annotieren.
- `MethodArgumentNotValidException` wird vom `GlobalExceptionHandler` zu HTTP 400 mit `fieldErrors`-Map gemappt.
- Pfad- und Query-Parameter explizit typisieren und einschränken (`@PathVariable Long id`, `@Min(1)`).
- Niemals `String`-Parsing per `parseLong`/`parseInt` ohne `try` oder Range-Check, wenn der Wert direkt in Geschäftslogik geht.

### Datenbankzugriff (CRITICAL)

- **JPA/Hibernate mit Parameter-Bindung.** Spring-Data-Repositories nutzen Named-Parameters oder Methoden-Naming.
- **JPQL/Native-SQL** ausschließlich mit `@Param`-gebundenen Parametern. **Niemals** Benutzereingaben in Query-Strings konkatenieren.
- Beispiel zulässig:
  ```java
  @Query("SELECT w FROM WidgetEntity w WHERE w.dashboardId = :dashboardId")
  List<WidgetEntity> findByDashboardId(@Param("dashboardId") Long dashboardId);
  ```
- Beispiel verboten:
  ```java
  entityManager.createQuery("SELECT w FROM WidgetEntity w WHERE w.dashboardId = " + id);  // ❌
  ```
- DDL-Änderungen ausschließlich über Flyway. `hibernate.ddl-auto=validate` in Produktion — niemals `update` oder `create-drop`.

### Datenbank-Berechtigungen

- DB-User der Anwendung erhält nur minimale notwendige Rechte (CRUD auf eigene Tabellen, kein `DROP`, kein `GRANT`, kein `CREATE USER`).
- Niemals `root` oder administrative Accounts für die Webanwendung.
- Getrennte Accounts und Passwörter pro Umgebung (dev/test/staging/prod).
- Migrationen können einen erweiterten Account brauchen — der ist getrennt vom Laufzeit-Account.

### Authentifizierung & Tokens (falls eingeführt)

- **Passwort-Hashing** über `BCryptPasswordEncoder` (Spring Security), Standard-Strength oder höher.
- **Token-Generierung** kryptographisch sicher (`SecureRandom`), in der DB ausschließlich der Hash gespeichert (`SHA-256` oder besser).
- **TTL** konfigurierbar über `@ConfigurationProperties`, Default eng (z. B. 8 h).
- Abgelaufene Tokens regelmäßig löschen (`@Scheduled` mit klar definiertem Cron).
- Brute-Force-Schutz: Failed-Login-Delay (zufällig 200–800 ms via `Thread.sleep` in einem dedizierten Use-Case) und Rate Limiting an der API-Grenze.
- Auth-Verhalten ist mit Spring-Security-Integration-Tests abgedeckt — nicht nur Happy-Path.

### CORS

- Im aktuellen Deployment ist Frontend und Backend auf derselben Origin — **CORS wird nicht benötigt**.
- Wenn das Frontend künftig auf eine andere Origin zieht: explizite `CorsConfiguration` mit konkreter Allow-Origin-Liste. Niemals `*` für Auth-belegte Endpunkte.

### HTTP-Header

- Spring Security-Defaults aktiviert lassen: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Cache-Control: no-store` für Auth-Antworten.
- HSTS-Header in Produktion via Reverse-Proxy oder Spring-Security-Konfiguration.

### Error-Handling & Logging

- Globaler Handler via `@RestControllerAdvice` ([GlobalExceptionHandler.java](src/main/java/org/mwolff/api/common/GlobalExceptionHandler.java)). Nach außen: HTTP-Statuscode + generische Message + (bei Validation) `fieldErrors`. **Niemals** Stacktrace, SQL, interne Klassen.
- Logging über SLF4J/Logback. **Niemals** Secrets, Tokens, Passwörter, Klartext-PII, vollständige SQL-Queries mit Werten loggen.
- Logge nicht den kompletten Request-Body von Auth-Endpunkten.
- `printStackTrace()` ist verboten.

---

## 🐳 Container & Deployment

- `Dockerfile`: Multi-Stage-Build, finale Stage als JRE (kein JDK in Produktion). Kein `sudo`, keine zusätzlichen Tools außer wirklich nötigem (`curl` nur für Healthcheck).
- Container läuft idealerweise als Non-Root-User. Bei `eclipse-temurin:21-jre`: explizit Non-Root setzen, wenn das Image dies nicht schon tut.
- `docker-compose.yml`: keine Secrets als Klartext — über `.env` oder Compose-Secrets binden.
- Healthcheck nutzt `/actuator/health` mit minimalen Informationen.
- Jeder Bezug auf ein fremdes Abbild in `docker-compose*.yml`, `Dockerfile` und `backup/Dockerfile` ist an Tag **und** Digest gebunden (`name:tag@sha256:…`, Digest der Manifest-Liste, nicht einer Architektur). So fährt ein unverändertes Tag nicht still einen anderen Inhalt. Den Digest hebt der Aktualisierungs-PR an (Renovate, `pinDigests`), und zwar an der Fundstelle und in `scripts/bausteine.json` gemeinsam; `scripts/bezugspruefung.test.mjs` hält beide Stellen gleich. Ausgenommen sind die eigenen Abbilder `ghcr.io/mannewolff/kanban-kit` und `ghcr.io/mannewolff/kanban-kit-backup`: Ihr Digest entsteht erst im Release-Lauf auf dem Tag, der Commit dazu kann ihn also nicht tragen (Plan #1295, E23). Ebenfalls ausgenommen ist das Rückweg-Overlay `docker-compose.altspeicher.yml` (E12).

---

## 🔍 Security-Checklist vor Commit

- [ ] Keine Secrets, Tokens, Passwörter, Hostnamen oder Cloud-Credentials im Diff.
- [ ] `.env`, `.env.local`, `application-local.yml` weiterhin gitignored — falls erst neu angelegt.
- [ ] Alle Datenbank-Queries mit Parameter-Bindung (`@Param`, JPA-Methoden-Naming oder Prepared Statements).
- [ ] Alle Controller-Parameter `@Valid`-annotiert, falls DTO; `@PathVariable`/`@RequestParam` typisiert.
- [ ] Keine `System.out.println`, `e.printStackTrace()`, `var_dump`-Äquivalente.
- [ ] Keine leeren `catch (Exception e) { }`-Blöcke.
- [ ] Frontend-Code referenziert keine internen Endpunkte/Pfade in Fehlermeldungen.
- [ ] `mvn verify` und `npm run build` grün.
- [ ] `dangerouslySetInnerHTML` nirgends ohne dokumentierten Grund.
- [ ] Actuator: keine sensiblen Endpunkte zusätzlich exponiert.

---

## 🚨 Rote Flaggen

- DB-Passwort, Token-Wert oder API-Key in `application.yml`, `Dockerfile`, Code oder Logs.
- Direkter Zugriff auf `request.getParameter(...)` o. ä. ohne Validation.
- JPQL/SQL-String mit Konkatenation von Nutzereingaben.
- `hibernate.ddl-auto=update` oder `create-drop` außerhalb von Tests.
- `management.endpoints.web.exposure.include: "*"` oder ungeschützter `/actuator/env`.
- Passwort-Hashing ohne BCrypt/Argon2 (z. B. selbstgebastelte MD5/SHA-Lösungen).
- Token-Vergleich mit `String.equals` auf Plain-Token statt Hash-Vergleich.
- React-Code mit `dangerouslySetInnerHTML={{ __html: userInput }}`.
- CORS `*` auf Auth-belegten Endpunkten.

---

## 🔗 Weiterführende Docs

- [CLAUDE.md](CLAUDE.md) — Projekt-Übersicht
- [CLAUDE-java.md](CLAUDE-java.md) — JPA, Spring-Designregeln
- [CLAUDE-react.md](CLAUDE-react.md) — XSS, Storage, Frontend-Patterns
- [CLAUDE-workflow.md](CLAUDE-workflow.md) — Pflichtchecks vor Push
- [GlobalExceptionHandler.java](src/main/java/org/mwolff/api/common/GlobalExceptionHandler.java) — zentrale Fehler-Mappings
- [application.yml](src/main/resources/application.yml) — Konfiguration mit Env-Vars
