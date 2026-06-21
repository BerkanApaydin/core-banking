FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build

# Copy parent pom and module poms to cache dependencies
COPY pom.xml ./
COPY common/pom.xml ./common/
COPY account-module/pom.xml ./account-module/
COPY transfer-module/pom.xml ./transfer-module/
COPY user-module/pom.xml ./user-module/
COPY audit-module/pom.xml ./audit-module/
COPY app/pom.xml ./app/

# Run dependency resolution
RUN --mount=type=cache,target=/root/.m2 mvn dependency:go-offline -B

# Copy source code of all modules
COPY common/src ./common/src
COPY account-module/src ./account-module/src
COPY transfer-module/src ./transfer-module/src
COPY user-module/src ./user-module/src
COPY audit-module/src ./audit-module/src
COPY app/src ./app/src

# Package the project
RUN --mount=type=cache,target=/root/.m2 mvn package -DskipTests -B

# Runner stage
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S bank && adduser -S bank -G bank
USER bank
WORKDIR /app

# Copy the executable jar from the app module target directory
COPY --from=builder /build/app/target/*.jar app.jar
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
