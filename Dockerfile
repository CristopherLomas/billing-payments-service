# ==============================================================================
# Multi-stage Dockerfile for billing-payments-service (Java 21, Spring Boot 3.3)
# ==============================================================================

# ------------------------------------------------------------------------------
# Stage 1: Build stage with Maven and Eclipse Temurin JDK 21
# ------------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /workspace

# Cache Maven dependencies layer
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy application source code and package executable jar
COPY src ./src
RUN mvn clean package -DskipTests -B

# ------------------------------------------------------------------------------
# Stage 2: Production runtime stage with Eclipse Temurin JRE 21 Alpine
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runner
WORKDIR /app

# Create unprivileged application user and group for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy compiled jar from builder stage
COPY --from=builder /workspace/target/billing-payments-service-*.jar /app/app.jar

# Adjust ownership to non-root user
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Expose microservice HTTP port
EXPOSE 8085

# Healthcheck for container orchestration
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=5 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8085/actuator/health || exit 1

# Java options and production entrypoint
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
