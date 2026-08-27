# Runbook: Showcase-Server bei STRATO

Stand: 09. Juli 2026

Ziel: Ein kleiner Server, der eine öffentliche Demo-Instanz des Kanban-Boards
trägt und daneben meine eigenen Projekte. **Kein Kundenhosting.** Das Board ist
Open Source, wer es produktiv nutzen will, betreibt es selbst. Betriebsaufwand
nach dem Setup: nahe null. Gleichzeitig ein Objekt, das ich im Workshop
vorführen kann.

Die Repository-Seite steht in einem eigenen Dokument
(`repo-grundausstattung-kanban.md`). Dieses Runbook behandelt nur den Server.

---

## 00 Vorbemerkung: Zwei widersprüchliche STRATO-Quellen

Die STRATO-Produktseite zum Linux V-Server nennt als Virtualisierung KVM und
listet unter "Erweiterungen" ausdrücklich **Docker ready: Vorhanden**.

Der FAQ-Artikel #2427 ("Welche Server und Betriebssysteme bei STRATO sind Docker
ready?") sagt dagegen: Auf V-Servern Linux sei Docker "aufgrund einer
unvollständigen Unterstützung durch den Hersteller der Virtualisierungsplattform
nur bedingt einsetzbar". Dieser Satz stammt aus der Virtuozzo-Zeit. STRATO hat
die V-Server inzwischen auf KVM umgestellt, der FAQ-Artikel ist offensichtlich
nicht nachgezogen.

**Vor der Bestellung:** Eine Mail an den STRATO-Support, Frage wörtlich: "Ist auf
dem aktuellen Linux V-Server (KVM) der uneingeschränkte Betrieb von Docker und
Docker Compose vorgesehen?" Die Antwort archivieren. Wenn STRATO ausweicht: auf
den Dedicated Server Linux gehen, dort ist Docker seit jeher unstrittig
freigegeben.

Der Managed Server scheidet in jedem Fall aus. STRATO sagt selbst: "Auf STRATO
Managed Servern bieten wir Docker nicht an."

---

## 01 Tarifwahl

Aktuelle Linux-V-Server bei STRATO (Preise inkl. MwSt., Einrichtung jeweils 9 Euro):

| Tarif | vCores | RAM | Storage | Preis/Monat |
|---|---|---|---|---|
| VPS XS | 1 | 2 GB | 60 GB NVMe | 2 Euro |
| VPS S | 2 | 2 GB | 90 GB NVMe | 3 Euro |
| VPS M | 4 | 4 GB | 120 GB NVMe | 8 Euro |
| **VPS L** | **6** | **8 GB** | **240 GB NVMe** | **16 Euro** |
| VPS XL | 8 | 16 GB | 480 GB NVMe | 34 Euro |
| VPS XXL | 12 | 24 GB | 720 GB NVMe | 52 Euro |

**Empfehlung: VPS L.** Coolify selbst braucht rund 1 GB, dazu Postgres, Redis,
das Board, ein Reverse Proxy und Luft für zwei, drei weitere Container. Mit 4 GB
(VPS M) wird es beim Image-Build eng, weil Docker beim Bauen kurzzeitig viel RAM
zieht. 8 GB ist der Punkt, an dem man nicht mehr nachdenken muss.

Bei 12-Monats-Laufzeit sind die ersten drei Monate reduziert (VPS L: 5 Euro).
Wenn ich flexibel bleiben will, die 1-Monats-Variante nehmen und die 16 Euro
zahlen. Bei einem Showcase, den ich vielleicht in einem halben Jahr auf eine
andere Größe ziehe, ist das die klügere Wahl.

**Standort im Bestellprozess: Deutschland.** STRATO bietet auch Spanien und
Frankreich an. Für die Souveränitätsargumentation, die ich im Workshop führe,
ist Deutschland der Punkt.

