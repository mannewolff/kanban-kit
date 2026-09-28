# Multi-Stage-Build: Node (Frontend) -> Maven (Backend-Jar inkl. Frontend) -> JRE (Runtime)

# 1) Frontend + VitePress-Doku bauen
#
# --platform=$BUILDPLATFORM an den beiden Bau-Stufen (hier und `backend`): Frontend-Bundle und Jar
# sind architekturunabhaengig. Ohne diese Angabe liefen npm- und Maven-Lauf beim arm64-Abbild aus
# .github/workflows/release-images.yml vollstaendig unter QEMU-Emulation — Minuten statt Sekunden,
# fuer ein Ergebnis, das ohnehin dasselbe ist. Die Laufzeit-Stufe `runtime` traegt den Schalter
# bewusst NICHT: Sie ist das ausgelieferte Abbild und muss je Zielarchitektur entstehen.
FROM --platform=$BUILDPLATFORM node:22.23.3-alpine AS frontend
WORKDIR /build/frontend
COPY frontend/package.json ./
RUN npm install
COPY frontend/ ./
RUN npm run build

# VitePress-Doku (docs-site) bauen; copy-docs.mjs liest die Markdown-Quellen aus ../docs.
WORKDIR /build/docs-site
COPY docs-site/package.json ./
RUN npm install
COPY docs-site/ ./
COPY docs /build/docs
RUN npm run build

# 2) Backend-Jar bauen (Frontend-Plugin übersprungen, dist wird hineinkopiert)
FROM --platform=$BUILDPLATFORM maven:3.9.16-eclipse-temurin-25 AS backend
WORKDIR /build
COPY pom.xml ./
COPY src ./src
COPY --from=frontend /build/frontend/dist ./src/main/resources/static
COPY --from=frontend /build/docs-site/.vitepress/dist ./src/main/resources/static/docs
RUN mvn -q -B -DskipTests -Dskip.frontend=true package

# 3) Schlanke Runtime
FROM eclipse-temurin:25.0.4.1_1-jre AS runtime
WORKDIR /app
RUN groupadd --system manban && useradd --system --gid manban manban
COPY --from=backend /build/target/manban.jar app.jar
USER manban
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
