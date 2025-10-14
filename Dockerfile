# Stage 1: Build
FROM maven:3.9.5-eclipse-temurin-17 AS build

# UTF-8 ortam değişkenleri
ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8
ENV LANGUAGE=en_US:en

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src /app/src
RUN mvn clean package -DskipTests -Dproject.build.sourceEncoding=UTF-8

# Stage 2: Run
FROM eclipse-temurin:17-jre-alpine

ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8
ENV LANGUAGE=en_US:en

COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
