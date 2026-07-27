package cn.superhuang.data.scalpel.dispatcher.backend.localdocker;

import cn.superhuang.data.scalpel.dispatcher.backend.BackendException;
import cn.superhuang.data.scalpel.dispatcher.backend.ExecutionIdentity;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DockerInspectParserTest {
    private final DockerInspectParser parser = new DockerInspectParser(new ObjectMapper());

    @Test
    void parsesStrictInspectionAndIdentityLabels() throws BackendException {
        ExecutionIdentity identity = identity();
        String json = inspectionJson("running", true, 0, identity);

        DockerContainerInspection inspection = parser.parseInspection(json);

        assertThat(inspection.id()).hasSize(64);
        assertThat(inspection.status()).isEqualTo("running");
        assertThat(inspection.hasIdentity(identity)).isTrue();
        assertThat(inspection.parsedStartedAt()).isNotNull();
    }

    @Test
    void rejectsUnexpectedInspectFieldsAndInvalidPsLines() {
        assertThatThrownBy(() -> parser.parseInspection(inspectionJson("running", true, 0, identity())
                .replaceFirst("\\{", "{\"unexpected\":true,")))
                .isInstanceOf(BackendException.class)
                .extracting(error -> ((BackendException) error).code())
                .isEqualTo("INVALID_DOCKER_RESPONSE");
        assertThatThrownBy(() -> parser.parseContainerIds("\"short-id\""))
                .isInstanceOf(BackendException.class);
    }

    static ExecutionIdentity identity() {
        return new ExecutionIdentity(
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                UUID.fromString("20000000-0000-0000-0000-000000000002"),
                UUID.fromString("30000000-0000-0000-0000-000000000003"), 1);
    }

    static String inspectionJson(String state, boolean running, int exitCode, ExecutionIdentity identity) {
        return """
                {"id":"%s","name":"/datascalpel-runner-%s","createdAt":"2026-07-17T12:00:00Z",\
                "labels":{"cn.superhuang.datascalpel.managed":"true",\
                "cn.superhuang.datascalpel.engine-id":"%s",\
                "cn.superhuang.datascalpel.execution-id":"%s",\
                "cn.superhuang.datascalpel.run-id":"%s",\
                "cn.superhuang.datascalpel.attempt":"%d"},\
                "status":"%s","running":%s,"startedAt":"2026-07-17T12:00:01Z",\
                "finishedAt":"2026-07-17T12:00:02Z","exitCode":%d,"error":""}
                """.formatted("a".repeat(64), identity.executionId(), identity.engineId(), identity.executionId(),
                identity.runId(), identity.attempt(), state, running, exitCode);
    }
}
