# ============================================================
# Stage 1: Build
# ============================================================
FROM gradle:8-jdk17 AS build

WORKDIR /app

COPY . .

RUN ls -la
RUN test -f ./gradlew
RUN chmod +x ./gradlew

RUN ./gradlew clean bootJar --no-daemon


# ============================================================
# Stage 2: Runtime
# ============================================================
FROM eclipse-temurin:17-jre

WORKDIR /app

RUN useradd --system --create-home --shell /usr/sbin/nologin appuser

COPY --from=build /app/build/libs/*.jar app.jar

RUN chown appuser:appuser app.jar

USER appuser


ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-jar", \
    "app.jar"]