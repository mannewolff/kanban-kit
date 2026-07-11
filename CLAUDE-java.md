# CLAUDE-java.md — Java- & Spring-Boot-Standards

> **Diese Datei ist nicht verhandelbar.** Sie definiert die einzig zulässige Arbeitsweise für Java-/Spring-Boot-Code in diesem Projekt. Jede Abweichung gilt als Fehler und muss korrigiert werden, bevor die Aufgabe als erledigt gilt. Wenn eine Regel mit einer Nutzeranweisung kollidiert, weise den Konflikt aus und frag nach — Regeln dieser Datei werden nicht stillschweigend aufgeweicht.

Ergänzend zu [CLAUDE.md](CLAUDE.md), [CLAUDE-security.md](CLAUDE-security.md) und [CLAUDE-workflow.md](.claude/CLAUDE-workflow.md).

---

## 1. Projektkontext

- **Sprache:** Java 25 (LTS), keine Preview-Features ohne explizite Freigabe. Die Version wird durch `maven-enforcer-plugin` erzwungen (`requireJavaVersion=[25,26)`), der Build bricht auf jeder anderen JDK-Hauptversion ab. Wenn lokal eine andere JDK aktiv ist, entweder `JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home` setzen oder `maven-toolchains-plugin` über `~/.m2/toolchains.xml` konfigurieren (Beispiel im Repo: `infra/toolchains.xml.example`).
- **Framework:** Spring Boot 3.5.
- **Build:** Maven 3.9+. Compiler läuft mit `-Xlint:all -Werror`; jede Warnung bricht den Build. Enforcer prüft zusätzlich `dependencyConvergence` und `banDuplicatePomDependencyVersions`.
- **Datenbank:** PostgreSQL 16 (Testcontainers in Integrationstests). Objektspeicher für Anhänge: MinIO (S3-kompatibel).
- **Schema-Migration:** Flyway (`src/main/resources/db/migration/`, `V<n>__<beschreibung>.sql`).
- **Paradigma:** Strenges Test-Driven Development (TDD) nach Kent Beck — **Red → Green → Refactor**.
- **Qualitätsziel:** 100 % Zeilenabdeckung, 100 % Branch-Abdeckung, 100 % Mutationsabdeckung. Keine Ausnahmen ohne dokumentierte Begründung im Code.
- **Lokale Build-Umgebung (macOS/Colima):** Die Integrationstests brauchen Docker über Colima. Zwingend: `DOCKER_HOST=unix://$HOME/.colima/default/docker.sock` und `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock` (sonst scheitert der Ryuk-Container am Socket-Mount) sowie `~/.docker-java.properties` mit `api.version=1.44` (docker-java handelt sonst API 1.32 aus, moderne Daemons verlangen ≥ 1.40).

### 1.1 Versionsstrategie

- **Ist:** Java 25 (LTS), Spring Boot 3.5.x, JUnit 5. LTS-zu-LTS-Upgrades sind der Standardpfad; Non-LTS-Versionen (z. B. Java 26) werden übersprungen.
- **Offen (jeweils eigener Plan, kein Nebenbei-Upgrade):** Spring Boot 4.x + JUnit 6 (Major-Umstieg: neue Baseline, JSpecify-annotierte Frameworks), Virtual Threads (`spring.threads.virtual.enabled`).
- Abhängigkeits- und Supply-Chain-Pflege (Updates, SBOM, Vulnerability-Scan) → [CLAUDE-security.md](CLAUDE-security.md).

---

## 2. Eiserne TDD-Regeln

### 2.1 Reihenfolge (nicht verhandelbar)

1. **Roter Test zuerst.** Vor jeder Zeile Produktivcode existiert ein fehlschlagender Test, der genau das Verhalten beschreibt, das implementiert werden soll.
2. **Minimale Implementierung.** Schreibe **nur** so viel Produktivcode, dass dieser eine Test grün wird. Nichts darüber hinaus.
3. **Refactoring** mit grüner Testsuite. Strukturverbesserungen erfolgen ausschließlich, wenn alle Tests grün sind.
4. **Ein Test, ein Commit-Kandidat.** Niemals Produktivcode ohne zugehörigen, vorab geschriebenen Test einchecken.

### 2.2 Verbote

