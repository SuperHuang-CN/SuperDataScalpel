# Build from the repository root after the task-engine-full-package Maven profile.
# Supply a verified immutable Spark 4.1.1 / Scala 2.13 / Java 21 image digest.
ARG SPARK_BASE_IMAGE
FROM ${SPARK_BASE_IMAGE}

USER root
RUN mkdir -p /opt/datascalpel && chown 185:0 /opt/datascalpel
COPY --chown=185:0 data-scalpel-task-engine/target/data-scalpel-task-engine-0.1.0-SNAPSHOT-runner-cluster.jar /opt/datascalpel/task-runner-cluster.jar
USER 185