Im Tarif enthalten: 1 IPv4, IPv6, unlimitierter Traffic, bis 1.000 MBit/s,
vorgelagerte Firewall, VNC-Konsole, Rettungssystem, Monitoring Basic.

**Nicht enthalten: Backups.** Das ist der wichtigste Punkt der Tabelle. Siehe 07.

---

## 02 Betriebssystem

STRATO bietet Ubuntu 26.04 LTS und Ubuntu 24.04 LTS an, dazu Debian 13 und 12,
Rocky und AlmaLinux.

**Ubuntu 24.04 LTS nehmen, nicht 26.04.** Zwei Gründe: Coolify und die
Docker-Repositories sind auf 24.04 seit zwei Jahren erprobt, auf 26.04 noch
nicht. Und 24.04 hat regulär Support bis 2029, mit Ubuntu Pro bis 2034. Es
besteht null Eile, auf 26.04 zu gehen.

Der Wechsel des Betriebssystems ist bei STRATO jederzeit kostenlos über eine
Neuinstallation möglich. Also: kein Drama, wenn ich mich verrenne.

---

## 03 Grundhärtung (einmalig, etwa 60 Minuten)

### 3.1 Der Key muss liegen, bevor irgendetwas abgeschaltet wird

Die Reihenfolge ist hier das Ganze. Wer `PasswordAuthentication no` setzt, bevor
der Schlüssel auf dem Server liegt, sperrt sich aus. Also erst Key, dann testen,
dann abschalten.

**Bei STRATO ist das einfach**, weil der Key im Installationsdialog abgefragt
wird. Im Server-Login unter "Mein Server" → "Neuinstallation" öffnet sich ein
Fenster, in dem ich das Betriebssystem wähle, ein Root-Passwort vergebe und einen
öffentlichen SSH-Schlüssel eintrage. STRATO schreibt ihn direkt nach
`/root/.ssh/authorized_keys`. Bei der VC- und VDS-Klasse ist der Key-Login
ohnehin der vorgesehene Weg, ein Passwort-Login per SSH ist dort gar nicht erst
vorgesehen.

Vorher lokal prüfen, was ich überhaupt habe:

```bash
cat ~/.ssh/id_ed25519.pub    # oder id_rsa.pub
```

Falls nichts da ist:

```bash
ssh-keygen -t ed25519 -C "manne@mwolff.org"
```

Passphrase vergeben. Der Key liegt dann verschlüsselt auf der Platte, der
`ssh-agent` hält ihn während der Sitzung entsperrt.

**Formatfalle:** In das STRATO-Feld gehört die einzeilige OpenSSH-Form, also die
Zeile, die mit `ssh-ed25519 AAAA...` oder `ssh-rsa AAAA...` beginnt. Das
PuTTY-eigene Exportformat (mehrzeilig, mit `---- BEGIN SSH2 PUBLIC KEY ----`)
funktioniert nicht. In PuTTYgen den Key aus dem oberen Feld "Public key for
pasting" kopieren, nicht "Save public key".

Danach der erste Test, noch bevor irgendetwas gehärtet wird:

```bash
ssh root@<IP>
```

Muss ohne Passwortabfrage durchgehen. Wenn nicht, hier stehenbleiben und nicht
weitermachen.

### 3.2 Key nachrüsten, falls er nicht bei der Installation dabei war

Der Fall tritt auf dem Hostinger-Server auf, oder wenn ich es bei STRATO vergessen
habe. Solange `PasswordAuthentication` noch auf `yes` steht:

```bash
ssh-copy-id root@<IP>
```

Fragt einmal nach dem Root-Passwort und legt danach alles richtig an. Von Hand
geht es auch, dann aber auf die Rechte achten, sonst ignoriert sshd die Datei
kommentarlos:

```bash
mkdir -p /root/.ssh
chmod 700 /root/.ssh
echo "ssh-ed25519 AAAA... manne@mwolff.org" >> /root/.ssh/authorized_keys
chmod 600 /root/.ssh/authorized_keys
```

