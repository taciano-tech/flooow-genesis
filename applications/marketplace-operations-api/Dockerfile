# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

COPY . .
RUN sed -i 's/\r$//' gradlew && \
    chmod +x gradlew && \
    ./gradlew :applications:marketplace-operations-api:installDist \
      --no-daemon --stacktrace

FROM eclipse-temurin:21-jre-jammy AS runtime

RUN groupadd --gid 10001 flooow && \
    useradd --uid 10001 --gid flooow --no-create-home --shell /usr/sbin/nologin flooow

WORKDIR /opt/flooow
COPY --from=build --chown=flooow:flooow \
    /workspace/applications/marketplace-operations-api/build/install/marketplace-operations-api/ ./

USER 10001:10001
EXPOSE 8080

ENTRYPOINT ["java", "-cp", "/opt/flooow/lib/*", "io.flooow.marketplace.api.ApplicationKt"]
