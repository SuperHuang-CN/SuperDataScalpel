FROM docker:cli AS docker-cli

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=docker-cli /usr/local/bin/docker /usr/local/bin/docker

EXPOSE 18092

ENTRYPOINT ["java"]