**Wenn ich mich doch ausgesperrt habe**, gibt es zwei Rettungswege, beide über den
STRATO-Kunden-Login:

1. **VNC-Konsole.** Die hängt nicht am SSH-Dienst, dort komme ich mit dem
   Root-Passwort rein und kann die `sshd_config` reparieren. Das ist der Grund,
   warum ich das Root-Passwort trotz Key-Login nicht wegwerfe, sondern in den
   Passwortmanager lege.
2. **Rettungssystem.** Startet ein Minimalsystem, mountet das Dateisystem, ich
   schreibe die `authorized_keys` von außen. Danach Rettungssystem stoppen. Im
   Rettungssystem ist der Server nur über die VNC-Konsole erreichbar, nicht über
   SSH.

### 3.3 Nutzer anlegen und Key übernehmen

```bash
adduser manne
usermod -aG sudo manne
rsync --archive --chown=manne:manne /root/.ssh /home/manne/
```

`rsync --archive` ist hier kein Selbstzweck: Es nimmt Rechte und Modus mit, sonst
liegt `authorized_keys` mit 644 im neuen Home und sshd verweigert die Annahme.

Testen, aus einem **zweiten Terminal**, die erste Root-Session offen lassen:

```bash
ssh manne@<IP>
sudo -v
```

Beides muss funktionieren. Erst dann weiter.

### 3.4 Erst jetzt sshd härten

`/etc/ssh/sshd_config`:

```
PermitRootLogin no
PasswordAuthentication no
PubkeyAuthentication yes
```

Bei Ubuntu 24.04 zusätzlich prüfen, ob `/etc/ssh/sshd_config.d/` etwas
überschreibt. Cloud-Images legen dort gerne eine `50-cloud-init.conf` ab, die die
Hauptdatei sticht:

```bash
grep -r "PasswordAuthentication\|PermitRootLogin" /etc/ssh/sshd_config.d/
```

Syntax prüfen, bevor der Dienst neu startet:

```bash
sshd -t && systemctl restart ssh
```

Die alte Session bleibt bestehen, auch wenn die Konfiguration kaputt ist. Also im
zweiten Terminal einen neuen Login versuchen. Wenn der klappt, ist es geschafft.
Wenn nicht, in der noch offenen Session zurückdrehen.

### 3.5 Firewall

Zwei Ebenen, beide nutzen:

**STRATO-Firewall** im Kunden-Login. Die liegt vor der VM, blockt also schon,
bevor Pakete meine Maschine überhaupt erreichen. Regeln: eingehend nur 22, 80,
443. Alles andere zu.

**ufw** auf der Maschine als zweite Schicht:

```bash
ufw default deny incoming
ufw default allow outgoing
ufw allow 22/tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw enable
```

Achtung, Fallstrick: Docker schreibt eigene iptables-Regeln und umgeht ufw
teilweise. Container niemals mit `-p 0.0.0.0:5432:5432` nach außen mappen,
sondern nur `-p 127.0.0.1:5432:5432`. Coolify macht das per Default richtig, das
gilt für alles, was ich daneben von Hand starte.

### 3.6 fail2ban

```bash
apt install fail2ban
```

Default-Konfiguration reicht für SSH. Mehr Kosmetik als Sicherheit, wenn
Passwort-Login ohnehin aus ist, aber es hält die Logs sauber.

---

## 04 Update-Automatik: der eigentliche Punkt

Das ist der Teil, wegen dem ich das Ganze überhaupt aufschreibe. Wenn diese drei
Dinge stehen, muss ich das Betriebssystem nie wieder anfassen.

### 4.1 unattended-upgrades

```bash
apt install unattended-upgrades
dpkg-reconfigure -plow unattended-upgrades
```

In `/etc/apt/apt.conf.d/50unattended-upgrades` explizit setzen:

