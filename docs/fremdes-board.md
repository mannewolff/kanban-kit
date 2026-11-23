# Fremdes Board anbinden

Manche Teams führen ihr Board nicht in kanban-kit, sondern in einem fremden System — häufig in einem
selbst betriebenen GitLab. kanban-kit kann die Karten eines solchen Boards **spiegeln**: Sie stehen in
der gewohnten Board-Ansicht, Leitstand, Durchläufe, Herkunft und Entscheidungskette funktionieren wie
bei einem eigenen Board. **Bearbeitet wird im fremden System**; kanban-kit liest nur und schreibt nie
dorthin.

Der erste und derzeit einzige Fall ist **GitLab**, gleichwertig ob selbst betrieben oder gitlab.com.
Am Ende dieses Kapitels steht, was ein weiteres System liefern muss, um auf demselben Weg dazuzukommen.

## Überblick

| Was | Wo | Wer |
|---|---|---|
| Schlüssel für Zugänge (`MANBAN_SECRET_KEY`) | Umgebung des Servers | Betreiber |
| Erlaubnisliste der Hosts (`MANBAN_FREMDBOARD_HOSTS`) | Umgebung des Servers | Betreiber |
| Lesezugang mit `read_api` | GitLab | Owner des Projekts |
| Adresse, Projektpfad, Zugang, Kit-Konto, Statuslabels | kanban-kit, *Administration → Board-Quelle* | Owner des Projekts |
| Takt des Abgleichs (`manban.fremdboard.*`) | `application.yml` (optional) | Betreiber |

## Voraussetzungen

- **Ein Projekt ohne Karten.** Angebunden wird nur ein Projekt, das in kanban-kit noch keine Karten
  hat — weder auf einem Board noch im Archiv, im Papierkorb oder als Vorhaben. Sonst lehnt kanban-kit
  ab und nennt die Zahlen. Am einfachsten legt man für das fremde Board ein neues Projekt an.
- **Genau ein Board.** Das Projekt darf höchstens ein Board haben (archivierte zählen mit). kanban-kit
  übernimmt es als Spiegel; es braucht je eine Spalte **Backlog**, **Ready**, **In progress**,
  **In review** und **Done**. Das Standard-Board eines neuen Projekts erfüllt das.
- **Genau eine Quelle.** Ein kanban-kit-Projekt spiegelt genau ein GitLab-Projekt mit allen seinen
  Karten (Issues).
- **Endgültig.** Eine Anbindung lässt sich nicht lösen und nicht auf ein anderes GitLab-Projekt
  umhängen. Ändern lassen sich danach nur Zugang, Kit-Konto und Statuslabels. Das Projekt hat ab dann
  kein eigenes Board mehr: Karten lassen sich in kanban-kit nicht anlegen, ziehen oder bearbeiten.
- Einrichten darf nur der **Owner** des Projekts.

## Schlüssel für Zugänge: `MANBAN_SECRET_KEY`

Der Lesezugang wird verschlüsselt in der Datenbank abgelegt (AES-GCM). Den Schlüssel setzt der
Betreiber als Umgebungsvariable, 32 Byte hexadezimal:

```bash
openssl rand -hex 32
```

Den Wert in die `.env` eintragen:

```dotenv
MANBAN_SECRET_KEY=<64 Hex-Zeichen>
```

- **Leer** (Vorgabe): Das Anbinden ist gesperrt. Die Einstellung *Board-Quelle* sagt dann, dass auf dem
  Server kein Schlüssel gesetzt ist.
- **Ungültig** (falsche Länge, keine Hex-Zeichen): Die Anwendung startet nicht.
- **Sichern.** Der Schlüssel ist ein Geheimnis wie `MANBAN_SESSION_SECRET` und gehört in dieselbe
  Ablage, getrennt von den Datenbank-Sicherungen. Eine Sicherung der Datenbank ohne den Schlüssel
  enthält die Zugänge nur unlesbar.