- ❌ **Kein Produktivcode ohne fehlschlagenden Test.** Wer Produktivcode vor dem Test schreibt, beginnt die Aufgabe neu.
- ❌ **Keine spekulative Generalisierung.** Kein Code „für später". YAGNI ist Pflicht.
- ❌ **Keine auskommentierten Tests.** Entweder grün oder gelöscht — niemals deaktiviert mit `@Disabled` ohne Ticket-ID und Ablaufdatum als Kommentar.
- ❌ **Keine Tests, die bestehen, ohne etwas zu prüfen.** Jeder Test muss mindestens eine aussagekräftige Assertion enthalten. `assertTrue(true)` und Äquivalente sind verboten.

### 2.3 Beweispflicht

Vor dem Markieren einer Aufgabe als „fertig" ist nachzuweisen:

- Der Test wurde **zuerst** geschrieben und war **rot** (in Commit-Historie oder im Konversationsverlauf sichtbar).
- Die minimale Implementierung wurde gezeigt.
- Die volle Testsuite läuft grün.
- Coverage- und Mutationsberichte erfüllen die Schwellwerte (s. §5).

---

## 3. Testpyramide und Teststruktur

### 3.1 Verteilung

- **Unit-Tests (≥ 80 % der Tests):** JUnit 5 + AssertJ + Mockito. Schnell, isoliert, deterministisch.
- **Slice-Tests:** `@WebMvcTest`, `@DataJpaTest`, `@JsonTest` etc. — eingesetzt zielgerichtet, nicht inflationär.
- **Integrationstests:** `@SpringBootTest` mit Testcontainers für echte Infrastruktur (PostgreSQL, ggf. MinIO). **Niemals** H2 als Postgres-Ersatz.
- **Architekturtests:** ArchUnit prüft Schichtentrennung, Paketregeln, Naming (siehe `ArchitectureTest`). Verstoß = Build-Fehler.
  **Achtung, gelernte Falle:** Die `archunit-junit5`-Engine (`@AnalyzeClasses`/`@ArchTest`) wird von Surefire in diesem Projekt **nicht ausgeführt** — die Regeln liefen als „0 Tests" durch, ein bewusst eingebauter Verstoß blieb unentdeckt (falsches Grün). ArchUnit-Regeln deshalb immer als reguläre JUnit-`@Test`-Methoden gegen einen `ClassFileImporter` (mit `DoNotIncludeTests`) schreiben.

### 3.2 Pflichtbibliotheken

| Zweck                | Bibliothek                                  |
|----------------------|---------------------------------------------|
| Test-Framework       | JUnit 5 (Jupiter)                           |
| Assertions           | AssertJ (kein Hamcrest, kein JUnit-Assert)  |
| Mocking              | Mockito (nur an Architekturgrenzen)         |
| Integrationsumgebung | Testcontainers + spring-boot-testcontainers |
| Architektur          | ArchUnit                                    |
| Coverage             | JaCoCo                                      |
| Mutationstests       | PIT (pitest)                                |
| Static Analysis      | SpotBugs, PMD, Checkstyle, Error Prone      |
| Formatierung         | Spotless (Google Java Format)               |

### 3.3 Benennung und Struktur

- Testklasse: `ClassNameTest` (Unit / Slice), `ClassNameIT` (Integration). Failsafe führt `*IT` aus, Surefire `*Test`.
- Testmethode: `methodUnderTest_givenCondition_thenExpectedOutcome` oder als Satz: `shouldReturnEmptyListWhenRepositoryIsEmpty`.
- Aufbau jeder Testmethode strikt **Given / When / Then** (als Kommentar oder durch Leerzeilen sichtbar gegliedert).
- **Eine** logische Assertion pro Test — mehrere `assertThat` nur, wenn sie denselben Aspekt aus mehreren Blickwinkeln prüfen.
- Keine Logik in Tests: keine `if`, keine `for` über Assertions, keine Try/Catch um Asserts. Parametrisierte Fälle: `@ParameterizedTest`.

### 3.4 Was getestet wird

