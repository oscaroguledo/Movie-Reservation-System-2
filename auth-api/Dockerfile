# Build context is the repo root (a multi-module Gradle build needs the
# root settings.gradle/build.gradle and every included module's sources),
# so this is referenced from docker-compose.yml as:
#   build: { context: ., dockerfile: auth-api/Dockerfile }

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew build.gradle settings.gradle ./
COPY gradle gradle
COPY auth-api/build.gradle auth-api/build.gradle
COPY movie-api/build.gradle movie-api/build.gradle
COPY movie-api/src movie-api/src
COPY auth-api/src auth-api/src

RUN ./gradlew :auth-api:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/auth-api/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
