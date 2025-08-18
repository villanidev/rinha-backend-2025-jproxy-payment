# Maven build stage
FROM maven:3.9.9-eclipse-temurin-21-alpine as build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src/ ./src/
RUN mvn clean package -DskipTests=true

# App package stage
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/target/jproxy-payment-1.0.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "-XX:+UseZGC", "-Xms128m", "-Xmx128m", "-XX:MaxRAMPercentage=80", "-XX:+UseStringDeduplication", "-XX:MaxGCPauseMillis=5", "-XX:ThreadStackSize=128k", "/app/app.jar"]