- **Verhalten, nicht Implementierung.** Tests dürfen nicht brechen, wenn interne Struktur sich ändert, das Verhalten aber gleich bleibt.
- **Öffentliche API** der Einheit. Private Methoden werden **nicht** direkt getestet, sondern über ihre öffentlichen Aufrufer.
- **Randfälle sind Pflicht:** null, leer, Grenzwerte (0, 1, MAX), negativ, Unicode, gleichzeitige Zugriffe wo relevant.
- **Fehlerpfade gleichrangig zu Erfolgspfaden.** Jede `throw`-Anweisung im Produktivcode hat einen Test.

---

## 4. Mocking-Disziplin

- Mockito wird **nur** für Abhängigkeiten an Architekturgrenzen verwendet (Repositories, externe Clients, `Clock`, `Random`, Filesystem).
- ❌ **Keine Mocks für Werttypen, DTOs, Domänenobjekte, eigene reine Services ohne I/O.**
- ❌ **Keine** `mockStatic`, `mockConstruction`, `PowerMock` o. ä. — wer sie braucht, hat ein Designproblem.
- ❌ **Keine** „Lenient"-Mocks (`Mockito.lenient()`) ohne dokumentierte Begründung.
- ✅ Bevorzuge **Fakes** und **In-Memory-Implementierungen** für Repositories in Unit-Tests, wenn dadurch Tests klarer werden.
- Mocks verifizieren **Interaktionen nur, wenn die Interaktion das geprüfte Verhalten ist**. Reines `verify` für Getter ist verboten.

---

## 5. Coverage und Mutationstests

### 5.1 Schwellwerte (Build bricht ab bei Unterschreitung)

| Metrik                            | Schwellwert |
|-----------------------------------|-------------|
| JaCoCo Line Coverage              | **100 %**   |
| JaCoCo Branch Coverage            | **100 %**   |
| PIT Mutation Score                | **100 %**   |
| PIT Test Strength                 | **100 %**   |

JaCoCo (`check`) läuft im Default-`mvn verify`. **PIT läuft bewusst im Maven-Profil `-Ppit`**
(`mvn -Ppit test`), damit der Standard-Build schnell bleibt — das Profil gehört zu den
Pflichtchecks vor Abschluss (local-check und CI). Eine separate Test-Strength-Schwelle bietet
pitest-maven nicht; sie wird über `mutationThreshold=100` miterzwungen und im Report geprüft.

### 5.2 Ausschlussregeln (eng gefasst)

Ausgeschlossen werden **ausschließlich**:

- Generierter Code (`@Generated`, MapStruct-Output, Lombok-Generate).
- Spring-Boot-Application-Klasse mit reiner `main`-Methode.
- Konfigurations-Properties-/Bean-Wiring-Klassen ohne Fachlogik (z. B. `OpenApiConfig`).
- DTOs/Records/Projektionen **nur**, wenn sie keinerlei Logik enthalten (keine compact constructors, keine berechneten Felder).
- **Infrastruktur-Adapter, Entities und Spring-Data-Repositories, die nur gegen echte
  Infrastruktur sinnvoll testbar sind** und dort per Testcontainers-`*IT` zu 100 % abgedeckt
  werden (real: die `*Entity`-Klassen, `*JpaRepository`, `*RepositoryAdapter`/`Jdbc*` sowie die
  Mail-/S3-Adapter `JavaMail*`/`MinioObjectStorage`). Die JaCoCo-Prüfung wertet die Unit-Coverage
  (surefire) aus; IT-Coverage fließt nicht in `jacoco.exec` ein, daher der Ausschluss trotz voller
  IT-Abdeckung. **Nicht** ausgeschlossen werden Klassen, die mit gemockten Ports unit-testbar
  sind — Controller, Security-Filter und `Sha256TokenCryptoAdapter` sind deshalb unit-getestet
  und **in** der Coverage.
- **Krypto-Plumbing mit nachweislich nicht erreichbaren Zweigen** (Checked-Exceptions garantiert
  verfügbarer JCA-Provider): real `SecureTokens` (klassenweise) und der `hmac`-Catch in
  `SignedSessionTokens` (methodengenau via `@ExcludeFromJacocoGeneratedReport`, s. §5.4).

Jeder Ausschluss steht **explizit** (klassenweise, keine Paket-Wildcards) in der `pom.xml` und
ist dort begründet. Pauschale Paket-Ausschlüsse sind verboten. Für jede ausgeschlossene Klasse
mit Fachlogik existiert ein separater `*Test` oder `*IT`, der diese Logik abdeckt.

