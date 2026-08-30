FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY apps/api/pom.xml apps/api/pom.xml
COPY apps/notification-worker/pom.xml apps/notification-worker/pom.xml
COPY apps apps
RUN mvn -B -pl apps/api -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/apps/api/target/ticketing-api-*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
