# ============================================================
# Stage 1: Build
# ============================================================
FROM gradle:9.1.0-jdk17 AS build

WORKDIR /app

COPY . .

# Use the Gradle installed in the build image so the wrapper does not download
# a separate Gradle distribution during the image build.
RUN gradle clean bootJar --no-daemon


# ============================================================
# Stage 2: Runtime
# ============================================================
FROM eclipse-temurin:17-jre

WORKDIR /app

# Run as non-root user
RUN useradd --system --create-home --shell /usr/sbin/nologin appuser

COPY --from=build /app/build/libs/*.jar app.jar

RUN chown appuser:appuser app.jar

USER appuser

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-jar", \
    "app.jar"]