```
Unattended-Upgrade::Automatic-Reboot "true";
Unattended-Upgrade::Automatic-Reboot-Time "04:00";
Unattended-Upgrade::Remove-Unused-Kernel-Packages "true";
Unattended-Upgrade::Mail "manne@...";
```

Konsequenz: Nachts um 4 kann der Server neu starten. Alle Container brauchen
deshalb `restart: unless-stopped` in der Compose-Datei, sonst kommen sie nicht
wieder hoch. Coolify setzt das selbst, für eigene Compose-Dateien selbst dran
denken.

### 4.2 needrestart

```bash
apt install needrestart
```

In `/etc/needrestart/needrestart.conf`: `$nrconf{restart} = 'a';`

Sorgt dafür, dass ein Dienst nach einem Bibliotheks-Update tatsächlich die neue
Bibliothek lädt. Ohne das läuft nach dem OpenSSL-Update weiter der alte Code im
Speicher, und ich glaube, ich sei gepatcht.

### 4.3 Ubuntu Pro: bitte einmal die Lizenzlage prüfen

Ubuntu Pro bringt zwei Dinge, die ich haben will: `esm-apps` (Security-Updates
auch für Pakete aus universe, nicht nur main) und **Livepatch** (Kernel-Patches
ohne Reboot).

Die Lizenzlage ist unklar und ich sollte sie nicht einfach annehmen. Canonical
hat 2022 kommuniziert, der Free Tier gelte für "personal and small-scale
commercial use" auf bis zu fünf Maschinen. Die aktuelle Seite ubuntu.com/pro
formuliert enger: "free for personal use on up to 5 physical machines". Der
bezahlte Server-Tarif liegt bei 500 US-Dollar pro Jahr und Maschine, das ist für
einen Showcase-Server offensichtlich absurd.

**Vorgehen:** Auf ubuntu.com/pro den aktuellen Wortlaut der Bedingungen lesen.
Wenn der Free Tier meine Nutzung deckt:

```bash
pro attach <TOKEN>
pro enable esm-apps
pro enable livepatch
```

Wenn nicht: verzichten. Dann fehlt mir Livepatch, und der Kernel wird erst beim
nächtlichen Reboot aus 4.1 aktiv. Für einen Ein-Server-Showcase ohne
Verfügbarkeitszusage ist das völlig in Ordnung. `esm-apps` fehlt dann auch, was
bedeutet: Pakete aus universe bekommen nur Community-Updates. Konsequenz für
mich: möglichst wenig aus universe installieren und stattdessen alles in
Container packen. Was ich ohnehin vorhabe.

---

## 05 Docker und Coolify

### 5.1 Docker

Offizielles Docker-Repository, nicht das Ubuntu-Paket:

```bash
curl -fsSL https://get.docker.com | sh
```

Kurz gegentesten, dass das Netzwerk-Mapping funktioniert (genau der Punkt, an dem
STRATO-V-Server historisch geklemmt haben):

```bash
docker run -d -p 8888:80 --name t nginx
curl -I http://localhost:8888
docker stop t && docker start t
curl -I http://localhost:8888   # muss nach dem Neustart weiter antworten
docker rm -f t
```

Der zweite `curl` ist der eigentliche Test. In der Virtuozzo-Zeit war ein
gestoppter und wieder gestarteter Container von außen nicht mehr erreichbar. Wenn
das hier scheitert: sofort stornieren und auf den Dedicated Server wechseln.

### 5.2 Coolify

```bash
curl -fsSL https://cdn.coollabs.io/coolify/install.sh | bash
```

Danach Coolify unter `http://<IP>:8000` aufrufen, Admin-Account anlegen,
**sofort** eine Domain für die Coolify-Instanz selbst hinterlegen (zum Beispiel
`deploy.mwolff.org`), damit die Oberfläche hinter TLS liegt. Anschließend Port
8000 in der STRATO-Firewall wieder schließen.

Was Coolify für mich übernimmt:

