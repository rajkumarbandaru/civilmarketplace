FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY backend/config-server/target/*.jar app.jar
EXPOSE 8888
ENTRYPOINT ["java", "-jar", "app.jar"]
