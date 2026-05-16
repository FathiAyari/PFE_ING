# syntax=docker/dockerfile:1.7

# ---- build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# Cache deps first
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw mvnw
RUN mvn -B -q -DskipTests dependency:go-offline

# Then sources
COPY src ./src
RUN mvn -B -DskipTests clean package \
 && mv target/*.jar /app/app.jar

# ---- runtime stage ----
FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app

# non-root user
RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=build /app/app.jar /app/app.jar

EXPOSE 8080
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
