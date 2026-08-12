FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY backend/support-service/target/*.jar app.jar
EXPOSE 8098
ENTRYPOINT ["java", "-jar", "app.jar"]