### 5.3 PIT-Konfiguration (Mindestmutatoren)

- `STRONGER`-Gruppe aktiviert.
- Mindestens: `CONDITIONALS_BOUNDARY`, `INCREMENTS`, `INVERT_NEGS`, `MATH`, `NEGATE_CONDITIONALS`, `VOID_METHOD_CALLS`, `EMPTY_RETURNS`, `FALSE_RETURNS`, `TRUE_RETURNS`, `NULL_RETURNS`, `PRIMITIVE_RETURNS`, `REMOVE_CONDITIONALS`. (Das frühere `RETURN_VALS` existiert in aktuellem PIT nicht mehr — es ist in die `*_RETURNS`-Mutatoren aufgeteilt, die alle aktiv sind.)
- `timeoutFactor=1.5`, `timeoutConst=4000`.
- Überlebende Mutanten sind **immer** durch zusätzliche Tests zu töten — nie durch Mutator-Deaktivierung umgangen.

### 5.4 Umgang mit nicht erreichbarer Coverage

Wenn 100 % unmöglich erscheinen, lautet die Antwort **nicht** „Schwellwert senken", sondern:

1. Refactoring, bis der ungetestete Pfad testbar wird, oder
2. Code entfernen (er war nicht nötig), oder
3. Begründeter `@ExcludeFromJacocoGeneratedReport` mit Code-Review-Kommentar — die Annotation
   existiert unter `common/ExcludeFromJacocoGeneratedReport.java` (Retention CLASS; PIT honoriert
   sie über das `FANN`-Feature in der pit-Profil-Konfiguration) und ist methodengenau
   einzusetzen, nie pauschal.

---

## 6. Produktivcode — Designregeln

### 6.1 Architektur

- **Schichtentrennung** (durch ArchUnit erzwungen): `domain` → keine Spring-, Jakarta-, JPA-Importe. `application` → orchestriert. `infrastructure` → Spring/JPA/HTTP. `web` → Controller, DTOs.
- Domänenmodell ist **framework-frei**. Entities mit JPA-Annotationen liegen in `infrastructure.persistence` und werden auf Domänenobjekte gemappt.
- Hexagonale Architektur / Ports & Adapters. Repositories sind Interfaces im `domain`, Implementierungen in `infrastructure`.

### 6.2 Sprache und Stil

