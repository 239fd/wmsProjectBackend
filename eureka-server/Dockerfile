FROM gradle:8-jdk21-alpine AS builder
WORKDIR /app
COPY settings.gradle build.gradle ./
COPY src src
RUN gradle clean bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8761
ENTRYPOINT ["java", "-jar", "app.jar"]

