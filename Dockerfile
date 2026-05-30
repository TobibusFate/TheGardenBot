FROM eclipse-temurin:21-jdk-alpine as builder

WORKDIR /app

COPY build.gradle.kts settings.gradle.kts gradlew .
COPY gradle/ gradle/
RUN chmod 777 gradlew
RUN ./gradlew dependencies --no-daemon -q

COPY src ./src
RUN ./gradlew jar --no-daemon -q



FROM eclipse-temurin:21-jre-alpine 

RUN apk add --no-cache ffmpeg
RUN apk add --no-cache gallery-dl
RUN apk add --no-cache yt-dlp

WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8086

RUN mkdir -p /app/downloads && chown -R 1000:1000 /app
USER 1000:1000

ENTRYPOINT ["java", "-jar",  "app.jar"]