- Deployment aus GitLab oder GitHub, per Push oder per API
- Traefik als Reverse Proxy, Let's Encrypt automatisch pro Domain
- Docker-Compose-Deployments, also exakt das lokale Setup
- Postgres, Redis und Co. als Ein-Klick-Ressourcen mit Backup nach S3
- Projekte und Umgebungen sauber getrennt, jede mit eigener Domain

Der wichtige Punkt für den Workshop: Coolify hat eine API. Damit kann das
claude-workflow-kit nach dem Merge das Deployment auslösen. Die Kette wird
vollständig zeigbar: Issue, GO, Implementierung, Push, Merge, Deploy. Die drei
menschlichen Gates bleiben genau da, wo sie im Prozess stehen.

---

## 06 Domains und TLS

DNS-A-Record auf die Server-IP. Bei STRATO lassen sich A- und MX-Records direkt
im Kunden-Login setzen, das ist für die Domains, die dort schon liegen, der
kürzeste Weg.

Sinnvolle Struktur:

- `deploy.mwolff.org` für Coolify
- `demo.mwolff.org` für die öffentliche Demo-Instanz des Boards
- `board.mwolff.org` für meine eigene, produktive Instanz

Drei feste A-Records, drei normale HTTP-Challenges. Kein Wildcard-Zertifikat,
keine DNS-Challenge, kein Gefummel. Das ist der erste konkrete Gewinn aus der
Open-Source-Entscheidung: Ohne Kundeninstanzen als Subdomains fällt der
komplizierteste Teil des TLS-Setups ersatzlos weg.

---

## 07 Backup

Bei STRATO sind Backups im V-Server-Tarif **nicht enthalten**. Zwei Ebenen bauen:

**Ebene 1, Anwendungsdaten.** Coolify sichert seine Datenbank-Ressourcen nach
S3-kompatiblem Ziel. Als Ziel eignet sich STRATO HiDrive Objektspeicher, damit
alles bei einem Anbieter und in Deutschland bleibt.

**Ebene 2, Volumes und Config.** `restic` als Cronjob, verschlüsselt, auf ein
zweites Ziel:

```bash
restic -r s3:... backup /var/lib/docker/volumes /data --exclude-caches
restic -r s3:... forget --keep-daily 7 --keep-weekly 4 --keep-monthly 6 --prune
```

**Ebene 3, ganze Maschine.** Optional STRATO Cyber Protect dazubuchen. Für einen
Showcase, der aus Git reproduzierbar ist, würde ich mir das sparen. Der Server
muss aus dem Repository plus restic-Snapshot in unter zwei Stunden neu entstehen
können. Wenn das nicht geht, ist das Setup falsch.

**Quartalsweise: einen Restore auf einen VPS XS für 2 Euro durchspielen.** Ein
Backup, das nie zurückgespielt wurde, ist eine Vermutung. Das ist übrigens ein
gutes Workshop-Bild.

---

## 08 Der Teil, den unattended-upgrades nicht abdeckt

Hier ist die Stelle, an der Selbstbetreiber sich gerne selbst belügen: **Die
Container patcht niemand.** Ein gepatchtes Host-OS hilft nichts, wenn das Board
auf einem `node:22-alpine` von vor acht Monaten läuft.

Drei Bausteine, alle in meinem Repository, nicht auf dem Server:

**Renovate.** Bumpt Base-Images und Dependencies automatisch als Merge Request.
Damit wird aus einem Sicherheitsupdate ein normales Issue mit GO, Push und Merge.
Genau der Prozess, den ich im Workshop lehre.

**Trivy** als Scan-Schritt in der Pipeline, vor dem Merge-Gate:

```yaml
- trivy image --severity HIGH,CRITICAL --exit-code 1 $IMAGE
```

Ein CVE im Image blockt den Merge. Nicht verhandelbar, kein Ticket, kein "machen
wir später".

**Für Fremd-Images** (Postgres, Redis, was noch dazukommt): Coolify kann Stacks
nach Zeitplan aktualisieren. Alternativ Watchtower im Digest-Modus.

