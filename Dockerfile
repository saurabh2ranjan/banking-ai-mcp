# ── Build Stage ───────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /workspace

COPY gradlew gradlew.bat ./
COPY gradle/ gradle/
COPY build.gradle settings.gradle ./

# Copy all module sources
COPY banking-common/       banking-common/
COPY banking-events/       banking-events/
COPY banking-notification/ banking-notification/
COPY banking-onboarding/   banking-onboarding/
COPY banking-account/      banking-account/
COPY banking-payment/      banking-payment/
COPY banking-fraud/        banking-fraud/
COPY banking-ai-gateway/   banking-ai-gateway/
COPY banking-mcp-client/   banking-mcp-client/

RUN chmod +x gradlew
RUN ./gradlew :banking-ai-gateway:bootJar --no-daemon -q

# ── Runtime Stage ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Non-root user for security
RUN addgroup -S banking && adduser -S banking -G banking
USER banking

# Extract layered jar for faster Docker cache
ARG JAR_FILE=banking-ai-gateway/build/libs/banking-ai-gateway-*.jar
COPY --from=builder /workspace/${JAR_FILE} app.jar

# JVM tuning for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -XX:+UseStringDeduplication \
               -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
