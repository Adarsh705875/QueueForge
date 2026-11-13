FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN chmod +x mvnw
RUN mvn -B -q -DskipTests dependency:go-offline
COPY src src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S queueforge && adduser -S -G queueforge -u 10001 queueforge \
    && apk add --no-cache curl
WORKDIR /app
COPY --from=build /workspace/target/queueforge-1.0.0.jar /app/app.jar
USER 10001:queueforge
EXPOSE 8080 8081
ENV JAVA_OPTS="-Duser.timezone=UTC -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS http://127.0.0.1:8081/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
