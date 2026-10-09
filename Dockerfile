# T-12: base images are parameterized so the release workflow can pin digests
# (docker build --build-arg BASE_BUILDER_IMAGE=...@sha256:...). Default tags
# stay versioned (never :latest); pin digests in release for supply-chain
# reproducibility. Rebuild timestamp is fixed via
# project.build.outputTimestamp in the root pom.
ARG BASE_BUILDER_IMAGE=maven:3.9-eclipse-temurin-21-alpine
ARG BASE_RUNTIME_IMAGE=eclipse-temurin:21-jre-alpine
FROM ${BASE_BUILDER_IMAGE} AS builder
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
FROM ${BASE_RUNTIME_IMAGE}
# D20: pin UID 1000 so the image user matches k8s runAsUser: 1000.
# (Alpine -S assigns the next free system UID otherwise, and the pod
# securityContext would override USER bank with an anonymous UID.)
RUN addgroup -S bank && adduser -S -u 1000 -G bank bank \
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
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Duser.timezone=UTC -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
