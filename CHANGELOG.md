# Changelog

Alle nennenswerten Änderungen an kanban-kit werden hier festgehalten.

Das Format orientiert sich an [Keep a Changelog](https://keepachangelog.com/de/1.1.0/); die
Versionierung folgt der dreiteiligen Betriebsversion (siehe [RELEASING.md](RELEASING.md)). Die
Einträge je Version sind ein automatischer Auszug der Commit-Titel seit dem letzten Release,
erzeugt von `scripts/gen-changelog.mjs`.

## [1.33.0] – 2026-09-03

- chore: v1.32.1
- Mutationtest für react
- Herkunftsbaum: Label-Chips vor dem Titel ([#740](https://github.com/mannewolff/kanban-kit/issues/740))
- Herkunftsbaum: Zeile per Maus anklickbar ([#739](https://github.com/mannewolff/kanban-kit/issues/739))
- chore: v1.32.0
- chore: v1.31.1
- Stryker installiert und konfiguriert
- Nachtlauf-Auswertung deutet null aus der API ([#734](https://github.com/mannewolff/kanban-kit/issues/734))

## [1.32.0] – 2026-09-03

- chore: v1.31.1
- Stryker installiert und konfiguriert
- Nachtlauf-Auswertung deutet null aus der API ([#734](https://github.com/mannewolff/kanban-kit/issues/734))

## [1.31.0] – 2026-09-02

- chore: v1.30.2
- Parser deutet echte Protokolle vollstaendig ([#720](https://github.com/mannewolff/kanban-kit/issues/720))
- Rollen- und Nutzungsdokumentation der Nachtlauf-Auswertung ([#728](https://github.com/mannewolff/kanban-kit/issues/728))
- Uebernahmetext der Nachtlauf-Auswertung ([#727](https://github.com/mannewolff/kanban-kit/issues/727))
- Haeufigkeit einer Fehlerklasse in der Nachtlauf-Auswertung ([#726](https://github.com/mannewolff/kanban-kit/issues/726))
- Navigationseintrag und Route der Nachtlauf-Auswertung ([#724](https://github.com/mannewolff/kanban-kit/issues/724))
- Grunddarstellung der Nachtlauf-Auswertung ([#725](https://github.com/mannewolff/kanban-kit/issues/725))
- Endpunkte und Frontend-Anbindung der Nachtlauf-Auswertung ([#723](https://github.com/mannewolff/kanban-kit/issues/723))
- Nachtlauf-Service mit Owner-Recht, Ringpuffer und Duplikaten ([#722](https://github.com/mannewolff/kanban-kit/issues/722))
- Persistenzschicht fuer die Nachtlauf-Auswertung ([#721](https://github.com/mannewolff/kanban-kit/issues/721))
- chore: v1.30.1
- Nachtlauf-Protokoll als reines Modul parsen ([#720](https://github.com/mannewolff/kanban-kit/issues/720))
- Getoenter Grund traegt jetzt die ganze Flaeche (Bahn 1)
- Vorhaben-Kacheln haben alle dieselbe Hoehe (Bahn 1)
- Getoenter Grund fuer die gesamte Anwendung ([#713](https://github.com/mannewolff/kanban-kit/issues/713))
- Designsprache des Leitstands als CLAUDE-design.md verankern ([#712](https://github.com/mannewolff/kanban-kit/issues/712))

## [1.30.0] – 2026-09-01

- Vorhaben-Ansicht: Ausblenden über ⋮-Menü und Umschalter ([#705](https://github.com/mannewolff/kanban-kit/issues/705))
- Vorhaben-Ansicht: Sichtbarkeitsfilter visibleEpics ([#704](https://github.com/mannewolff/kanban-kit/issues/704))
- Listen-Sortierung: Kopfzeile, Zustand und Hinweistexte ([#700](https://github.com/mannewolff/kanban-kit/issues/700))
- Listen-Sortierung: reine Sortierlogik in lib/listSort.ts ([#699](https://github.com/mannewolff/kanban-kit/issues/699))
- Compat-API: eigene Board-Spalten tragen keinen Kanban-Zustand mehr ([#697](https://github.com/mannewolff/kanban-kit/issues/697))
- chore: v1.29.1
- Anzeigename auf "KI-Leitstand" ändern

## [1.29.0] – 2026-09-01

- chore: v1.28.1
- Listenansicht: Kürzel über die Herkunft zuordnen und zum Vorhaben springen ([#689](https://github.com/mannewolff/kanban-kit/issues/689))
- Board: Kürzel über die Herkunft zuordnen und zum Vorhaben springen ([#688](https://github.com/mannewolff/kanban-kit/issues/688))
- epicToCard nach lib extrahieren ([#691](https://github.com/mannewolff/kanban-kit/issues/691))
- Vorhaben-Seite reicht die fehlenden Props an den Detail-Dialog ([#687](https://github.com/mannewolff/kanban-kit/issues/687))
- Vorhaben-Dialog zeigt den Fortschritt ([#686](https://github.com/mannewolff/kanban-kit/issues/686))
- EpicBadge wird ein Bedienelement ([#685](https://github.com/mannewolff/kanban-kit/issues/685))
- Zuordnungsfunktion Karte zu Vorhaben als reines Modul ([#684](https://github.com/mannewolff/kanban-kit/issues/684))

## [1.28.0] – 2026-08-31

- chore: v1.27.1
- Schalter "Auf dem Board ausblenden" an der Vorhaben-Kachel ([#669](https://github.com/mannewolff/kanban-kit/issues/669))
- Board blendet ausgeblendete Vorhaben aus ([#668](https://github.com/mannewolff/kanban-kit/issues/668))
- Reine Hilfsfunktion für verdeckte Kartennummern ([#667](https://github.com/mannewolff/kanban-kit/issues/667))
- Massenaktionen treffen nur noch sichtbare Karten ([#621](https://github.com/mannewolff/kanban-kit/issues/621))
- Tests: Massenaktionen nur auf sichtbaren Karten (Issue #621, rot)

## [1.27.0] – 2026-08-31

- Reviewer neu eingestellt
- Reviewer neu eingestellt
- chore: v1.26.2
- Codex reviewer hinzugefügt
- Haekchen je Label unterscheidbar benennen (Review zu #664)
- Haekchen "auf der Vorhaben-Kachel zaehlen" im Label-Dialog ([#664](https://github.com/mannewolff/kanban-kit/issues/664))
- Vorhaben-Kachel zeigt Zusammensetzung und Zustaende ([#663](https://github.com/mannewolff/kanban-kit/issues/663))
- Reine Hilfsfunktion fuer die Vorhaben-Kachel ([#662](https://github.com/mannewolff/kanban-kit/issues/662))
- Zustand je Zeile im Herkunftsbaum ([#661](https://github.com/mannewolff/kanban-kit/issues/661))
- docs: Praesentations-Verzeichnis ohne Umlaut benennen
- docs: Factsheet-Unterlagen unter präsentationen/ ablegen
- Doppelte Kartenliste aus dem Vorhaben-Dialog entfernen ([#660](https://github.com/mannewolff/kanban-kit/issues/660))
- Label traegt "auf der Vorhaben-Kachel zaehlen" ([#659](https://github.com/mannewolff/kanban-kit/issues/659))
- chore: v1.26.1
- Anzeigename "Leitstand" und farbige Kopfleiste
- Designsprache von "Kante" auf "Panel" umstellen
- Struktur-Leitplanke schaerfen und pruefbar machen (Review zu #648)
- Label-Chips stuerzen nicht mehr an unbrauchbaren Farben ab (Review zu #649/#650/#652)
- Redesign Kante, Paket 6: Shell und Auth ([#653](https://github.com/mannewolff/kanban-kit/issues/653))
- Redesign Kante, Paket 5: Modale und Dialoge ([#652](https://github.com/mannewolff/kanban-kit/issues/652))
- Redesign Kante, Paket 4: Kacheln und Kennzahlen ([#651](https://github.com/mannewolff/kanban-kit/issues/651))
- Redesign Kante, Paket 3: Listen und Tabellen ([#650](https://github.com/mannewolff/kanban-kit/issues/650))
- Redesign Kante, Paket 2: Board und Ideen-Board ([#649](https://github.com/mannewolff/kanban-kit/issues/649))
- Redesign Kante, Paket 1: Token-Ebene ([#648](https://github.com/mannewolff/kanban-kit/issues/648))

## [1.26.0] – 2026-08-31

- chore: v1.25.1
- Dokumentation der Vorhaben-Kachel und des Baums nachziehen ([#646](https://github.com/mannewolff/kanban-kit/issues/646))
- Vorgang eroeffnen an der Kartenmaske ([#647](https://github.com/mannewolff/kanban-kit/issues/647))
- Board-weiten Herkunftsbaum zurueckbauen ([#645](https://github.com/mannewolff/kanban-kit/issues/645))
- Baum in die Vorhaben-Detailansicht, Reiter und Aufklapp-Liste entfallen ([#644](https://github.com/mannewolff/kanban-kit/issues/644))
- Herkunftsbaum je Vorhaben als Endpunkt ([#643](https://github.com/mannewolff/kanban-kit/issues/643))
- Wurzelfilter im Herkunftsbaum lockern ([#642](https://github.com/mannewolff/kanban-kit/issues/642))
- Vorhaben-Kachel nennt ihre Anforderung und zaehlt Arbeitspakete ([#641](https://github.com/mannewolff/kanban-kit/issues/641))
- Vorgang eroeffnen: Vorhaben aus einer Karte anlegen ([#640](https://github.com/mannewolff/kanban-kit/issues/640))
- Anforderungskarte setzen, lesen und beim Projektwechsel aufraeumen ([#639](https://github.com/mannewolff/kanban-kit/issues/639))
- Anforderungskarte am Vorhaben persistieren ([#638](https://github.com/mannewolff/kanban-kit/issues/638))
- Zustandslabels des Reviews einschalten

## [1.25.0] – 2026-08-30

- chore: v1.24.1
- Sonar-Fehlalarm java:S2077 an findByCardIds unterdruecken ([#625](https://github.com/mannewolff/kanban-kit/issues/625))

## [1.24.0] – 2026-08-30

- chore: v1.23.1
- Dokumentation der Vorhaben-Zugehoerigkeit nachziehen ([#635](https://github.com/mannewolff/kanban-kit/issues/635))
- Vorhaben-Kachel klappt die gezaehlten Karten auf ([#634](https://github.com/mannewolff/kanban-kit/issues/634))
- listEpics zaehlt den Nachfahrenbaum, EpicView traegt Mitglieder und Wurzeln ([#633](https://github.com/mannewolff/kanban-kit/issues/633))
- Zugehoerigkeit eines Vorhabens als Herkunftsabstieg berechnen ([#632](https://github.com/mannewolff/kanban-kit/issues/632))

## [1.23.0] – 2026-08-28

- chore: v1.22.1
- Reiter Fortschritt und Herkunft auf der Vorhaben-Seite ([#613](https://github.com/mannewolff/kanban-kit/issues/613))
- Sprungziel an der Abhaengigkeitsmarke ([#612](https://github.com/mannewolff/kanban-kit/issues/612))
- Baumkomponente mit Tastaturfuehrung und Screenreader-Auszeichnung ([#611](https://github.com/mannewolff/kanban-kit/issues/611))
- Herkunftsbaum als Endpunkt berechnen ([#609](https://github.com/mannewolff/kanban-kit/issues/609))
- md Dateien in Tickets überführt.

## [1.22.0] – 2026-08-27

- Version 1.21.3
- Herkunftsfeld in der Kartenmaske ([#608](https://github.com/mannewolff/kanban-kit/issues/608))
- Herkunft ueber die normale Karten-API schreibbar ([#607](https://github.com/mannewolff/kanban-kit/issues/607))
- Version 1.21.2
- Herkunft als Nummer ausgeliefert ([#605](https://github.com/mannewolff/kanban-kit/issues/605))
- Ingest nimmt derivedFrom entgegen ([#604](https://github.com/mannewolff/kanban-kit/issues/604))
- Herkunfts-Aufloesung und Transfer-Regel ([#603](https://github.com/mannewolff/kanban-kit/issues/603))
- Herkunft in Domaene und Persistenz ([#602](https://github.com/mannewolff/kanban-kit/issues/602))
- Spalte derived_from_card_id anlegen ([#601](https://github.com/mannewolff/kanban-kit/issues/601))
- docs: Sidebar-Eintrag fuer die Testsuite-Doku
- docs: Testcontainers-Setup unter Colima dokumentieren
- Version 1.21.1
- Doku-Site auf Vorhaben ziehen ([#599](https://github.com/mannewolff/kanban-kit/issues/599))
- docs: Entwickler-Guides auf Vorhaben ziehen ([#598](https://github.com/mannewolff/kanban-kit/issues/598))
- Dokumentation auf Vorhaben ziehen ([#597](https://github.com/mannewolff/kanban-kit/issues/597))
- Rollen-Ansicht nennt die Ressource Vorhaben ([#596](https://github.com/mannewolff/kanban-kit/issues/596))
- Route /vorhaben mit Weiterleitung und Navigationseintrag ([#595](https://github.com/mannewolff/kanban-kit/issues/595))
- Oberflaechentexte auf Vorhaben ziehen ([#594](https://github.com/mannewolff/kanban-kit/issues/594))
- Backend-Vokabel auf Vorhaben ziehen ([#593](https://github.com/mannewolff/kanban-kit/issues/593))
- diffs von früher

## [1.21.0] – 2026-08-14

- Version 1.20.1
- Board-Wechsel in der App-Shell verdrahten ([#587](https://github.com/mannewolff/kanban-kit/issues/587))
- Karten aus der Suche heraus bearbeitbar machen ([#586](https://github.com/mannewolff/kanban-kit/issues/586))
- Vertrag fuer Vorschlags- und Protokollbloecke festlegen ([#585](https://github.com/mannewolff/kanban-kit/issues/585))
- BoardSwitcher mit Tastaturfuehrung und Zielpruefung ([#584](https://github.com/mannewolff/kanban-kit/issues/584))
- Board-Verlauf als Hook mit nutzerspezifischer Ablage ([#583](https://github.com/mannewolff/kanban-kit/issues/583))
- Plattform-Admin-Bypass fuer die Mitgliederliste ([#582](https://github.com/mannewolff/kanban-kit/issues/582))
- Rollenaufloesung in useProjectRole zentralisieren ([#581](https://github.com/mannewolff/kanban-kit/issues/581))
- pairs gewechselt weil codex donw war2
- Kommentare als Markdown rendern und mehrzeilig verfassen ([#575](https://github.com/mannewolff/kanban-kit/issues/575))
- kanbancompat: Labels atomar zu Karten hinzufuegen und entfernen ([#574](https://github.com/mannewolff/kanban-kit/issues/574))
- Karten sollen direkt im board landen

## [1.20.0] – 2026-08-11

- chore: Version auf 1.19.1 (push main)
- kanbancompat: externalKey in den Items ausliefern ([#573](https://github.com/mannewolff/kanban-kit/issues/573))

## [1.19.0] – 2026-08-11

- chore: Version auf 1.18.1 (push main)
- Textgrenzen auf 50.000 Zeichen vereinheitlichen ([#572](https://github.com/mannewolff/kanban-kit/issues/572))

## [1.18.0] – 2026-08-11

- chore: Version auf 1.17.1 (push main)
- kanbancompat: Titel und Body einer Karte ändern ([#571](https://github.com/mannewolff/kanban-kit/issues/571))
- Löschen in das Karten-Kontextmenü aufnehmen ([#570](https://github.com/mannewolff/kanban-kit/issues/570))

## [1.17.0] – 2026-08-10

- chore: Version auf 1.16.1 (push main)
- direct-Ingest respektiert die angeforderte Spalte ([#569](https://github.com/mannewolff/kanban-kit/issues/569))

## [1.16.0] – 2026-08-10

- chore: Version auf 1.15.1 (push main)
- Codex als Pflichtreviewer festgelegt
- Befunde aus dem Codex-Review nachziehen (Issues #565, #566)
- Anpassung für Codex durchgeführt
- Abhaengigkeiten ueber den Ingest setzen ([#566](https://github.com/mannewolff/kanban-kit/issues/566))
- Karte mit vorgegebener Nummer ueber den Ingest anlegen ([#565](https://github.com/mannewolff/kanban-kit/issues/565))

## [1.15.0] – 2026-08-07

- chore: Version auf 1.14.1 (push main)
- Login-Meldung am Freigabe-Gate verweist an den Betreiber ([#562](https://github.com/mannewolff/kanban-kit/issues/562))
- workflow.config.json ist nicht mehr teil von .gitignore
- Freigabe-Regel fuer Plattform-Admins an allen Pruefstellen ([#561](https://github.com/mannewolff/kanban-kit/issues/561))
- Zeitabhaengige Luecke im Faelligkeitsdatum-Test schliessen
- Login-Meldung am Freigabe-Gate nennt den Ausweg ([#560](https://github.com/mannewolff/kanban-kit/issues/560))
- Bestehende Plattform-Admins ohne Freigabe nachstempeln ([#558](https://github.com/mannewolff/kanban-kit/issues/558))
- Doku: Erst-Admin per Datenbank setzt die Freigabe mit ([#559](https://github.com/mannewolff/kanban-kit/issues/559))
- Befoerderung zum Plattform-Admin gibt implizit frei ([#557](https://github.com/mannewolff/kanban-kit/issues/557))
- Login-Gate: Plattform-Admin braucht keine Freigabe ([#556](https://github.com/mannewolff/kanban-kit/issues/556))

## [1.14.0] – 2026-07-31

- chore: Version auf 1.13.3 (push main)
- Sonar: Fence-Zeichenvergleich auf startsWith umstellen ([#546](https://github.com/mannewolff/kanban-kit/issues/546))
- Sonar: Backtracking beim Abschneiden der Schlussrauten aufloesen ([#545](https://github.com/mannewolff/kanban-kit/issues/545))
- Sonar: Backtracking in der Heading-Regex aufloesen ([#544](https://github.com/mannewolff/kanban-kit/issues/544))
- Sonar: Backtracking in der Code-Fence-Regex aufloesen ([#543](https://github.com/mannewolff/kanban-kit/issues/543))
- Sonar: Verschachtelte Ternary in der Leermeldung aufloesen ([#542](https://github.com/mannewolff/kanban-kit/issues/542))
- Sonar: Mehrdeutigen Abstand vor verstecktem Input aufloesen ([#541](https://github.com/mannewolff/kanban-kit/issues/541))
- Sonar: Kommentarblock an ActivityView als Javadoc formulieren ([#540](https://github.com/mannewolff/kanban-kit/issues/540))
- Sonar: CardActivityEntity-Konstruktor auf sechs Parameter kuerzen ([#539](https://github.com/mannewolff/kanban-kit/issues/539))
- Sonar: Self-Invocation bei createProjectIdea aufloesen ([#538](https://github.com/mannewolff/kanban-kit/issues/538))
- chore: hs_err-Crash-Logs der PIT-Minions ignorieren
- chore: Version auf 1.13.2 (push main)
- Karten-Detail: gespeicherte Abhaengigkeiten sofort anzeigen ([#537](https://github.com/mannewolff/kanban-kit/issues/537))
- chore: Version auf 1.13.1 (push main)
- Sonar-Sync: Findings als Karten aufs Sonar-Board statt als GitHub-Issues ([#536](https://github.com/mannewolff/kanban-kit/issues/536))
- kanbancompat-Ingest: direct-Routing aufs token-gebundene Board ([#535](https://github.com/mannewolff/kanban-kit/issues/535))
- kanbancompat-Ingest: Idempotenz ueber externen Schluessel ([#534](https://github.com/mannewolff/kanban-kit/issues/534))

## [1.13.0] – 2026-07-30

- chore: Version auf 1.12.3 (push main)
- Aktivitaetsverlauf: Token-Herkunft als Tatsache, Modell als Angabe anzeigen ([#518](https://github.com/mannewolff/kanban-kit/issues/518))
- Aktivitaetsverlauf: Herkunft (Session/Token) und Modell-Angabe erfassen ([#517](https://github.com/mannewolff/kanban-kit/issues/517))
- chore: Version auf 1.12.2 (push main)
- Ausreisser-Modal: Klick-Guard und dokumentierte Kontextdaten-Entscheidung ([#516](https://github.com/mannewolff/kanban-kit/issues/516))
- Einzelkarten-Endpoint GET /api/cards/{id} ([#515](https://github.com/mannewolff/kanban-kit/issues/515))
- Durchsatz-Chart: Vertrags-Smoke-Test, aria-hidden und Label-Randfaelle ([#514](https://github.com/mannewolff/kanban-kit/issues/514))
- Sortier-Endpoint: Doku- und Naming-Nacharbeiten aus dem Review ([#510](https://github.com/mannewolff/kanban-kit/issues/510))
- Hero-Kennzahl: Cycle-Time-Datenbasis, KPI-Invariante und robuster Leerfall ([#513](https://github.com/mannewolff/kanban-kit/issues/513))
- Verweildauer-Kacheln: eine Leerwert-Quelle, Engpass erst ab zwei Messungen ([#512](https://github.com/mannewolff/kanban-kit/issues/512))
- Sortier-Toggle: Erfolgsfeedback, Busy-Sperre und Zustands-Doku ([#511](https://github.com/mannewolff/kanban-kit/issues/511))
- Aktiv-Praedikat im CardRepositoryAdapter vereinheitlichen ([#509](https://github.com/mannewolff/kanban-kit/issues/509))
- Spezifikation aus Markdown-Datei einlesen und als Ideen anlegen ([#493](https://github.com/mannewolff/kanban-kit/issues/493))
- Mehrere Ideen in einem Zug anlegen: Batch-Endpoint fuer den Spec-Import ([#492](https://github.com/mannewolff/kanban-kit/issues/492))
- chore: Version auf 1.12.1 (push main)
- Navigationspfad Projekt / Board / Spalte im Karten-Detail ([#491](https://github.com/mannewolff/kanban-kit/issues/491))
- Suchfeld in der Kopfzeile: Kartennummer eingeben und Karte öffnen ([#490](https://github.com/mannewolff/kanban-kit/issues/490))
- Kartensuche per Nummer über alle eigenen Projekte ([#489](https://github.com/mannewolff/kanban-kit/issues/489))
- Abhängigkeits-Verweise im Detail-Modal anklickbar machen ([#488](https://github.com/mannewolff/kanban-kit/issues/488))
- .idea/ ins gitignore überführt
- startboard.sh entfernen

## [1.12.0] – 2026-07-29

- Betriebsversion auf 1.11.3 (push main)
- S2077-Fundstellen im CardRepositoryAdapter bewertet und verankert ([#508](https://github.com/mannewolff/kanban-kit/issues/508))
- Board nach erfolgreichem Sortier-Toggle sofort aktualisieren ([#507](https://github.com/mannewolff/kanban-kit/issues/507))
- Ausreißer-Tabelle im Dashboard zur Karte verlinken ([#485](https://github.com/mannewolff/kanban-kit/issues/485))
- Durchsatz-Diagramm ohne Hover lesbar ([#478](https://github.com/mannewolff/kanban-kit/issues/478))
- Ø Lead Time als Hero-Zahl im Dashboard ([#477](https://github.com/mannewolff/kanban-kit/issues/477))
- Verweildauer als lesbare Kacheln statt Balkenchart ([#476](https://github.com/mannewolff/kanban-kit/issues/476))
- Sortier-Toggle im Spaltenkopf ([#505](https://github.com/mannewolff/kanban-kit/issues/505))
- Spalte nach Kartennummer sortieren ([#504](https://github.com/mannewolff/kanban-kit/issues/504))
- Betriebsversion auf 1.11.2 (push main)
- Attachment-Metadaten und Objektspeicher konsistent halten ([#503](https://github.com/mannewolff/kanban-kit/issues/503))
- E-Mail-Versand ueber die Outbox statt innerhalb der Transaktion ([#502](https://github.com/mannewolff/kanban-kit/issues/502))
- Transaktionale Outbox als Fundament fuer Mail und Objektspeicher ([#501](https://github.com/mannewolff/kanban-kit/issues/501))
- Kartennummern und Positionen konkurrenzfest vergeben ([#499](https://github.com/mannewolff/kanban-kit/issues/499))
- Rollen-Invarianten serialisieren ([#498](https://github.com/mannewolff/kanban-kit/issues/498))
- Einmal-Tokens atomar verbrauchen ([#497](https://github.com/mannewolff/kanban-kit/issues/497))
- Unique-Kollisionen als 409 statt 500 ausliefern ([#496](https://github.com/mannewolff/kanban-kit/issues/496))
- JaCoCo-Report nach den ITs erzeugen ([#495](https://github.com/mannewolff/kanban-kit/issues/495))
- Release 1.11.1 (push main)
- Sonar S1192: Parameternamen in JdbcCardLabelRepository als Konstante

## [1.11.0] – 2026-07-28

- Release 1.10.2 (push main)
- Verschachtelte Ternary im Ideen-Pool aufloesen ([#474](https://github.com/mannewolff/kanban-kit/issues/474))
- JdbcCardLabelRepository auf NamedParameterJdbcTemplate umbauen ([#473](https://github.com/mannewolff/kanban-kit/issues/473))
- Review-Nachlese zum Fassaden-Batch ([#472](https://github.com/mannewolff/kanban-kit/issues/472))
- CLI: Kommentare in 'tbx issue get' ausgeben ([#471](https://github.com/mannewolff/kanban-kit/issues/471))
- ArchUnit-Modulgrenzen: Fassaden-Whitelist und config-Regeln ([#470](https://github.com/mannewolff/kanban-kit/issues/470))
- Kommentar-Lesepfad: Board-Scope-Negativfall schaerfen ([#469](https://github.com/mannewolff/kanban-kit/issues/469))
- Startnummer-Endpoints: Web-Verdrahtung per MockMvc pinnen ([#468](https://github.com/mannewolff/kanban-kit/issues/468))
- Ideen-Planungsboard: Leer-Guard fuer Einplanen-Knopf pinnen ([#467](https://github.com/mannewolff/kanban-kit/issues/467))
- PAT-Kommentar-Body: Längenbegrenzung ergänzen ([#466](https://github.com/mannewolff/kanban-kit/issues/466))
- PIT-Gate: config-Paket einbeziehen ([#465](https://github.com/mannewolff/kanban-kit/issues/465))
- SSE-Event-Kette per Integrationstest absichern ([#464](https://github.com/mannewolff/kanban-kit/issues/464))
- Ziel-Board-Dialog: Projekt und Spalte vorbelegen ([#450](https://github.com/mannewolff/kanban-kit/issues/450))
- Ungeprüfte Schreib-Fassaden maschinell absichern
- Anhänge: projekt-basierte Rechteprüfung festnageln ([#462](https://github.com/mannewolff/kanban-kit/issues/462))
- Release 1.10.1 (push main)
- kanbancompat: GET-Endpoint fuer Item-Kommentare ([#448](https://github.com/mannewolff/kanban-kit/issues/448))
- Coverage: ProjectStartNumberControllerTest ergänzt ([#446](https://github.com/mannewolff/kanban-kit/issues/446))
- Sonar S6582: optional chaining in IdeaPlanningBoard, ESLint-Gate ergänzt
- Sonar S7467: ungenutztes catch-e durch Unnamed-Pattern ersetzen ([#444](https://github.com/mannewolff/kanban-kit/issues/444))
- Sonar S2589: tote Bedingungen in BootstrapService entfernen
- Doku: nutzung.md an tatsächlichen Code-Stand nachziehen
- card→project-Restfassade: PermissionChecker.isMember + ProjectService.setNextCardNumber ([#461](https://github.com/mannewolff/kanban-kit/issues/461))
- project→auth-Fassade: AppUserRepository-Zugriff aus project/comment kapseln ([#460](https://github.com/mannewolff/kanban-kit/issues/460))
- board-Modul-Fassade: fremde Zugriffe auf BoardRepository/Board/BoardColumn kapseln ([#459](https://github.com/mannewolff/kanban-kit/issues/459))
- card-Modul-Fassade: fremde Zugriffe auf CardRepository/Card/Label kapseln ([#458](https://github.com/mannewolff/kanban-kit/issues/458))
- Security-Composition-Root aus dem Auth-Modul herausgelöst

## [1.10.0] – 2026-07-27

- Release 1.9.1 (push main)
- Doku: nutzung.md an den Batch anpassen ([#436](https://github.com/mannewolff/kanban-kit/issues/436))
- kanbancompat: Labels je Karte exponieren ([#457](https://github.com/mannewolff/kanban-kit/issues/457))
- Ideen-Pool: Beschriftung und Erfolgs-Toast beim Verschieben ([#435](https://github.com/mannewolff/kanban-kit/issues/435))
- Ideen-Seite: Listen-Ansicht entfernen, Suche in die Planen-Ansicht ([#431](https://github.com/mannewolff/kanban-kit/issues/431))
- Ideenbereich: Karte per Drag von Board zu Board verschieben ([#426](https://github.com/mannewolff/kanban-kit/issues/426))
- Ideenbereich: alle Boards untereinander statt Board-Auswahl ([#425](https://github.com/mannewolff/kanban-kit/issues/425))
- Rechte-Matrix vervollstaendigen und Doppelung aufloesen ([#437](https://github.com/mannewolff/kanban-kit/issues/437))
- Karten-Menü: zwei Verschieben-Einträge statt einer Zielspalten-Liste ([#430](https://github.com/mannewolff/kanban-kit/issues/430))
- Spalten-Plus entfernen, Taste + löst Neu anlegen aus ([#429](https://github.com/mannewolff/kanban-kit/issues/429))
- Transfer-Recht richtungsabhängig: projektintern CARD_MOVE statt OWNER ([#424](https://github.com/mannewolff/kanban-kit/issues/424))
- Rechtliches-Abschnitt in der Administration: Copyright und MIT-Hinweis ([#423](https://github.com/mannewolff/kanban-kit/issues/423))
- MIT-Lizenz im Repository verankern ([#422](https://github.com/mannewolff/kanban-kit/issues/422))
- Bulk-Verschieben überträgt die Sichtreihenfolge statt der Klick-Reihenfolge ([#418](https://github.com/mannewolff/kanban-kit/issues/418))
- issues/ als lokalen Board-Fallback ins .gitignore aufnehmen

## [1.9.0] – 2026-07-27

- Release 1.8.2 (push main)
- Ideen-Speicher führt in den Pool: board-los, Nummer bleibt, Bestand migriert ([#433](https://github.com/mannewolff/kanban-kit/issues/433))
- Release 1.8.1 (push main)
- kanbancompat blendet Ideen aus ([#434](https://github.com/mannewolff/kanban-kit/issues/434))
- Issue-Dateien ins Board überführt und entfernt (#438–#447)
- Ideen-Pool aufsteigend sortieren: älteste zuerst ([#419](https://github.com/mannewolff/kanban-kit/issues/419))
- Checkbox-Kurzschreibweise: / und /x beim Tippen expandieren ([#420](https://github.com/mannewolff/kanban-kit/issues/420))
- Listenansicht: Spaltenreihenfolge und Breite global merken ([#432](https://github.com/mannewolff/kanban-kit/issues/432))
- Versionskennung im Header größer und kontrastreicher ([#421](https://github.com/mannewolff/kanban-kit/issues/421))
- Sonar-Findings + Coverage: vier Issue-Dateien zum Nachziehen (Tracker im Deploy)

## [1.8.0] – 2026-07-24

- Release 1.7.1 (push main)
- SSE-Endpoints: 403 auf Async-Dispatch beheben (ASYNC/ERROR permitten)
- Karte nach projektweiter Nummer auflösen (GET by-number) ([#408](https://github.com/mannewolff/kanban-kit/issues/408))
- Gate-Regression aus #405 beheben: Spotless + Nichtmitglied-Status 404
- Default-Board bei Projektanlage automatisch anlegen ([#406](https://github.com/mannewolff/kanban-kit/issues/406))
- Board-lose Pool-Ideen editierbar (projekt-basierte Rechte/Ops) ([#405](https://github.com/mannewolff/kanban-kit/issues/405))
- Pool-Idee im CardDetailModal öffnen & bearbeiten ([#404](https://github.com/mannewolff/kanban-kit/issues/404))
- Pool-Ideen sofort projektweit nummerieren (Generator + Ingest-Antwort) ([#402](https://github.com/mannewolff/kanban-kit/issues/402))
- Live-Ideen-Pool: SSE-Backend (Event + Registry + Endpoint + Publikation) ([#401](https://github.com/mannewolff/kanban-kit/issues/401))
- Live-Ideen-Pool: EventSource-Hook + IdeasPage-Verdrahtung ([#400](https://github.com/mannewolff/kanban-kit/issues/400))
- release.mjs als convinient script eingeführt

## [1.7.0] – 2026-07-23

- Release 1.6.1 (push main)
- Speichern-Feedback über globale Toasts ([#393](https://github.com/mannewolff/kanban-kit/issues/393))
- Editiermodus-Hinweisleiste über dem Header ([#392](https://github.com/mannewolff/kanban-kit/issues/392))
- Feld „Nächste Kartennummer" im Projekt-Umbenennen-Dialog ([#391](https://github.com/mannewolff/kanban-kit/issues/391))
- Projekt-Startnummer setzen: Owner-Endpoint im card-Modul ([#390](https://github.com/mannewolff/kanban-kit/issues/390))
- Projekt-Startnummer: Floor-basierte Nummernvergabe + Flyway V20 ([#389](https://github.com/mannewolff/kanban-kit/issues/389))
- Sidebar-Akzentrand von 4px auf 8px verbreitern

## [1.6.0] – 2026-07-23

- Release 1.5.2 (push main)
- Deploy-Pfad korrigieren: /root/opt/kanban-kit ([#384](https://github.com/mannewolff/kanban-kit/issues/384))
- Release 1.5.1 (push main)
- Transfer & Abhängigkeiten projektweit: richtungsabhängiger Umzug ([#386](https://github.com/mannewolff/kanban-kit/issues/386))
- Kartennummern projektweit: Generator + Constraint + Migration V19 ([#385](https://github.com/mannewolff/kanban-kit/issues/385))
- Automatisches Deployment via self-hosted Actions-Runner ([#384](https://github.com/mannewolff/kanban-kit/issues/384))
- Toast-Tests: drei 3-s-Auto-Hide-Tests parametrisieren ([#382](https://github.com/mannewolff/kanban-kit/issues/382))

## [1.5.0] – 2026-07-23

- Version 1.4.4 (push main)
- Board-Listenansicht: Sortieren nur bei ausgewählter Einzelspalte ([#381](https://github.com/mannewolff/kanban-kit/issues/381))
- Ideen-Planungsboard: Direktsprung in die Board-Listenansicht ([#380](https://github.com/mannewolff/kanban-kit/issues/380))
- Ideen-Planungsboard: Ziehgriff-Icon an den verschiebbaren Zeilen ([#379](https://github.com/mannewolff/kanban-kit/issues/379))
- Toast-Benachrichtigungen: oben-mittiger Stapel mit Severity-Logik ([#378](https://github.com/mannewolff/kanban-kit/issues/378))
- Ideen-Planungsboard: Backlog-Reihenfolge per Drag sortieren ([#377](https://github.com/mannewolff/kanban-kit/issues/377))
- Ideen-Seite: Umschalter Liste/Planen + gestapeltes Planungs-Board ([#376](https://github.com/mannewolff/kanban-kit/issues/376))
- install.mjs ins gitignore
- Version 1.4.3 (push main)
- BoardListPage: per-Board-Ideen-Zone zurückbauen ([#375](https://github.com/mannewolff/kanban-kit/issues/375))
- Projekt-Ideen-Seite + Nav + API-Client ([#374](https://github.com/mannewolff/kanban-kit/issues/374))
- kanbancompat-Ingest in den Projekt-Ideen-Pool routen ([#373](https://github.com/mannewolff/kanban-kit/issues/373))
- Ideen-Pool: Use-Cases + Endpoints (anlegen/einplanen/zurück/Liste) ([#372](https://github.com/mannewolff/kanban-kit/issues/372))
- Datenmodell: Karte board-optional + project_id (Migration V18) ([#371](https://github.com/mannewolff/kanban-kit/issues/371))
- Version 1.4.2 (push main)
- Self-Invocation von @Transactional-Methoden (S6809) auflösen ([#370](https://github.com/mannewolff/kanban-kit/issues/370))
- Version 1.4.1 (push main)
- Leere close()-Methode im EventSource-Test-Stub kommentieren ([#369](https://github.com/mannewolff/kanban-kit/issues/369))
- Reliability-Gate: 3x S2583 durch korrekte @Nullable-Annotationen beheben ([#368](https://github.com/mannewolff/kanban-kit/issues/368))

## [1.4.0] – 2026-07-21

- Version 1.3.1 (push main)
- Live-Board: EventSource-Hook + Verdrahtung in Board- und Listen-Ansicht ([#343](https://github.com/mannewolff/kanban-kit/issues/343))
- Live-Board: BoardChangedEvent bei allen board-relevanten Card-Mutationen publizieren ([#342](https://github.com/mannewolff/kanban-kit/issues/342))
- Live-Board: SSE-Infrastruktur (Endpoint + Emitter-Registry + Heartbeat + Auth) ([#341](https://github.com/mannewolff/kanban-kit/issues/341))
- Administration: Done-Aufbewahrung anzeigen/ändern + Countdown auf effektiven Wert ([#340](https://github.com/mannewolff/kanban-kit/issues/340))
- Global änderbare Done-Aufbewahrung: Persistenz + Admin-Endpoint + effektiver Wert ([#339](https://github.com/mannewolff/kanban-kit/issues/339))
- „Boards" in der Navigation auf Projekte-Ebene hochziehen ([#338](https://github.com/mannewolff/kanban-kit/issues/338))
- DataTable: Spalten-Ziehgriff sichtbar machen ([#337](https://github.com/mannewolff/kanban-kit/issues/337))
- BoardPage-Tests gegen reloadEpics-Coverage-Flake härten ([#336](https://github.com/mannewolff/kanban-kit/issues/336))
- Sonar java.libraries: Tilde-Pfad durch target/dependency ersetzen ([#335](https://github.com/mannewolff/kanban-kit/issues/335))
- Board-Button "Neues Item" neutral benennen ([#333](https://github.com/mannewolff/kanban-kit/issues/333))

## [1.3.0] – 2026-07-21

- Projektmitglieder-Liste auf DataTable umstellen ([#332](https://github.com/mannewolff/kanban-kit/issues/332))
- Nutzerverwaltung (/admin) auf DataTable umstellen ([#331](https://github.com/mannewolff/kanban-kit/issues/331))
- Wiederverwendbare DataTable-Komponente ([#330](https://github.com/mannewolff/kanban-kit/issues/330))
- Zebra-Zeilenfarben für alle Tabellen global im Theme ([#329](https://github.com/mannewolff/kanban-kit/issues/329))
- Namen-Bleistift in der Nutzerverwaltung an den Editiermodus koppeln ([#328](https://github.com/mannewolff/kanban-kit/issues/328))
- Karten-Anlegen mit vollem Feldsatz verdrahten ([#327](https://github.com/mannewolff/kanban-kit/issues/327))
- Gemeinsame CardFields-Komponente aus CardEditForm extrahieren ([#326](https://github.com/mannewolff/kanban-kit/issues/326))
- Karten-Anlegen: Fälligkeit, Zuständige und Labels atomar im Create-Endpoint ([#325](https://github.com/mannewolff/kanban-kit/issues/325))
- Item-/Epic-Bearbeiten aus dem Editiermodus-Gate lösen ([#324](https://github.com/mannewolff/kanban-kit/issues/324))
- Sonar S7755/S7758: .at()/codePointAt() statt Index/charCodeAt (New Code)
- Frontend-Coverage auf echte 100/100/100/100 + finaler Ratchet ([#323](https://github.com/mannewolff/kanban-kit/issues/323))
- Sonar-Duplikate: useBoardRole-Hook extrahieren + config.ts testen ([#322](https://github.com/mannewolff/kanban-kit/issues/322))
- Sonar-Duplikate: gemeinsame Basis fuer die Auth-Token-Entities ([#321](https://github.com/mannewolff/kanban-kit/issues/321))

## [1.2.0] – 2026-07-20

- Release 1.1.1 (push main)
- Toten Namen 'Stellwerk' aus Produkt und Doku entfernen
- Token-Dialog: Select-Labels shrinken lassen (Fix zu #319)
- API-Tokens: Dogfooding-Doku auf UI-Weg umstellen ([#320](https://github.com/mannewolff/kanban-kit/issues/320))
- API-Tokens: Erzeugen/Listen/Widerrufen auf der Administration-Seite ([#319](https://github.com/mannewolff/kanban-kit/issues/319))
- API-Tokens: Frontend-API-Wrapper ([#318](https://github.com/mannewolff/kanban-kit/issues/318))

## [1.1.0] – 2026-07-20

- Release 1.0.2 (push main)
- Doku-Inhalt nachziehen: Ideen-Speicher + Editiermodus ([#316](https://github.com/mannewolff/kanban-kit/issues/316))
- "Dokumentation"-Eintrag im Administrations-Bereich, oeffnet /docs/ ([#315](https://github.com/mannewolff/kanban-kit/issues/315))
- Doku-Auslieferung unter /docs/ (VitePress in die App bündeln) ([#314](https://github.com/mannewolff/kanban-kit/issues/314))
- Release 1.0.1 (push main)
- Sonar-Findings beheben: MarkdownInput-Props Readonly + Ternary aufloesen (#250, #312)

## [1.0.0] – 2026-07-20

- Release 0.8.2 (push main)
- Sonar-Sync: Severity-Filter + NUL-Bytes bereinigen + Fehlerausgabe ([#310](https://github.com/mannewolff/kanban-kit/issues/310))
- ESLint-Leitplanken: testing-library/recommended + no-deprecated ([#309](https://github.com/mannewolff/kanban-kit/issues/309))
- TaskMarkdown-Props als Readonly markieren ([#250](https://github.com/mannewolff/kanban-kit/issues/250))
- Ideen-Speicher: Zwei-Zonen-Listenansicht mit Hochziehen und Idee-anlegen ([#247](https://github.com/mannewolff/kanban-kit/issues/247))
- Ideen-Speicher: Frontend-API + Board-Unsichtbarkeit + Karten-Aktion ([#246](https://github.com/mannewolff/kanban-kit/issues/246))
- Ideen-Speicher: Service-Logik + Endpoints + kanbancompat-Ingest ([#245](https://github.com/mannewolff/kanban-kit/issues/245))
- Ideen-Speicher: Datenmodell + Migration V16 ([#244](https://github.com/mannewolff/kanban-kit/issues/244))
- docs-site: statischen VitePress-Build reparieren (srcDir außerhalb Root)
- Editiermodus-Gating der Bleistifte auf den Projekt-Seiten ([#243](https://github.com/mannewolff/kanban-kit/issues/243))
- Editiermodus-Gating der Bleistifte auf den Board-Seiten ([#242](https://github.com/mannewolff/kanban-kit/issues/242))
- Administration-Eintrag unten in der Sidebar ([#241](https://github.com/mannewolff/kanban-kit/issues/241))
- AdministrationPage mit Editiermodus-Schalter ([#240](https://github.com/mannewolff/kanban-kit/issues/240))
- Editiermodus-Context einführen ([#239](https://github.com/mannewolff/kanban-kit/issues/239))

## [0.8.0] – 2026-07-17

- Coverage-Schwellen in vite.config.ts final anheben ([#237](https://github.com/mannewolff/kanban-kit/issues/237))
- Restliche Fehlerpfade in bestehenden Seiten-Tests bündeln ([#236](https://github.com/mannewolff/kanban-kit/issues/236))
- ProjectMembersPage.tsx Coverage-Lücken schließen ([#235](https://github.com/mannewolff/kanban-kit/issues/235))
- BoardListPage.tsx Coverage-Lücken schließen ([#234](https://github.com/mannewolff/kanban-kit/issues/234))
- BoardPage.tsx Coverage-Lücken schließen ([#233](https://github.com/mannewolff/kanban-kit/issues/233))
- CardDetailModal.tsx Coverage-Lücken schließen ([#232](https://github.com/mannewolff/kanban-kit/issues/232))
- BoardView.tsx Coverage-Lücken schließen ([#231](https://github.com/mannewolff/kanban-kit/issues/231))
- AppShell.tsx Coverage-Lücken schließen ([#230](https://github.com/mannewolff/kanban-kit/issues/230))
- AuthContext.tsx direkt testen ([#229](https://github.com/mannewolff/kanban-kit/issues/229))
- Vier leere Auth-Flow-Seiten testen ([#228](https://github.com/mannewolff/kanban-kit/issues/228))
- CardDetailModal.tsx: erneut aufgetretene Sonar-Findings nach #214 bündeln ([#222](https://github.com/mannewolff/kanban-kit/issues/222))
- NewCardModal.tsx: SelectProps/InputLabelProps auf slotProps migrieren ([#224](https://github.com/mannewolff/kanban-kit/issues/224))
- BoardView.tsx: SelectProps/InputLabelProps auf slotProps migrieren ([#221](https://github.com/mannewolff/kanban-kit/issues/221))
- DashboardPage.tsx: Props als Readonly markieren ([#227](https://github.com/mannewolff/kanban-kit/issues/227))
- SnackbarProvider.tsx: Props als Readonly markieren ([#226](https://github.com/mannewolff/kanban-kit/issues/226))
- PasswordField.tsx: Props als Readonly markieren ([#225](https://github.com/mannewolff/kanban-kit/issues/225))
- LabelManagerDialog.tsx: Props als Readonly markieren ([#223](https://github.com/mannewolff/kanban-kit/issues/223))
- AuthCard.tsx: Props als Readonly markieren ([#220](https://github.com/mannewolff/kanban-kit/issues/220))
- AppShell.tsx: primaryTypographyProps auf slotProps migrieren ([#219](https://github.com/mannewolff/kanban-kit/issues/219))
- AuthContext.tsx: Props als Readonly markieren ([#218](https://github.com/mannewolff/kanban-kit/issues/218))
- SonarQube-Sync: PROJECTS_PAT statt GITHUB_TOKEN fuer GitHub-Projects-v2-Zugriff
- Kopierte Karte immer nach Backlog statt in die Ursprungsspalte ([#207](https://github.com/mannewolff/kanban-kit/issues/207))
- Release-Tag-Handling: annotated Tags auf den Release-Commit ([#206](https://github.com/mannewolff/kanban-kit/issues/206))
- SonarCloud-Trigger von production auf main umstellen
- Frontend-Coverage-Philosophie in CLAUDE-react.md verankern ([#217](https://github.com/mannewolff/kanban-kit/issues/217))
- Frontend-API-Schicht direkt testen statt ausschließen ([#216](https://github.com/mannewolff/kanban-kit/issues/216))
- sonar.coverage.exclusions ergänzen: Sonar-Scope an lokale Gates angleichen ([#215](https://github.com/mannewolff/kanban-kit/issues/215))
- CardDetailModal.tsx: alle Sonar-Findings bündeln ([#214](https://github.com/mannewolff/kanban-kit/issues/214))
- SonarCloud-Sync: auf Compute-Engine-Task warten ([#213](https://github.com/mannewolff/kanban-kit/issues/213))
- Restliche Sonar-Code-Smells abräumen ([#212](https://github.com/mannewolff/kanban-kit/issues/212))
- markdownTasks.ts: Regex-Backtracking + Template-Literals + exec ([#210](https://github.com/mannewolff/kanban-kit/issues/210))
- BoardView.tsx: Komplexitäts-Smells reduzieren ([#211](https://github.com/mannewolff/kanban-kit/issues/211))
- Component-Props als Readonly markieren ([#209](https://github.com/mannewolff/kanban-kit/issues/209))
- MUI inputProps-Deprecation auf slotProps migrieren ([#208](https://github.com/mannewolff/kanban-kit/issues/208))
- SonarCloud: Analyse immer als main-Branch melden (Free-Plan-Fix)

## [0.7.0] – 2026-07-15

- Archivierte Karten wiederherstellen: Restore in Listenansicht und Detail-Modal ([#204](https://github.com/mannewolff/kanban-kit/issues/204))
- Bulk-Aktion 'In den Papierkorb': BulkActionBar + Anbindung mit Bestätigung ([#203](https://github.com/mannewolff/kanban-kit/issues/203))
- Bulk-Löschen mehrerer Karten in den Papierkorb: POST /api/cards/bulk-delete ([#202](https://github.com/mannewolff/kanban-kit/issues/202))
- Breadcrumb auf restliche Seiten ausrollen: Mitglieder, Liste, Epics, Dashboard ([#201](https://github.com/mannewolff/kanban-kit/issues/201))
- Wiederverwendbare Breadcrumb-Komponente: vollständiger Pfad, nur letztes Segment fett ([#200](https://github.com/mannewolff/kanban-kit/issues/200))
- Auto-Routing: bei manueller Projektauswahl mit einem Board durchrouten ([#199](https://github.com/mannewolff/kanban-kit/issues/199))
- Bulk-Aktionen anbinden: API, Bulk-Transfer-Dialog und Archiv-Bestätigung ([#198](https://github.com/mannewolff/kanban-kit/issues/198))
- Karten-Auswahlmodus im Board mit Mehrfachauswahl und BulkActionBar-Gerüst ([#197](https://github.com/mannewolff/kanban-kit/issues/197))
- Bulk-Verschieben mehrerer Karten auf ein anderes Board: POST /api/cards/bulk-transfer ([#196](https://github.com/mannewolff/kanban-kit/issues/196))
- Bulk-Archivieren mehrerer Karten: POST /api/cards/bulk-archive ([#195](https://github.com/mannewolff/kanban-kit/issues/195))
- Release-Changelog-Tooling: gen-changelog.mjs, CHANGELOG.md, Git-Tags ([#194](https://github.com/mannewolff/kanban-kit/issues/194))

## [0.6.0] – 2026-07-15

- Marken-Assets und Deployment-Spec einchecken
- CI-Frontend grün: localStorage zwischen Tests zurücksetzen ([#192](https://github.com/mannewolff/kanban-kit/issues/192))
- Einladung: Mail-Fehler sprengt die Einladung nicht mehr als 500 ([#191](https://github.com/mannewolff/kanban-kit/issues/191))
- Anzeigename ändern: Projekt-Owner/Admin in der Mitgliederliste ([#190](https://github.com/mannewolff/kanban-kit/issues/190))
- Anzeigename ändern: Plattform-Admin in der Nutzerverwaltung ([#189](https://github.com/mannewolff/kanban-kit/issues/189))
- Anzeigename ändern: Self-Service + Fundament withDisplayName ([#188](https://github.com/mannewolff/kanban-kit/issues/188))
- Doku: Benutzer- & Betriebsdoku auf Funktionsstand nachziehen
- E-Mail-Texte: Betreffe auf kanban-kit + Einladungs-Weg erklären ([#187](https://github.com/mannewolff/kanban-kit/issues/187))
- Login: konkrete Fehlermeldung statt pauschalem E-Mail-Hinweis ([#186](https://github.com/mannewolff/kanban-kit/issues/186))
- Task-Checkboxen mit Leerzeichen-Varianten robust rendern ([#185](https://github.com/mannewolff/kanban-kit/issues/185))
- Neues Springer-Icon für Favicon und Header
- Papierkorb: Karten-Löschung soft machen und selbst wiederherstellen ([#179](https://github.com/mannewolff/kanban-kit/issues/179))
- Admin: Nutzerkonten sperren/entsperren (deaktivieren) ([#178](https://github.com/mannewolff/kanban-kit/issues/178))
- Karten-Aktivitätsverlauf: wer hat wann was geändert ([#177](https://github.com/mannewolff/kanban-kit/issues/177))
- Labels/Tags an Karten vergeben und danach filtern ([#176](https://github.com/mannewolff/kanban-kit/issues/176))
- Fälligkeitsdatum an Karten setzen und überfällige hervorheben ([#175](https://github.com/mannewolff/kanban-kit/issues/175))
- Karten-Zuweisung: ein oder mehrere Assignees je Karte ([#174](https://github.com/mannewolff/kanban-kit/issues/174))
- Spalten-Zykluszeit: Board-Dashboard-Seite mit KPI-Charts ([#173](https://github.com/mannewolff/kanban-kit/issues/173))
- Spalten-Zykluszeit: KPI-Aggregation + Dashboard-Endpoint ([#172](https://github.com/mannewolff/kanban-kit/issues/172))
- Spalten-Zykluszeit: Transition-Hooks in CardService ([#171](https://github.com/mannewolff/kanban-kit/issues/171))
- Spalten-Zykluszeit: Persistenz-Fundament ([#170](https://github.com/mannewolff/kanban-kit/issues/170))
