package cn.superhuang.data.scalpel.dispatcher;

import cn.superhuang.data.scalpel.contract.execution.ExecutionArtifactLocation;
import cn.superhuang.data.scalpel.contract.execution.ExecutionMessageType;
import cn.superhuang.data.scalpel.contract.execution.ExecutionTaskType;
import cn.superhuang.data.scalpel.contract.execution.SubmitExecutionCommand;
import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherExecutionState;
import cn.superhuang.data.scalpel.dispatcher.messaging.MessageCoordinates;
import cn.superhuang.data.scalpel.dispatcher.messaging.command.DispatcherCommandService;
import cn.superhuang.data.scalpel.dispatcher.repository.DispatcherTaskExecutionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class DispatcherManagementIntegrationTest {
    @Autowired
    private WebApplicationContext applicationContext;
    @Autowired
    private DispatcherCommandService commandService;
    @Autowired
    private DispatcherTaskExecutionRepository executionRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext).build();
    }

    @Test
    void exposesAuthenticatedIdempotentRegistrationLifecycle() throws Exception {
        mockMvc.perform(get("/health/live"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/api/v1/dispatcher/info"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/v1/dispatcher/info").header("Authorization", "Bearer test-dispatcher-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.protocolVersion").value(1))
                .andExpect(jsonPath("$.backendType").value("LOCAL_DOCKER"));

        UUID engineId = UUID.randomUUID();
        String body = registration(engineId, 1);
        mockMvc.perform(post("/api/v1/dispatcher/registration/actions/activate")
                        .header("Authorization", "Bearer test-dispatcher-token")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.engineId").value(engineId.toString()))
                .andExpect(jsonPath("$.state").value("ACTIVE"))
                .andExpect(jsonPath("$.effectiveAdmissionPolicy.maxQueuedExecutions").value(20));
        mockMvc.perform(post("/api/v1/dispatcher/registration/actions/activate")
                        .header("Authorization", "Bearer test-dispatcher-token")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ACTIVE"));
        mockMvc.perform(get("/health/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kafka").value("UP"))
                .andExpect(jsonPath("$.listeners").value("UP"));

        mockMvc.perform(post("/api/v1/dispatcher/registration/actions/drain")
                        .header("Authorization", "Bearer test-dispatcher-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("DRAINING"));
        mockMvc.perform(post("/api/v1/dispatcher/registration/actions/deactivate")
                        .header("Authorization", "Bearer test-dispatcher-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"force\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("INACTIVE"));
    }

    @Test
    void forceDeactivationCancelsQueuedExecutionsBeforeBecomingInactive() throws Exception {
        UUID engineId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/dispatcher/registration/actions/activate")
                        .header("Authorization", "Bearer test-dispatcher-token")
                        .contentType(MediaType.APPLICATION_JSON).content(registration(engineId, 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ACTIVE"));

        UUID executionId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        String prefix = "task-runs/" + runId + "/attempts/1/";
        Instant now = Instant.now();
        SubmitExecutionCommand command = new SubmitExecutionCommand(
                1, UUID.randomUUID(), ExecutionMessageType.SUBMIT_EXECUTION, now,
                engineId, executionId, runId, 1, UUID.randomUUID(), ExecutionTaskType.SPARK_CANVAS,
                1, now.plusSeconds(3600), new ExecutionArtifactLocation(
                prefix + "manifest.json", "a".repeat(64), prefix + "result.json", prefix + "console.log"
        ));
        assertThat(commandService.accept(command, new MessageCoordinates("commands.local", 0, 1)))
                .isEqualTo(DispatcherCommandService.Outcome.ACCEPTED);

        mockMvc.perform(post("/api/v1/dispatcher/registration/actions/deactivate")
                        .header("Authorization", "Bearer test-dispatcher-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"force\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("INACTIVE"));

        assertThat(executionRepository.findByExecutionId(executionId).orElseThrow().getState())
                .isEqualTo(DispatcherExecutionState.CANCELLED);
    }

    private static String registration(UUID engineId, long revision) {
        return """
                {
                  "protocolVersion":1,
                  "engineId":"%s",
                  "configRevision":%d,
                  "topics":{
                    "commandTopic":"commands.local",
                    "runnerEventTopic":"runner.local",
                    "adminEventTopic":"admin.events"
                  },
                  "admissionPolicy":{
                    "maxQueuedExecutions":20,
                    "maxConcurrentSubmissions":2,
                    "maxInFlightApplications":2
                  }
                }
                """.formatted(engineId, revision);
    }
}
