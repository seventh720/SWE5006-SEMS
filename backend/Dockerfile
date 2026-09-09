FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 sems
COPY --from=build /workspace/target/sems-backend-*.jar app.jar
USER sems
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
