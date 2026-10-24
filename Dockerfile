# Build stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Download dependencies first to cache them between builds
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --no-create-home app
USER app

COPY --from=build /app/target/south-america-guide-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
