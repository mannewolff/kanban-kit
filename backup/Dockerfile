# Sicherungs-Container (Issue #829, Plan #825).
#
# Die Sicherung laeuft ausserhalb der Anwendung (E1): Sie muss funktionieren, wenn die Anwendung
# nicht startet — genau dann braucht man sie —, und eine Sicherung auf einen Zeitpunkt braucht den
# Datenbankserver auf Protokollebene, nicht den Zugriff ueber JPA.
#
# Alle Versionen sind fest getaggt, nirgends `latest`. Eine Sicherung, deren Werkzeuge sich unter
# der Hand aendern, ist keine: `age` schreibt das Format, aus dem spaeter zurueckgeholt werden muss.
# Die drei ARG-Zeilen unten sind der einzige Ort, an dem eine Version angehoben wird.
#
# Warum die Basis `postgres:16` ist: Der Sicherungsclient muss zur Hauptversion des Servers aus
# docker-compose.yml passen — `pg_basebackup` einer anderen Hauptversion kann den Datenbestand
# nicht lesen. Beides zusammen zu heben, ist damit eine einzige, sichtbare Entscheidung.
#
# Der Anhang-Spiegel lief bis Issue #1228 ueber `mc`, kopiert aus dem MinIO-Abbild des Stacks.
# Beides ist entfallen: Das Abbild auf quay.io ist anonym nicht mehr beziehbar — dieser Bau lief
# damit gar nicht mehr durch —, und mit dem Wechsel des Objektspeichers (Issue #1226) gibt es auch
# kein MinIO mehr, zu dem der Client passen muesste. An seine Stelle tritt `rclone`, das ohnehin
# schon hier liegt und den Weg ausser Haus faehrt: `mc mirror` ohne --remove und `rclone copy`
# haben dieselbe Semantik, der Spiegel loescht weiterhin nie (E5 aus Plan #825). Damit faellt eine
# Bezugsquelle und ein Werkzeug weg.

ARG AGE_VERSION=1.2.1
ARG RCLONE_VERSION=1.68.2

FROM debian:bookworm-slim AS werkzeuge
ARG AGE_VERSION
ARG RCLONE_VERSION
# TARGETARCH setzt BuildKit selbst (amd64 auf dem Server, arm64 auf einem Apple-Rechner). Fest
# verdrahtetes `amd64` liesse das Abbild auf dem Entwicklerrechner gar nicht erst entstehen.
ARG TARGETARCH
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl unzip \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /bau
RUN curl -fsSL -o age.tar.gz \
        "https://github.com/FiloSottile/age/releases/download/v${AGE_VERSION}/age-v${AGE_VERSION}-linux-${TARGETARCH}.tar.gz" \
    && tar -xzf age.tar.gz \
    && install -m 0755 age/age age/age-keygen /usr/local/bin/
RUN curl -fsSL -o rclone.zip \
        "https://downloads.rclone.org/v${RCLONE_VERSION}/rclone-v${RCLONE_VERSION}-linux-${TARGETARCH}.zip" \
    && unzip -q rclone.zip \
    && install -m 0755 "rclone-v${RCLONE_VERSION}-linux-${TARGETARCH}/rclone" /usr/local/bin/rclone

FROM postgres:16-bookworm

# `cron` faehrt den Takt der Basissicherung aus MANBAN_BACKUP_BASE_CRON; `util-linux` bringt
# `flock` mit, das zwei gleichzeitige Laeufe derselben Art verhindert.
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates cron util-linux \
    && rm -rf /var/lib/apt/lists/*

COPY --from=werkzeuge /usr/local/bin/age /usr/local/bin/age
COPY --from=werkzeuge /usr/local/bin/age-keygen /usr/local/bin/age-keygen
COPY --from=werkzeuge /usr/local/bin/rclone /usr/local/bin/rclone
# Alle drei Skripte in dasselbe Verzeichnis: restore.sh und retention.sh laden backup.sh von dort
# nach (gemeinsame Begriffe — Pfade, Protokollzeile, Ziel ausser Haus).
#
# `age-keygen` liegt im Abbild, `age --decrypt` ist damit technisch moeglich — aber es gibt keinen
# privaten Schluessel, mit dem es etwas zu entschluesseln gaebe (E3, AK8). Erst restore.sh
# --quelle aussenhaus bekommt einen hereingereicht, fuer genau diesen einen Aufruf.
COPY backup.sh restore.sh retention.sh /usr/local/bin/
RUN chmod 0755 /usr/local/bin/backup.sh /usr/local/bin/restore.sh /usr/local/bin/retention.sh \
    && mkdir -p /wal-archiv /sicherungen /wiederherstellung/pgdata

# Das Verzeichnis `backup/` selbst haengt read-only unter /etc/manban-backup (siehe
# docker-compose.backup.yml). Dort sucht rclone seine Konfiguration: So gibt es keinen
# Einzeldatei-Mount, der bei fehlender Datei stillschweigend zu einem Verzeichnis wird.
# Das Remote `speicher` — der Objektspeicher — steht bewusst NICHT in dieser Datei, sondern kommt
# als RCLONE_CONFIG_SPEICHER_*-Umgebung aus docker-compose.backup.yml: Die eingehaengte Datei
# gehoert dem Betreiber und beschreibt sein Ziel ausser Haus.
ENV RCLONE_CONFIG=/etc/manban-backup/rclone.conf

ENTRYPOINT ["/usr/local/bin/backup.sh"]
CMD ["dienst"]
