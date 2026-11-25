FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /build
COPY backend/ ./
RUN --mount=type=cache,target=/root/.m2 chmod +x mvnw && ./mvnw -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app
RUN groupadd --system answerbase && useradd --system --gid answerbase --home-dir /app answerbase
RUN mkdir -p /app/documents && chown answerbase:answerbase /app/documents
USER answerbase
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"

FROM runtime AS api
COPY --from=build /build/api/target/answerbase-api-0.1.0-SNAPSHOT.jar /app/application.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/application.jar"]

FROM runtime AS worker
COPY --from=build /build/worker/target/answerbase-worker-0.1.0-SNAPSHOT.jar /app/application.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
