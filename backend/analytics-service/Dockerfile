FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY backend/analytics-service/target/*.jar app.jar
EXPOSE 8102
ENTRYPOINT ["java", "-jar", "app.jar"]
