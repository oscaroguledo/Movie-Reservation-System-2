# Build context is the repo root — same reasoning as auth-api/Dockerfile
# (a multi-module Gradle build needs the root settings.gradle/build.gradle
# and every included module's sources), referenced from docker-compose.yml as:
#   build: { context: ., dockerfile: movie-api/Dockerfile }

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew build.gradle settings.gradle ./
COPY gradle gradle
COPY movie-api/build.gradle movie-api/build.gradle
COPY auth-api/build.gradle auth-api/build.gradle
COPY auth-api/src auth-api/src
COPY movie-api/src movie-api/src

RUN ./gradlew :movie-api:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/movie-api/build/libs/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
