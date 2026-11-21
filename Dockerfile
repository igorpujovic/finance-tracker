# Dependencies are resolved in their own layer so that a source-only change
# does not re-download the world.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# The client's dependencies get a layer of their own, before its sources, for
# the same reason the Java ones do: editing a component should not re-run
# `npm ci`. The Maven build drives the client (ADR-0008) and downloads its own
# Node, so there is nothing to install in this image for it.
COPY frontend/package.json frontend/package-lock.json ./frontend/
RUN mvn -B -q frontend:install-node-and-npm frontend:npm@npm-ci

COPY frontend ./frontend
COPY src ./src
# `clean` is deliberately absent: there is nothing to clean in a fresh image,
# and it would delete the Node install the layer above just made.
#
# Java tests are skipped here because the `build` CI job already ran them, and
# running them in this image would need a Docker daemon inside the build
# container for Testcontainers. The client's tests are skipped for the same
# reason — that job runs those too.
RUN mvn -B -q package -DskipTests -Dfrontend.test.skip=true

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
