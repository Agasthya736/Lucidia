# Build stage — compile the Spring Boot fat JAR
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Copy pom first so Maven dependency layer is cached
COPY pom.xml .
RUN mvn -q dependency:go-offline -B

COPY src ./src
RUN mvn -q -DskipTests package -B

# Runtime stage — minimal JRE image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Non-root user for security + data directory (must be created as root first)
RUN addgroup -S lucidia && adduser -S lucidia -G lucidia \
    && mkdir -p /data/images \
    && chown -R lucidia:lucidia /data

USER lucidia

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java -Duser.timezone=Asia/Kolkata -Dserver.port=${PORT:-8080} -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -jar app.jar"]
