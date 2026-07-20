FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /workspace

# Resolve dependencies in a separate layer so source changes remain cheap.
COPY pom.xml ./
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN groupadd --system --gid 10001 orbit \
    && useradd --system --uid 10001 --gid orbit --home-dir /app orbit

COPY --from=build --chown=orbit:orbit /workspace/target/backend-*.jar /app/orbit.jar

USER 10001:10001
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/orbit.jar"]
