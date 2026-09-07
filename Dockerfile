FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build

# Copy parent pom and module poms to cache dependencies
# (platform modules first, then bounded contexts, then bootstrap)
COPY pom.xml ./
COPY common/pom.xml ./common/
COPY persistence/pom.xml ./persistence/
COPY account-api/pom.xml ./account-api/
COPY infrastructure/pom.xml ./infrastructure/
COPY account/pom.xml ./account/
COPY transfer/pom.xml ./transfer/
COPY user/pom.xml ./user/
COPY audit/pom.xml ./audit/
COPY app/pom.xml ./app/

# Run dependency resolution
RUN --mount=type=cache,target=/root/.m2 mvn dependency:go-offline -B -pl app -am

# Copy source code of all modules
COPY common/src ./common/src
COPY persistence/src ./persistence/src
COPY account-api/src ./account-api/src
COPY infrastructure/src ./infrastructure/src
COPY account/src ./account/src
COPY transfer/src ./transfer/src
COPY user/src ./user/src
COPY audit/src ./audit/src
COPY app/src ./app/src

# Package the project
RUN --mount=type=cache,target=/root/.m2 mvn package -DskipTests -B

# Runner stage
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S bank && adduser -S bank -G bank \
    && apk add --no-cache curl
USER bank
WORKDIR /app

# Copy the executable jar from the app module target directory
COPY --from=builder /build/app/target/*.jar app.jar
EXPOSE 8080

# Container-aware heap defaults; override with JAVA_OPTS env (e.g. -Xmx1g).
# user.timezone=UTC + TZ=UTC: all timestamps are TIMESTAMP (without time zone),
# so the JVM wall clock IS the stored clock. Pinning UTC keeps every replica,
# the DB and the logs on the same instant regardless of host zone.
ENV TZ=UTC
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Duser.timezone=UTC -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
