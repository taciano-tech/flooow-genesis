# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

COPY . .
RUN sed -i 's/\r$//' gradlew && \
    chmod +x gradlew && \
    ./gradlew :applications:marketplace-operations-api:installDist \
      --no-daemon --stacktrace

FROM eclipse-temurin:21-jre-jammy AS runtime

ARG OTEL_JAVAAGENT_VERSION=2.30.0
ARG OTEL_JAVAAGENT_SHA256=9d6bc2ad8dd8fb7f730984988e57b8ac0a82d81c7b3b8ae795378718733a509d
ADD --checksum=sha256:${OTEL_JAVAAGENT_SHA256} \
    https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_JAVAAGENT_VERSION}/opentelemetry-javaagent.jar \
    /opt/opentelemetry-javaagent.jar

RUN apt-get purge --yes curl wget && \
    apt-get autoremove --yes && \
    rm -rf /var/lib/apt/lists/* && \
    groupadd --gid 10001 flooow && \
    useradd --uid 10001 --gid flooow --no-create-home --shell /usr/sbin/nologin flooow && \
    chmod 0444 /opt/opentelemetry-javaagent.jar

WORKDIR /opt/flooow
COPY --from=build --chown=flooow:flooow \
    /workspace/applications/marketplace-operations-api/build/install/marketplace-operations-api/ ./

USER 10001:10001
EXPOSE 8080

ENTRYPOINT ["java", "-javaagent:/opt/opentelemetry-javaagent.jar", "-cp", "/opt/flooow/lib/*", "io.flooow.marketplace.api.ApplicationKt"]