- **Records bevorzugen** für unveränderliche Daten.
- **`final` als Default** für Felder, Parameter, lokale Variablen — Veränderlichkeit muss begründet sein.
- **Null vermeiden:** `Optional` für Rückgabewerte, niemals als Parameter oder Feld. Sammlungen niemals null, immer leer.
  - **Erzwungen durch JSpecify + NullAway (Issue #0080):** Jedes Fachpackage trägt `@NullMarked` (`package-info.java`) — Referenzen sind damit per Default non-null. Wo `null` fachlich legitim ist (z. B. `Card.movedToDoneAt`, `parentId`, IDs vor der Persistierung), steht explizit `org.jspecify.annotations.Nullable` an der Komponente/dem Parameter. NullAway läuft als Error-Prone-Plugin im JSpecify-Modus mit `-Xep:NullAway:ERROR`: jedes Finding bricht den Build. Findings werden behoben (echte Lücke schließen oder `@Nullable` dort, wo es fachlich stimmt), nie unterdrückt. Die ID persistierter Instanzen liefert `Identifiable.requireId()` statt verstreuter `requireNonNull`-Aufrufe. Das Gate gilt für Produktivcode; Testcode kompiliert ohne NullAway (Tests konstruieren absichtlich mit `null`), Error Prone bleibt dort aktiv.
- **Keine Exceptions zur Ablaufsteuerung.** Checked Exceptions nur, wenn der Aufrufer reagieren kann; sonst eigene unchecked Domänenexceptions.
- **Keine statischen Hilfsmethoden mit Zustand.** Keine `Calendar`/`Date` — nur `java.time`.
- **`Clock` injizieren** statt `LocalDateTime.now()` direkt aufzurufen. Sonst sind Zeitlogiken untestbar.
- **Kein Lombok.** Das Projekt hat keine Lombok-Dependency — Records, `final`-Felder und explizite Konstruktoren decken den Bedarf ab. Wer Boilerplate schreibt, prüft zuerst, ob ein Record die richtige Form ist.

### 6.3 Spring-spezifisch

- **Konstruktorinjektion ausnahmslos.** Keine `@Autowired` auf Feldern. Keine Setter-Injection.
- **`@Component`/`@Service` sparsam.** Konfigurationsklassen mit `@Bean`-Methoden sind oft sauberer.
- **Keine Geschäftslogik in Controllern.** Controller validieren, delegieren, mappen Statuscodes — mehr nicht.
- **Transaktionsgrenzen** in der Application-Schicht (`@Transactional` auf Use-Case-Klassen), nicht auf Repositories oder Controllern.
- **Konfiguration über `@ConfigurationProperties`-Records** mit Bean Validation (`@Validated`).
- **Fehlerbehandlung nach außen:** zentral im globalen `@RestControllerAdvice` [`GlobalExceptionHandler`](src/main/java/org/mwolff/manban/common/web/GlobalExceptionHandler.java) — die einzige Stelle für Fehler-Mapping. Er mappt die `@ResponseStatus`-annotierten Domänenexceptions (Statuscode generisch aus der Annotation) und Bean-Validation-Fehler (400 + `fieldErrors`-Extension) auf RFC-9457 Problem Details (`ProblemDetail`, `application/problem+json`); unerwartete Fehler ergeben 500 mit generischem `detail` — keine Stacktraces/internen Details nach außen.

### 6.4 Datenbank

- Schemamigrationen ausschließlich über **Flyway**. Niemals `hibernate.ddl-auto=update/create` außerhalb von Tests. In Produktion: `validate`.
- Integrationstests verwenden **dieselbe DB-Engine** wie Produktion (PostgreSQL via Testcontainers). Alle `*IT` erben von `AbstractIntegrationTest`: **eine** geteilte Postgres-/MinIO-Singleton-Instanz für die ganze Suite (`@ServiceConnection`, Start im statischen Initialisierer — bewusst ohne `@Container`, die JUnit-Extension würde pro Klasse stoppen). Datenisolation: vor jeder Testmethode werden alle Fachtabellen geleert (Seed-Tabellen `permission`/`role_permission` bleiben).
- Repository-Tests prüfen tatsächliche SQL-Ausführung, nicht nur Spring-Data-Methodennamen.
- **Prepared Statements / Parameter-Bindung** ist Pflicht. Niemals Benutzereingaben in JPQL/SQL konkatenieren. Siehe [CLAUDE-security.md](CLAUDE-security.md).

### 6.5 Verboten

- ❌ `System.out.println`, `printStackTrace`, `e.getMessage()` als alleiniges Logging.
- ❌ Schweigend gefangene Exceptions (`catch (Exception e) { }`).
- ❌ TODO-/FIXME-Kommentare ohne Ticket-Referenz.
- ❌ Reflection außerhalb von Framework-Code.
- ❌ `instanceof`-Ketten statt Polymorphie (außer `switch` über sealed types).
- ❌ Zyklische Paketabhängigkeiten — ArchUnit-Test ist Pflicht.

---

## 7. Statische Analyse und Formatierung

Der Build schlägt fehl bei:

- **Checkstyle**-Verstößen (Google Style, angepasst — Regelset: `config/checkstyle/checkstyle.xml`).
- **SpotBugs**-Findings ab Priorität `Medium` (Ausnahmen: `config/spotbugs/exclude.xml` — nur die zwei `EI_EXPOSE_REP`-Stilmuster für DI-Singletons/immutable Records; alle Bug-Detektoren bleiben scharf).
- **PMD**-Findings (kuratiertes Regelset: `config/pmd/ruleset.xml`, Deaktivierungen einzeln begründet).
- **Error Prone**-Warnungen.
- **Spotless**-Formatabweichungen (`mvn spotless:check`, Google Java Format).
- **Compiler-Warnungen** (`-Xlint:all -Werror`; einzige dokumentierte Ausnahme: `-serial`, da die nicht serialisierten Domänenexceptions kein `serialVersionUID` tragen — konsistent zur PMD-Entscheidung).

`@SuppressWarnings` ist erklärungspflichtig: Kommentar mit Begründung **direkt darüber**, sonst Build-Bruch via Checkstyle-Regel.

---

## 8. Workflow für jede Java-Aufgabe

Wenn eine Implementierungsaufgabe gestellt wird, gehe **exakt** in dieser Reihenfolge vor (eingebettet in den projektweiten 9-Schritte-Workflow aus [CLAUDE-workflow.md](.claude/CLAUDE-workflow.md)):

1. **Verstehen und präzisieren.** Bei Unklarheit Rückfrage stellen, nicht raten.
2. **Akzeptanzkriterien als Testliste formulieren.** Eine Liste konkreter Tests, die nach der Aufgabe existieren werden.
3. **Ersten Test schreiben** — und ausführen, sodass er aus dem **richtigen Grund** fehlschlägt (Compile-Fehler zählt nicht als „rot", außer es ist der Anfang einer neuen Klasse).
4. **Minimale Implementierung** für genau diesen Test.
5. **Test grün** verifizieren.
6. **Refactoring**, wenn nötig — Testsuite bleibt grün.
7. **Wiederholen** für den nächsten Test der Liste.
8. **Abschluss-Check:**
   - `mvn verify` läuft komplett grün.
   - JaCoCo-Report: 100 %.
   - PIT-Report: 100 % Mutation Score (`mvn -Ppit test`).
   - ArchUnit: grün.
   - Spotless, Checkstyle, SpotBugs, PMD: keine Findings.
   - **CI erzwingt dieselben Gates** (`.github/workflows/ci.yml`): verify-Job + eigener PIT-Job (`-Ppit`) + Frontend-Job. Was lokal grün sein muss, ist auch in CI ein Pflicht-Gate.
9. **Zusammenfassung** an den Nutzer im Format aus [CLAUDE-workflow.md](.claude/CLAUDE-workflow.md): was getestet wurde, welche Coverage erreicht wurde, was bewusst nicht abgedeckt ist (mit Begründung).

### 8.1 Wenn du versucht bist, abzukürzen

Folgende Sätze sind in diesem Projekt **inakzeptabel** und ein Signal, dass die Aufgabe nicht abgeschlossen ist:

- „Die Tests folgen später."
- „Für diesen Trivialfall ist ein Test übertrieben."
- „Setter/Getter müssen nicht getestet werden." (Doch — oder sie existieren nicht.)
- „Die Coverage liegt bei 98 %, das reicht." (Reicht nicht.)
- „Diesen Mutanten kann man nicht töten." (Doch — oder der Code wird umstrukturiert.)
- „Ich habe `@Disabled` gesetzt, um schnell weiterzukommen."

Wenn diese Gedanken auftauchen: **stoppen, zurück zur Testliste, Aufgabe ordentlich zu Ende bringen.**

---

## 9. Commits

- Konventionell: `feat:`, `fix:`, `refactor:`, `test:`, `chore:`, `docs:`.
- **Ein Commit pro Red-Green-Refactor-Zyklus** ist das Ideal. Mindestens: Tests und zugehörige Implementierung im selben Commit.
- Reine Refactor-Commits dürfen **keine** Verhaltensänderung enthalten und müssen mit grüner Suite vor **und** nach dem Commit verifiziert sein.
- Commit-Message beschreibt **das Warum**, nicht das Was.
- Push erfolgt ausschließlich auf explizite Anweisung des Users (siehe [CLAUDE-workflow.md](.claude/CLAUDE-workflow.md)).

---

## 10. Schlussbestimmung

Diese Datei beschreibt den **Mindeststandard**. Wenn eine Möglichkeit gesehen wird, die Qualität weiter zu erhöhen, ohne Geschwindigkeit unverhältnismäßig zu opfern, ist sie zu nutzen und im PR vorzuschlagen.

**Qualität ist hier nicht ein Wert unter vielen. Sie ist die Grundbedingung dafür, dass Code überhaupt eingecheckt wird.**

---

## 🔗 Weiterführende Docs

- [CLAUDE.md](CLAUDE.md) — Projekt-Übersicht
- [CLAUDE-react.md](CLAUDE-react.md) — Frontend-Regeln
- [CLAUDE-security.md](CLAUDE-security.md) — Sicherheit auf JPA-/Spring-Ebene
- [CLAUDE-workflow.md](.claude/CLAUDE-workflow.md) — 9-Schritte-Workflow, Issue-Format, Git
