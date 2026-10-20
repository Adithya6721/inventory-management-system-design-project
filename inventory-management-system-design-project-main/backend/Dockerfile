# Build stage
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# Run stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
VOLUME /tmp
COPY --from=builder /app/target/inventory-backend-1.0.0-SNAPSHOT.jar app.jar
ENV PORT=8080
ENV MONGO_HOST=mongodb
ENV MONGO_PORT=27017
ENV MONGO_DB=inventory_db

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
