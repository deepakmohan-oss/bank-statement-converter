# ─────────────────────────────────────────────────────────────────
# Stage 1: Build React frontend
# ─────────────────────────────────────────────────────────────────
FROM node:20-alpine AS frontend-build

WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build


# ─────────────────────────────────────────────────────────────────
# Stage 2: Build Kotlin/Ktor backend fat JAR
# ─────────────────────────────────────────────────────────────────
FROM gradle:8-jdk21 AS backend-build

WORKDIR /app

COPY backend/gradle/             gradle/
COPY backend/gradlew             gradlew
COPY backend/build.gradle.kts    build.gradle.kts
COPY backend/settings.gradle.kts settings.gradle.kts

# Fix execute permission (lost when extracted from zip)
RUN chmod +x gradlew

# Pre-download dependencies (cached unless build files change)
RUN ./gradlew dependencies --no-daemon --quiet 2>/dev/null || true

COPY backend/src/ src/
COPY --from=frontend-build /frontend/dist/ src/main/resources/static/

RUN ./gradlew buildFatJar --no-daemon


# ─────────────────────────────────────────────────────────────────
# Stage 3: Minimal runtime image
# ─────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache \
    tesseract-ocr \
    tesseract-ocr-data-eng \
    poppler-utils \
    && rm -rf /var/cache/apk/*

WORKDIR /app
COPY --from=backend-build /app/build/libs/app.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-Xmx512m", "-XX:+UseContainerSupport", "-jar", "app.jar"]
