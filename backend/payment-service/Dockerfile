FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY backend/payment-service/target/*.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
