FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY apps/api/pom.xml apps/api/pom.xml
COPY apps/notification-worker/pom.xml apps/notification-worker/pom.xml
COPY apps apps
RUN mvn -B -pl apps/notification-worker -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/apps/notification-worker/target/notification-worker-*.jar app.jar
USER app
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
