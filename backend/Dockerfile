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

# Non-root user for security
RUN addgroup -S lucidia && adduser -S lucidia -G lucidia
USER lucidia

# Directory for local scan image storage (bind-mounted or Cloud Storage FUSE in prod)
RUN mkdir -p /data/images

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
  "-Duser.timezone=Asia/Kolkata", \
  "-Dserver.port=8080", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
