# ─────────────────────────────────────────────────────────────────
# Stage 1: Build React frontend
# ─────────────────────────────────────────────────────────────────
FROM node:20-alpine AS frontend-build

WORKDIR /frontend
COPY frontend/package*.json ./
RUN npm ci --silent

COPY frontend/ ./
RUN npm run build
# Output goes to frontend/dist (vite default)


# ─────────────────────────────────────────────────────────────────
# Stage 2: Build Kotlin/Ktor backend fat JAR
# ─────────────────────────────────────────────────────────────────
FROM gradle:8-jdk21 AS backend-build

WORKDIR /app

# Copy Gradle wrapper and build files first (layer cache)
COPY backend/gradle/      gradle/
COPY backend/gradlew      gradlew
COPY backend/build.gradle.kts    build.gradle.kts
COPY backend/settings.gradle.kts settings.gradle.kts

# Download dependencies only (cache this layer)
RUN ./gradlew dependencies --no-daemon 2>/dev/null || true

# Copy source + frontend static files
COPY backend/src/ src/

# Copy frontend build output into Ktor resources
COPY --from=frontend-build /frontend/dist/ src/main/resources/static/

# Build fat JAR
RUN ./gradlew buildFatJar --no-daemon


# ─────────────────────────────────────────────────────────────────
# Stage 3: Minimal runtime image
# ─────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

# Install OCR dependencies (Tesseract + Poppler for PDF rasterisation)
RUN apk add --no-cache \
    tesseract-ocr \
    tesseract-ocr-data-eng \
    poppler-utils \
    && rm -rf /var/cache/apk/*

WORKDIR /app
COPY --from=backend-build /app/build/libs/*-all.jar app.jar

# Railway sets PORT automatically
EXPOSE 8080
ENTRYPOINT ["java", \
  "-Xmx512m", \
  "-XX:+UseContainerSupport", \
  "-jar", "app.jar"]