Das ist der beste Inhalt, den ich aus dem ganzen Setup für den Workshop ziehe:
Supply-Chain-Security ist kein Vortragsthema, sondern drei Zeilen
`renovate.json` und eine Zeile in der Pipeline.

---

## 09 Monitoring

STRATO bringt Monitoring Basic mit, das reicht für "Server antwortet nicht".

Dazu ein **Uptime Kuma** als Container, der die einzelnen Anwendungen prüft, also
`board.mwolff.org/health` und die Kundeninstanzen. Benachrichtigung per Mail oder
Signal. Fünf Minuten Aufwand, und ich erfahre von einem Ausfall vor dem Kunden.

Kein Prometheus, kein Grafana. Bei einem Server ist das Selbstbeschäftigung.

---

## 10 Migration vom Hostinger-Server

Der erste Wurf liegt ohnehin auf Hostinger. Das ist gut, weil ich damit die
Struktur ausprobiere, bevor ich sie in Deutschland festnagle. Was anders ist:

| | Hostinger VPS | STRATO V-Server |
|---|---|---|
| Virtualisierung | KVM | KVM |
| Root | ja | ja |
| Docker | ja, sogar als Ein-Klick-Template | ja laut Produktseite, FAQ widerspricht |
| Backups | wöchentlich plus Snapshot inklusive | nicht enthalten |
| Firewall | in hPanel | vorgelagert im Kunden-Login |
| Rechenzentrum | Niederlande, UK, Litauen und weitere | Deutschland |

Der entscheidende Unterschied für mich ist die letzte Zeile. Hostinger hat kein
deutsches Rechenzentrum. Für den Showcase, in dem ich über digitale Souveränität
und über Kunden rede, die ihre Issues nicht bei GitHub liegen haben wollen, ist
das ein Argumentationsloch. Für die Entwicklung ist es völlig egal.

Die Migration selbst ist unspektakulär, wenn ich von Anfang an sauber arbeite:

1. Anwendung liegt vollständig als Compose-Datei im Git-Repository
2. Daten liegen ausschließlich in benannten Volumes, nie im Container
3. Umzug: restic-Snapshot auf der alten Maschine, restore auf der neuen,
   Coolify-Projekt neu anlegen, DNS umbiegen, TTL vorher auf 300 senken

Wenn Schritt 1 und 2 stimmen, dauert der Umzug einen Abend. Wenn sie nicht
stimmen, dauert er ein Wochenende und ich lerne etwas.

**Konsequenz für jetzt:** Auf Hostinger nichts von Hand auf dem Server
konfigurieren, was nicht im Repository steht. Kein `apt install` von
Anwendungskomponenten, kein Editieren von Dateien per ssh. Alles, was die
Anwendung ausmacht, gehört ins Compose-File.

---

## 11 Betriebsrhythmus

Was nach dem Setup an Arbeit bleibt:

**Täglich:** nichts. Die Demo setzt sich um 03:00 selbst zurück und zieht dabei
das aktuelle Image.

**Wöchentlich:** Die Renovate-Merge-Requests durchgehen. Zehn Minuten. Das ist
Entwicklungsarbeit, kein Betrieb. Dazu, seit das Repository öffentlich ist, ein
Blick in die fremden Issues. Nicht beantworten müssen, nur lesen.

**Monatlich:** In die Mail von unattended-upgrades schauen. Prüfen, ob der Server
nachts sauber durchgestartet ist. Einmal auf `demo.mwolff.org` klicken und sehen,
ob der Seed noch das zeigt, was er zeigen soll.

**Quartalsweise:** Restore-Test (siehe 07). Ein Blick auf `docker system df` und
ein `docker system prune`, damit die 240 GB nicht durch alte Layer volllaufen.
Der nächtliche `compose pull` sammelt sonst über Monate alte Images an.

**Jährlich:** Kurz prüfen, ob der Tarif noch passt.

