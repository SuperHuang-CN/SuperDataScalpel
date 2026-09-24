ARG DOCKER_CLI_IMAGE=docker:cli
ARG JAVA_RUNTIME_IMAGE=eclipse-temurin:21-jre-alpine

FROM ${DOCKER_CLI_IMAGE} AS docker-cli

FROM ${JAVA_RUNTIME_IMAGE}

WORKDIR /app
COPY --from=docker-cli /usr/local/bin/docker /usr/local/bin/docker

EXPOSE 18092

ENTRYPOINT ["java"]
