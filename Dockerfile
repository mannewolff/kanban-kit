# Multi-Stage-Build: Node (Frontend) -> Maven (Backend-Jar inkl. Frontend) -> JRE (Runtime)

# 1) Frontend bauen
FROM node:22-alpine AS frontend
WORKDIR /build/frontend
COPY frontend/package.json ./
RUN npm install
COPY frontend/ ./
RUN npm run build

# 2) Backend-Jar bauen (Frontend-Plugin übersprungen, dist wird hineinkopiert)
FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /build
COPY pom.xml ./
COPY src ./src
COPY --from=frontend /build/frontend/dist ./src/main/resources/static
RUN mvn -q -B -DskipTests -Dskip.frontend=true package

# 3) Schlanke Runtime
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
RUN groupadd --system manban && useradd --system --gid manban manban
COPY --from=backend /build/target/manban.jar app.jar
USER manban
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
