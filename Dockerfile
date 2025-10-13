# Dockerfile
# Projeyi derlemek için bir JDK imajı kullan
FROM maven:3.8.5-openjdk-17-slim AS build
COPY . /app
WORKDIR /app
# Maven ile projeyi derle ve JAR dosyasını oluştur
RUN mvn clean package -DskipTests

# Çalıştırma aşaması için sadece JRE içeren daha küçük bir imaj kullan
FROM openjdk:17-jre-slim
# Render'da oluşan JAR dosyasını kopyala
COPY --from=build /app/target/*.jar app.jar
# Uygulamanın çalışacağı portu belirt (Spring Boot varsayılanı 8080)
EXPOSE 8080
# Uygulamayı başlat
ENTRYPOINT ["java", "-jar", "/app.jar"]