---

## 12 Kosten

| Posten | Monat |
|---|---|
| STRATO VPS L | 16 Euro |
| HiDrive Objektspeicher als Backup-Ziel | ca. 3 bis 5 Euro |
| Coolify | 0 Euro |
| Ubuntu Pro | 0 Euro, sofern der Free Tier greift |
| Einrichtungsgebühr | 9 Euro einmalig |

Grob **20 Euro im Monat**. Zum Vergleich: Der Mittwald vServer mit Managed
Container Hosting beginnt bei 35 Euro netto und nimmt mir das Betriebssystem ab.
Er nimmt mir aber nicht die Container-Pflege ab, und das ist der Teil, der
wirklich Arbeit macht.

---

## 13 Die öffentliche Demo-Instanz

Seit das Board Open Source ist, hostet dieser Server keine Kunden mehr, sondern
eine Demo. Das ändert die Anforderungen komplett: Die Demo darf kaputtgehen, sie
darf beschrieben werden, sie muss sich nur selbst aufräumen.

### 13.1 Reset per Cronjob

Jede Nacht um 03:00, also eine Stunde vor dem möglichen Reboot aus 4.1, wird die
Demo-Datenbank auf einen Seed-Stand zurückgesetzt:

```bash
#!/usr/bin/env bash
set -euo pipefail

cd /opt/demo

docker compose down -v            # Volumes weg, Daten weg
docker compose pull               # neuestes Image aus der Registry
docker compose up -d
sleep 20
docker compose exec -T app ./bin/seed-demo   # Beispielprojekt, Beispiel-Issues
```

Der Seed ist Teil des Repositories, nicht des Servers. Er legt ein Projekt an,
das den claude-workflow-kit-Prozess abbildet: ein paar Issues in verschiedenen
Zuständen, einer davon vor dem GO-Gate, einer zwischen Push und Merge. Wer auf
`demo.mwolff.org` landet, sieht sofort, worum es geht.

`docker compose pull` ist der zweite Gewinn: Die Demo zieht bei jedem Reset das
aktuelle Image. Ein Merge auf main ist damit spätestens am nächsten Morgen
sichtbar, ohne dass ich irgendetwas tue.

### 13.2 Login

Kein Registrierungsformular. Ein fester Demo-Account, Zugangsdaten stehen sichtbar
auf der Login-Seite. Wer sich anmelden kann, kann alles kaputtmachen, und genau
das ist erlaubt. Um 03:00 ist es wieder heil.

### 13.3 Absicherung

Die Demo ist ein öffentlich beschreibbares System. Drei Dinge:

- **Ausgehende Verbindungen aus dem Demo-Container blocken**, soweit die
  Anwendung sie nicht braucht. Sonst wird die Demo zum Sprungbrett.
- **Ressourcenlimits** in der Compose-Datei (`mem_limit`, `cpus`), damit ein
  Übermütiger nicht den ganzen Server in die Knie zwingt.
- **Getrenntes Docker-Netzwerk.** Die Demo darf `board.mwolff.org` und Coolify
  nicht sehen.

### 13.4 Was damit erledigt ist

Kein AVV. Keine TOMs. Kein Löschkonzept. Keine Auftragsverarbeitung. Die
Demo-Instanz enthält per Konstruktion keine personenbezogenen Daten Dritter, weil
sie jede Nacht gelöscht wird und weil auf der Startseite steht, dass sie es wird.

Ein Satz in der Fußzeile: "Öffentliche Demo-Instanz. Alle Daten werden täglich um
03:00 Uhr gelöscht. Bitte keine echten Daten eingeben."

---

## 14 Der Haken, der bleibt

**Kein Failover.** Ein Server ist ein Server. Wenn STRATO die Maschine migriert
oder ich mir ein Volume zerschieße, ist die Demo offline. Bei einer Demo ist das
ein Schulterzucken, kein Vorfall. Genau deshalb ist die Open-Source-Entscheidung
auch eine Betriebsentscheidung: Sie senkt die Verfügbarkeitsanforderung von
"Kunde arbeitet damit" auf "jemand klickt sich das mal an".

