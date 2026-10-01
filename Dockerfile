# Multi-Stage-Build: Node (Frontend) -> Maven (Backend-Jar inkl. Frontend) -> JRE (Runtime)

# 1) Frontend + VitePress-Doku bauen
#
# --platform=$BUILDPLATFORM an den beiden Bau-Stufen (hier und `backend`): Frontend-Bundle und Jar
# sind architekturunabhaengig. Ohne diese Angabe liefen npm- und Maven-Lauf beim arm64-Abbild aus
# .github/workflows/release-images.yml vollstaendig unter QEMU-Emulation — Minuten statt Sekunden,
# fuer ein Ergebnis, das ohnehin dasselbe ist. Die Laufzeit-Stufe `runtime` traegt den Schalter
# bewusst NICHT: Sie ist das ausgelieferte Abbild und muss je Zielarchitektur entstehen.
FROM --platform=$BUILDPLATFORM node:22.23.3-alpine@sha256:0a7108bf6c7bf5de370ffb1a3ed6be93d405b43ff159f681a8d18c0e2bc2e402 AS frontend
WORKDIR /build/frontend
# Mit Sperrdatei und `npm ci`: Das Abbild baut genau die Fassungen, die im Repository stehen (#1331).
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# VitePress-Doku (docs-site) bauen; copy-docs.mjs liest die Markdown-Quellen aus ../docs und ../UPGRADING.md.
WORKDIR /build/docs-site
COPY docs-site/package.json docs-site/package-lock.json ./
RUN npm ci
COPY docs-site/ ./
COPY docs /build/docs
COPY UPGRADING.md /build/UPGRADING.md
RUN npm run build

# 2) Backend-Jar bauen (Frontend-Plugin übersprungen, dist wird hineinkopiert)
FROM --platform=$BUILDPLATFORM maven:3.9.16-eclipse-temurin-25@sha256:93b8a14ea2f412782e4e842651273b4d903e35cc496284f178fbbe2d67d00976 AS backend
WORKDIR /build
COPY pom.xml ./
COPY src ./src
COPY --from=frontend /build/frontend/dist ./src/main/resources/static
COPY --from=frontend /build/docs-site/.vitepress/dist ./src/main/resources/static/docs
RUN mvn -q -B -DskipTests -Dskip.frontend=true package

# 3) Schlanke Runtime
FROM eclipse-temurin:25.0.4.1_1-jre@sha256:8da0490fa9a3c26867012019565948eef0ee69438f5c75ac28146967bae984b5 AS runtime
WORKDIR /app
RUN groupadd --system manban && useradd --system --gid manban manban
COPY --from=backend /build/target/manban.jar app.jar
USER manban
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
