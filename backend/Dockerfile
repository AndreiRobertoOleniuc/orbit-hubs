# The published maven:* images ship known-vulnerable OS packages, so the build
# stage runs on a plain Temurin JDK and uses the repo's Maven wrapper instead.
FROM eclipse-temurin:21-jdk-noble AS build

WORKDIR /workspace

# Resolve dependencies in a separate layer so source changes remain cheap.
COPY .mvn ./.mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -DskipTests dependency:go-offline

COPY src ./src
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre-noble

WORKDIR /app

# Pull in the current Ubuntu security patches; the base image lags behind them.
RUN apt-get update \
    && apt-get upgrade -y --no-install-recommends \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system --gid 10001 orbit \
    && useradd --system --uid 10001 --gid orbit --home-dir /app orbit

COPY --from=build --chown=orbit:orbit /workspace/target/backend-*.jar /app/orbit.jar

USER 10001:10001
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/orbit.jar"]
