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
#
# Production memory investigation (ongoing): actuator sampling showed heap
# committed (~207 MB) sitting well above heap used (~99 MB) during a container
# RSS plateau near the 512 MB limit — G1 wasn't handing idle-committed heap back
# to the OS. G1PeriodicGCInterval forces a periodic opportunity to uncommit that
# slack; G1PeriodicGCInvokesConcurrent keeps that cycle concurrent so it doesn't
# introduce pause-time regressions on this 0.5 CPU instance. MaxMetaspaceSize
# caps a pool that was otherwise unbounded (jvm.memory.max reported -1) so a
# future runaway shows up as a logged OutOfMemoryError instead of a silent
# SIGKILL. NativeMemoryTracking=summary costs little and gives `jcmd <pid>
# VM.native_memory summary` a breakdown to run if the plateau doesn't move.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=50.0 -XX:+ExitOnOutOfMemoryError -XX:G1PeriodicGCInterval=30000 -XX:+G1PeriodicGCInvokesConcurrent -XX:MaxMetaspaceSize=200m -XX:NativeMemoryTracking=summary"

# This image is glibc-based (Debian), not musl/Alpine. glibc allocates a separate
# malloc arena per thread for concurrency, and those arenas fragment and are never
# returned to the OS — with multiple thread pools (Tomcat, study-pack-generation,
# llm-parallel) churning small allocations all day, container RSS can silently climb
# to the cgroup memory limit and get SIGKILLed with no JVM-level OutOfMemoryError
# ever logged, since this is OS/libc memory the JVM has zero visibility into. Capping
# arenas trades a little allocation throughput for bounded, predictable native memory.
ENV MALLOC_ARENA_MAX=2

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]