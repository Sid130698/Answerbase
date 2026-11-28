FROM node:24-alpine AS frontend
WORKDIR /build
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
ARG VITE_SUPABASE_URL
ARG VITE_SUPABASE_PUBLISHABLE_KEY
ARG VITE_SUPPORT_ORIGIN
RUN npm run build

FROM eclipse-temurin:21-jdk-jammy AS backend
WORKDIR /build
COPY backend/ ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends nginx && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home answerbase
WORKDIR /app
COPY --from=backend /build/api/target/answerbase-api-0.1.0-SNAPSHOT.jar /app/application.jar
COPY --from=frontend /build/dist /app/web
COPY infra/render/nginx.conf /app/nginx.conf
COPY infra/render/start.sh /app/start.sh
COPY infra/render/supabase-ca.crt /app/supabase-ca.crt
RUN chmod +x /app/start.sh
USER answerbase
ENV JAVA_TOOL_OPTIONS="-Xmx256m -Xss256k -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=32m -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"
ENV SPRING_TASK_SCHEDULING_POOL_SIZE=2
ENV EMBEDDED_WORKER=true API_DOCS_ENABLED=false EMBEDDING_PROVIDER=supabase RETRIEVAL_RERANK=false
EXPOSE 10000
ENTRYPOINT ["/app/start.sh"]
