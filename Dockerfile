# Dockerfile

# Stage 1: Build (Projeyi Derleme Aşaması)
FROM maven:3.9.5-eclipse-temurin-21 AS build

WORKDIR /app

# 1. Bağımlılıkları Önbellekleme Katmanı
# Sadece pom.xml dosyasını kopyala. Bu katman, sadece pom.xml değiştiğinde yeniden çalışır.
COPY pom.xml .

# Bağımlılıkları indir, ancak bir şey derleme. (Projenizdeki ilk build'den çok daha hızlıdır.)
RUN mvn dependency:go-offline -B

# 2. Kod Değişiklikleri Katmanı
# Kalan tüm proje dosyalarını kopyala (bu katman, kod her değiştiğinde yeniden çalışır.)
COPY src /app/src

# Kodu derle
RUN mvn clean package -DskipTests

# Stage 2: Run (Uygulamayı Çalıştırma Aşaması)
FROM eclipse-temurin:21-jre-alpine

# Render'da oluşan JAR dosyasını kopyala
COPY --from=build /app/target/*.jar app.jar

# Uygulamanın çalışacağı portu belirt
EXPOSE 8080

# Uygulamayı başlat
ENTRYPOINT ["java", "-jar", "/app.jar"]