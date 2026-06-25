FROM maven:3.9.11-eclipse-temurin-21 AS builder
WORKDIR /app

COPY pom.xml ./
COPY .mvn .mvn
COPY mvnw ./
COPY src src

RUN chmod +x mvnw && ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

# Give the JVM a real heap. With no flags the container-aware JVM caps the heap at
# ~25% of RAM (≈128 MB on Render's 512 MB Starter), which starves Spring Boot +
# Hibernate and OOMs under load. 50% (≈256 MB) is safe once Tomcat threads are
# capped (see application.yaml) and adapts if the instance is upsized. Exit on OOM
# so Render restarts a clean process instead of limping with an exhausted pool.
# Tunable via JAVA_OPTS without a rebuild; `exec` keeps the JVM as PID 1.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=50.0 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]