- **Wechsel oder Verlust.** Mit einem anderen Schlüssel lässt sich kein hinterlegter Zugang mehr lesen.
  Der Abgleich bleibt dann auf dem letzten Stand, und die Einstellung *Board-Quelle* meldet, dass der
  Zugang nicht lesbar ist. Der Owner muss den Zugang **neu hinterlegen** (Feld *Zugang* ausfüllen und
  speichern); Adresse, Projektpfad und gespiegelte Karten bleiben dabei erhalten.

## Erlaubnisliste: `MANBAN_FREMDBOARD_HOSTS`

kanban-kit ruft nur Hosts ab, die der Betreiber erlaubt hat — ein Schutz dagegen, dass jemand den
Server Anfragen an beliebige Adressen schicken lässt.

```dotenv
MANBAN_FREMDBOARD_HOSTS=gitlab.com,git.example.org
```

- Kommagetrennt, Groß- und Kleinschreibung zählt nicht; Vorgabe ist `gitlab.com`.
- Ein **selbst betriebenes GitLab** muss hier stehen, sonst lehnt kanban-kit die Adresse beim Anbinden
  ab („Die Adresse ist nicht erlaubt“). Private Adressen im eigenen Netz sind erlaubt, sobald ihr
  Hostname in der Liste steht. Der Port ist frei.
- Abgerufen wird **nur über `https`**, **ohne Weiterleitungen** und mit einem **Zeitlimit von
  10 Sekunden**. Ein GitLab hinter einem Proxy, der umleitet, muss unter seiner endgültigen Adresse
  eingetragen werden.

