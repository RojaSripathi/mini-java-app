# =============================================================================
# Multi-Stage Dockerfile for Tenant Admin (mini-java-app)
# Java 11 | Maven | Spring Boot 2.7.0
# =============================================================================

# ---------------------------------------------------------------------------
# Stage 1: Builder
# ---------------------------------------------------------------------------
FROM maven:3.9.4-eclipse-temurin-11 AS builder

WORKDIR /workspace

# Copy dependency descriptor first for layer caching
COPY pom.xml .

# Download all dependencies offline (cached layer)
RUN mvn dependency:go-offline -B

# Copy the full source tree
COPY src ./src

# Build the application JAR (skip tests for Docker build)
RUN mvn clean package -DskipTests -B

# ---------------------------------------------------------------------------
# Stage 2: Runtime
# ---------------------------------------------------------------------------
FROM eclipse-temurin:11-jdk-alpine

# Metadata labels
LABEL maintainer="Tenant Admin Team" \
      application="tenant-admin" \
      version="1.0.0" \
      java.version="11"

# Set timezone
ENV TZ=UTC

# JVM tuning for containerised environments
ENV JAVA_OPTS="-Xmx512m -Xms256m \
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -XX:+UnlockExperimentalVMOptions \
  -Djava.security.egd=file:/dev/./urandom \
  -Dfile.encoding=UTF-8 \
  -Duser.timezone=UTC"

# Application environment variables (override at runtime)
ENV SERVER_PORT=8080 \
    HEALTH_CHECK_PORT=8081 \
    CONFIG_FILE_PATH=/opt/app/config/app.properties \
    LOG_FILE_PATH=/var/log/mini-app/mini-app.log \
    LOG_DIR=/var/log/mini-app \
    DB_URL=jdbc:mysql://localhost:3306/mini_app_db \
    DB_USERNAME=root \
    DB_PASSWORD=changeme \
    REDIS_HOST=localhost \
    REDIS_PORT=6379

WORKDIR /app

# Create non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Create required runtime directories
RUN mkdir -p /opt/app/config /var/log/mini-app /tmp/mini-app /opt/uploads \
    && chown -R appuser:appgroup /app /opt/app /var/log/mini-app /tmp/mini-app /opt/uploads

# Copy the built JAR from builder stage
COPY --from=builder /workspace/target/*.jar app.jar

# Ensure the appuser owns the jar
RUN chown appuser:appgroup app.jar

# Switch to non-root user
USER appuser

# Expose application and health-check ports
EXPOSE 8080 8081

# Graceful shutdown support (Spring Boot / JVM)
STOPSIGNAL SIGTERM

# Run the application
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
