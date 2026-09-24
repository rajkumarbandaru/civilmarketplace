FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY backend/procurement-service/target/*.jar app.jar
EXPOSE 8101
ENTRYPOINT ["java", "-jar", "app.jar"]
