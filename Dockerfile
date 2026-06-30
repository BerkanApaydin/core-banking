FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build

# Copy parent pom and module poms to cache dependencies
COPY pom.xml ./
COPY common/pom.xml ./common/
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
RUN addgroup -S bank && adduser -S bank -G bank
USER bank
WORKDIR /app

# Copy the executable jar from the app module target directory
COPY --from=builder /build/app/target/*.jar app.jar
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
