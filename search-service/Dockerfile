FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY search-service/target/*.jar app.jar
EXPOSE 8092
ENTRYPOINT ["java", "-jar", "app.jar"]