Beide Variablen stehen auch in der Variablentabelle unter [Betrieb](betrieb.md#umgebungsvariablen).

## Zugang in GitLab: `read_api`

kanban-kit liest über die REST-API v4 von GitLab und braucht dafür einen Zugang, der **nur lesen**
darf. Geeignet ist ein Projekt-, Gruppen- oder persönliches Zugriffstoken mit dem Bereich
**`read_api`**, dessen Konto die Issues des GitLab-Projekts lesen darf. Mehr Rechte braucht kanban-kit
nicht — es schreibt nie nach GitLab.

Der Zugang wird beim Speichern verschlüsselt und **nie wieder angezeigt**, weder in der Oberfläche noch
in Meldungen oder Protokollen. Läuft das Token in GitLab ab, scheitert der Abgleich mit einem lesbaren
Grund; dann ein neues Token anlegen und unter *Zugang* neu hinterlegen.

## Einrichten in kanban-kit

Unter **Administration → Board-Quelle** (nur für Owner sichtbar):

1. **Projekt** wählen.
2. **Adresse** des GitLab, etwa `https://gitlab.com` oder `https://git.example.org`.
3. **Projektpfad** in GitLab, etwa `gruppe/projekt` (auch mit Untergruppen).
4. **Zugang**: das Token mit `read_api`.
5. **Kit-Konto** (optional): siehe unten.
6. **Statuslabels** prüfen, siehe unten.
7. **Anbinden.** kanban-kit liest das GitLab-Projekt einmal zur Probe; scheitert das, nennt es den Grund
   und bindet nicht an.

Danach zeigt die Einstellung, seit wann das Board angebunden ist, wann der letzte Abgleich lief und ob
er gescheitert ist.

## Board-Ansicht für Mitglieder

Jedes Mitglied des Projekts, gleich in welcher Rolle, erfährt in der Board-Ansicht, dass das Board in
GitLab liegt, von wann der gezeigte Stand ist und ob der letzte Abgleich gescheitert ist. Ist nach dem
Anbinden noch kein Abgleich gelungen, steht dort, dass noch kein Stand aus GitLab vorliegt. Das Konto
des Kits und den Fehlertext eines gescheiterten Abgleichs sehen nur der Owner und die Plattform-Admins
in *Administration → Board-Quelle*; den Zugang zeigt kanban-kit niemandem. Wer nicht Mitglied des
Projekts ist, erfährt nichts davon.

## Statuslabels

Jede Karte hat einen von fünf Status: Backlog, Ready, In progress, In review, Done. In GitLab trägt
eine Karte ihren Status als **Label**:

| Status | Vorgabe des Labels | Regel |
|---|---|---|
| Backlog | `Backlog` | auch jede offene Karte ohne Statuslabel |
| Ready | `Ready` | |
| In progress | `In progress` | |
| In review | `In review` | |
| Done | — | jede **geschlossene** Karte, gleich welche Labels sie noch trägt |

- Die Labelnamen sind je Projekt einstellbar und werden **exakt** verglichen.
- Trägt eine offene Karte mehrere Statuslabels, gilt das erste in der Reihenfolge der Tabelle.
- Weitere Spalten oder Listen des GitLab-Boards spielen keine Rolle.
- Karten, deren Titel sie als Dokument des Kits kennzeichnet (`[Idea]`, `[Requirement]`, `[Plan]`),
  tragen keinen Status und stehen in keiner Spalte.
- Die Statuslabels werden nicht als Labels gespiegelt — sie stehen als Spalte da. Alle anderen Labels
  und alle Kommentare werden gespiegelt.
- Werden die Statuslabels geändert, folgt jede Karte beim nächsten Abgleich der Spalte ihres neuen
  Status.

## Kit-Konto

Das **Kit-Konto** ist der GitLab-Benutzername, unter dem das Kit in GitLab schreibt — Karten
verschieben, Labels setzen, Kommentare. kanban-kit erkennt daran, was das Kit getan hat und was ein
Mensch:

- In der **Entscheidungskette** gilt ein Kommentar dieses Kontos mit dem festen Wortlaut des Kits als
  Meldung des Kits.
- Als **Freigeber** gilt, wer in GitLab die Markierung gesetzt hat; hält GitLab das nicht fest, steht
  dort „nicht erfasst“.

Der Vergleich ignoriert Groß- und Kleinschreibung. Bleibt das Feld leer, wird kein Schritt dem Kit
zugeordnet. Das Kit selbst braucht in GitLab einen eigenen Zugang mit Schreibrechten; der gehört zur
Einrichtung des Kits, nicht zu kanban-kit.

## Abgleich

kanban-kit hält die Spiegelung auf zwei Wegen aktuell.

**Anstoß durch das Kit.** Nach jedem eigenen Schritt an einer Karte ruft das Kit
`POST /api/kanban/fremdboard/anstoss` mit der Nummer der Karte, angemeldet mit dem an das Board
gebundenen Projekt-Token (Header `X-Kanban-Token`, siehe [Dogfooding](dogfooding.md)). kanban-kit
liest diese Karte sofort aus GitLab; Leitstand und Durchläufe zeigen den Schritt beim nächsten Laden.
Höchstens ein Abruf je Karte in 10 Sekunden — weitere Anstöße in dieser Zeit antworten mit `202` und
lesen nichts.

**Regelmäßiger Abgleich.** Ein Hintergrundjob holt Änderungen ab, die von Hand in GitLab entstanden
sind. Einstellbar unter `manban.fremdboard.*` (Dauer im ISO-8601-Format):

| Eigenschaft | Vorgabe | Bedeutung |
|---|---|---|
| `manban.fremdboard.takt` | `PT60S` | Abstand zwischen zwei Läufen des Jobs |
| `manban.fremdboard.mindestabstand` | `PT2M` | kürzester Abstand zwischen zwei Abgleichen desselben Projekts |
| `manban.fremdboard.vollabgleich` | `PT60M` | Abstand zwischen zwei vollständigen Abgleichen eines Projekts |

Mit den Vorgaben erscheint eine Änderung von Hand nach **höchstens vier Minuten**. Ein normaler
Abgleich liest nur die seit dem letzten Abgleich geänderten Karten; der Vollabgleich liest zusätzlich
alle Nummern, holt fehlende Karten nach und erkennt gelöschte. Der erste Abgleich nach dem Anbinden
ist ein voller. Mehrere Knoten dürfen den Job zugleich fahren; je Projekt übernimmt ihn genau einer.

**Was zählt.**

- Es gilt immer der **Stand in GitLab**. Widersprechen sich die Meldung eines Durchlaufs und GitLab,
  zeigt kanban-kit GitLab.
- Für Fluss und Verweildauer zählt der **Zeitpunkt, den GitLab** für einen Wechsel festhält, nicht der
  des Abgleichs.
- Jeder Wechsel trägt seine Kennung aus GitLab und zählt **genau einmal**, gleich wie oft er gelesen
  oder angestoßen wird.
- Gezählt wird **ab der Anbindung**. Ältere Spaltenwechsel werden nicht übernommen; Kommentare dagegen
  vollständig, sie sind Inhalt der Karte.

## Ausfall und gelöschte Karten

**GitLab nicht erreichbar.** Scheitert ein Abgleich — GitLab antwortet nicht, das Token ist abgelaufen,
der Schlüssel passt nicht —, behält kanban-kit den zuletzt bekannten Stand. Über den Spalten steht, von
wann er ist, und die Einstellung *Board-Quelle* nennt den Grund. Gelöscht wird bei einem Fehler
nichts; der nächste Takt versucht es erneut. Ein Fehler in einem Projekt hält die anderen nicht auf.

**Karte in GitLab gelöscht.** Der nächste Vollabgleich bemerkt, dass es die Karte in GitLab nicht mehr
gibt, und nimmt sie vom Board. Ihre bisherigen Messungen bleiben in Leitstand und Durchläufen; in der
Entscheidungskette steht sie als „nicht erfasst“. Gelöscht wird erst, wenn der Vollabgleich alles
gelesen hat — ein abgebrochener Vollabgleich löscht nichts.

## Was ein weiteres System liefern muss

Weitere Systeme (GitHub, Jira, Azure DevOps) sollen auf demselben Weg dazukommen. Im Code ist das die
Schnittstelle `FremdBoardQuelle` im Modul `fremdboard`; GitLab ist ihre erste Umsetzung
(`GitLabQuelle`). Ein System lässt sich anbinden, wenn es über einen **reinen Lesezugang** Folgendes
liefert:

1. **Alle Nummern** der Karten eines Projekts, offene wie geschlossene — für den Vollabgleich und das
   Erkennen gelöschter Karten.
2. **Die geänderten Karten** seit einem Zeitpunkt, offene wie geschlossene, die am längsten unveränderte
   zuerst — für den regelmäßigen Abgleich.
3. **Eine Karte** mit Titel, Text, Labels und Zustand (offen/geschlossen), oder die Auskunft, dass es
   sie nicht (mehr) gibt.
4. **Die Wechsel an einer Karte** mit Zeitpunkt, handelnder Person und einer stabilen Kennung, ältester
   zuerst — daraus entstehen Fluss, Verweildauer und Entscheidungskette.
5. **Die Kommentare an einer Karte** mit Autor, Anlage- und Änderungszeit, ältester zuerst.
6. **Die Adresse**, unter der sich eine Karte im System öffnen lässt („In GitLab öffnen“).

Dazu kommen drei Bedingungen an das System selbst: Es trägt den Status einer Karte so, dass er sich
auf die fünf Status abbilden lässt (bei GitLab: Labels und Zustand); es ist über `https` erreichbar;
und seine Benutzernamen sind stabil genug, um das Kit-Konto zu erkennen. Scheitert ein Abruf, meldet
die Umsetzung einen lesbaren Grund, ohne den Zugang zu nennen.
