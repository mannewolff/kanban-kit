# Multi-Stage-Build: Node (Frontend) -> Maven (Backend-Jar inkl. Frontend) -> JRE (Runtime)

# 1) Frontend + VitePress-Doku bauen
FROM node:22-alpine AS frontend
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
FROM maven:3.9-eclipse-temurin-25 AS backend
WORKDIR /build
COPY pom.xml ./
COPY src ./src
COPY --from=frontend /build/frontend/dist ./src/main/resources/static
COPY --from=frontend /build/docs-site/.vitepress/dist ./src/main/resources/static/docs
RUN mvn -q -B -DskipTests -Dskip.frontend=true package

# 3) Schlanke Runtime
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
RUN groupadd --system manban && useradd --system --gid manban manban
COPY --from=backend /build/target/manban.jar app.jar
USER manban
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