Meine eigene Instanz auf `board.mwolff.org` liegt auf derselben Maschine und hat
damit dieselbe Verfügbarkeit. Da ich der einzige Nutzer bin, ist mir das recht.
Das restic-Backup aus 07 ist die Versicherung, nicht die Redundanz.

---

## 15 Was ich im Workshop daraus mache

Der Server ist nicht Infrastruktur, sondern Lehrmaterial. Vier Geschichten
stecken drin:

**01 Der Prozess trägt bis in die Produktion.** Issue, GO, Implementierung, Push,
Merge, Deploy. Die drei menschlichen Gates bleiben. Das Deployment ist der
Beweis, dass der Prozess nicht bei "Code ist geschrieben" endet.

**02 Sicherheit ist ein Prozessschritt, kein Kapitel.** Renovate erzeugt ein
Issue. Trivy blockt den Merge. Beides passiert im selben Ablauf wie jedes
Feature. Wer Security als separaten Schritt plant, plant sie weg.

**03 Betrieb ist eine Entwurfsentscheidung.** Ob ich einen Server manuell pflege
oder ob er sich selbst pflegt, entscheidet sich nicht im Betrieb, sondern beim
Aufsetzen. Der Unterschied sind vier Konfigurationsdateien und eine Stunde.

**04 Die Lizenz ist eine Architekturentscheidung.** Weil das Board Open Source
ist, hostet der Kunde selbst. Weil der Kunde selbst hostet, brauche ich keinen
AVV, kein Wildcard-Zertifikat, keine Mandantenfähigkeit und keine
Verfügbarkeitszusage. Eine einzige Entscheidung außerhalb des Codes hat drei
technische Probleme gelöst. Das ist der Punkt, den ich Teilnehmern beibringen
will: Die schwierigsten technischen Probleme löst man oft, indem man sie nicht
hat.

---

## Anhang: Checkliste zum Abhaken

- [ ] STRATO-Support: schriftliche Bestätigung Docker auf V-Server Linux (KVM)
- [ ] Lokalen Public Key bereitgelegt, OpenSSH-Einzeilerformat geprüft
- [ ] VPS L bestellt, Standort Deutschland, Ubuntu 24.04 LTS
- [ ] SSH-Key im Installationsdialog eingetragen, Root-Passwort im Passwortmanager
- [ ] `ssh root@IP` funktioniert ohne Passwort
- [ ] Nutzer `manne` angelegt, Key übernommen, `ssh manne@IP` und `sudo -v` getestet
- [ ] Root-Login und Passwort-Login aus, `sshd_config.d/` gegengeprüft
- [ ] STRATO-Firewall: nur 22, 80, 443
- [ ] ufw aktiv
- [ ] fail2ban aktiv
- [ ] unattended-upgrades inkl. Auto-Reboot 04:00 und Mail-Benachrichtigung
- [ ] needrestart auf automatisch
- [ ] Ubuntu Pro geprüft, ggf. attached
- [ ] Docker installiert, Stop-Start-Netzwerktest bestanden
- [ ] Coolify installiert, hinter Domain und TLS, Port 8000 geschlossen
- [ ] DNS-Records für deploy, demo und board gesetzt
- [ ] restic-Cronjob läuft, erster Restore-Test bestanden
- [ ] Renovate im Repository aktiv
- [ ] Trivy im Merge-Gate
- [ ] Uptime Kuma überwacht die Anwendungen
- [ ] Demo-Reset-Cronjob läuft, Seed erzeugt ein sinnvolles Beispielprojekt
- [ ] Demo-Container: Ressourcenlimits, eigenes Netzwerk, Egress eingeschränkt
- [ ] Hinweis auf den nächtlichen Reset steht auf der Demo-Startseite
