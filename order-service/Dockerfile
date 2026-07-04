FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml ./pom.xml
COPY inventory-service/pom.xml ./inventory-service/pom.xml
COPY order-service/pom.xml ./order-service/pom.xml
COPY api-gateway/pom.xml ./api-gateway/pom.xml
COPY order-service/src ./order-service/src
RUN mvn clean package -DskipTests -pl order-service

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/order-service/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
