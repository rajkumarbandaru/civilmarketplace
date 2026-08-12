FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY review-service/target/*.jar app.jar
EXPOSE 8089
ENTRYPOINT ["java", "-jar", "app.jar"]
