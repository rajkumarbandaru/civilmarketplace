FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY messaging-service/target/*.jar app.jar
EXPOSE 8096
ENTRYPOINT ["java", "-jar", "app.jar"]
