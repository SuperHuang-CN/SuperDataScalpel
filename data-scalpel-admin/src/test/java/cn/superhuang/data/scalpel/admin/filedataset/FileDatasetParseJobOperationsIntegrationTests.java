package cn.superhuang.data.scalpel.admin.filedataset;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDataset;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetCompression;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFile;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFormat;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseJob;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetTable;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetTableSourceLoadMode;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetType;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetFieldRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetFileRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetParseJobRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetTableRepository;
import cn.superhuang.data.scalpel.business.filedataset.service.queue.FileDatasetParseJobHistoryService;
import cn.superhuang.data.scalpel.business.system.configuration.domain.SystemConfiguration;
import cn.superhuang.data.scalpel.business.system.configuration.domain.SystemConfigurationDefinition;
import cn.superhuang.data.scalpel.business.system.configuration.repository.SystemConfigurationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static cn.superhuang.data.scalpel.admin.support.AuthenticationTestSupport.loginAsAdministrator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
class FileDatasetParseJobOperationsIntegrationTests {

    private static final String CSV_OPTIONS = """
            {"kind":"CSV","charset":"UTF-8","fieldDelimiter":",","recordDelimiter":"AUTO",
             "quoteCharacter":"\"","escapeCharacter":"\\","firstRowHeader":true}
            """;

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private FileDatasetRepository datasetRepository;

    @Autowired
    private FileDatasetFileRepository fileRepository;

    @Autowired
    private FileDatasetTableRepository tableRepository;

    @Autowired
    private FileDatasetFieldRepository fieldRepository;

    @Autowired
    private FileDatasetParseJobRepository jobRepository;

    @Autowired
    private SystemConfigurationRepository configurationRepository;

    @Autowired
    private FileDatasetParseJobHistoryService historyService;

    private MockMvc authenticatedMockMvc;
    private MockMvc unauthenticatedMockMvc;

    @BeforeEach
    void setUp() throws Exception {
        unauthenticatedMockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
        String accessToken = loginAsAdministrator(unauthenticatedMockMvc);
        authenticatedMockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .defaultRequest(get("/").header("Authorization", "Bearer " + accessToken))
                .apply(springSecurity())
                .build();
        clearJobsAndResources();
        updateConfiguration(SystemConfigurationDefinition.FILE_DATASET_PARSING_QUEUE_ENABLED, "false");
        updateConfiguration(SystemConfigurationDefinition.FILE_DATASET_PARSING_WORKER_CONCURRENCY, "4");
        updateConfiguration(SystemConfigurationDefinition.FILE_DATASET_PARSING_HISTORY_RETENTION_DAYS, "30");
    }

    @AfterEach
    void tearDown() {
        clearJobsAndResources();
        updateConfiguration(SystemConfigurationDefinition.FILE_DATASET_PARSING_QUEUE_ENABLED, "true");
        updateConfiguration(SystemConfigurationDefinition.FILE_DATASET_PARSING_WORKER_CONCURRENCY, "2");
        updateConfiguration(SystemConfigurationDefinition.FILE_DATASET_PARSING_HISTORY_RETENTION_DAYS, "30");
    }

    @Test
    void exposesQueueSummaryAndSearchableJobDetailsWithoutStorageSecrets() throws Exception {
        ResourceFixture fixture = createResources();
        Instant now = Instant.now();

        FileDatasetParseJob runnable = queued(fixture, now.minusSeconds(120));
        FileDatasetParseJob retryWaiting = queued(fixture, now.minusSeconds(110));
        retryWaiting.claim("retry-worker", now.minusSeconds(80), now.plusSeconds(20));
        retryWaiting.retry("retry-worker", "对象存储暂时不可用", now.minusSeconds(60), now.plusSeconds(600));
        FileDatasetParseJob running = queued(fixture, now.minusSeconds(100));
        running.claim("admin-a:worker-1", now.minusSeconds(20), now.plusSeconds(40));
        FileDatasetParseJob succeeded = terminal(fixture, now.minusSeconds(90), Terminal.SUCCEEDED);
        FileDatasetParseJob failed = terminal(fixture, now.minusSeconds(80), Terminal.FAILED);
        FileDatasetParseJob cancelled = terminal(fixture, now.minusSeconds(70), Terminal.CANCELLED);
        jobRepository.saveAllAndFlush(java.util.List.of(
                runnable, retryWaiting, running, succeeded, failed, cancelled
        ));

        authenticatedMockMvc.perform(get("/api/v1/file-dataset-parse-jobs/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queueEnabled").value(false))
                .andExpect(jsonPath("$.configuredWorkerConcurrency").value(4))
                .andExpect(jsonPath("$.historyRetentionDays").value(30))
                .andExpect(jsonPath("$.queuedCount").value(2))
                .andExpect(jsonPath("$.runnableQueuedCount").value(1))
                .andExpect(jsonPath("$.retryWaitingCount").value(1))
                .andExpect(jsonPath("$.runningCount").value(1))
                .andExpect(jsonPath("$.succeededCount").value(1))
                .andExpect(jsonPath("$.failedCount").value(1))
                .andExpect(jsonPath("$.cancelledCount").value(1));

        authenticatedMockMvc.perform(get("/api/v1/file-dataset-parse-jobs")
                        .param("search", "status:\"RUNNING\"")
                        .param("page", "0")
                        .param("size", "20")
                        .param("sort", "-queuedAt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(running.getId().toString()))
                .andExpect(jsonPath("$.content[0].fileDatasetName").value("运维监控数据集"))
                .andExpect(jsonPath("$.content[0].sourceFileName").value("monitor.csv"))
                .andExpect(jsonPath("$.content[0].tableName").value("监控表"))
                .andExpect(jsonPath("$.content[0].leaseOwner").value("admin-a:worker-1"))
                .andExpect(jsonPath("$.content[0].objectKey").doesNotExist());

        unauthenticatedMockMvc.perform(get("/api/v1/file-dataset-parse-jobs/summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cleansOnlyExpiredTerminalHistoryAndUsesTheLatestRetentionSetting() {
        ResourceFixture fixture = createResources();
        Instant now = Instant.now();
        FileDatasetParseJob oldSucceeded = terminal(
                fixture, now.minus(45, ChronoUnit.DAYS), Terminal.SUCCEEDED
        );
        FileDatasetParseJob oldFailed = terminal(
                fixture, now.minus(44, ChronoUnit.DAYS), Terminal.FAILED
        );
        FileDatasetParseJob oldCancelled = terminal(
                fixture, now.minus(43, ChronoUnit.DAYS), Terminal.CANCELLED
        );
        FileDatasetParseJob recentTerminal = terminal(
                fixture, now.minus(2, ChronoUnit.DAYS), Terminal.SUCCEEDED
        );
        FileDatasetParseJob oldQueued = queued(fixture, now.minus(90, ChronoUnit.DAYS));
        FileDatasetParseJob oldRunning = queued(fixture, now.minus(90, ChronoUnit.DAYS));
        oldRunning.claim(
                "abandoned-worker",
                now.minus(89, ChronoUnit.DAYS),
                now.minus(89, ChronoUnit.DAYS).plusSeconds(60)
        );
        jobRepository.saveAllAndFlush(java.util.List.of(
                oldSucceeded, oldFailed, oldCancelled, recentTerminal, oldQueued, oldRunning
        ));

        assertThat(historyService.cleanExpiredHistory()).isEqualTo(3);
        assertThat(jobRepository.existsById(oldSucceeded.getId())).isFalse();
        assertThat(jobRepository.existsById(oldFailed.getId())).isFalse();
        assertThat(jobRepository.existsById(oldCancelled.getId())).isFalse();
        assertThat(jobRepository.existsById(recentTerminal.getId())).isTrue();
        assertThat(jobRepository.existsById(oldQueued.getId())).isTrue();
        assertThat(jobRepository.existsById(oldRunning.getId())).isTrue();

        updateConfiguration(SystemConfigurationDefinition.FILE_DATASET_PARSING_HISTORY_RETENTION_DAYS, "1");
        assertThat(historyService.cleanExpiredHistory()).isEqualTo(1);
        assertThat(jobRepository.existsById(recentTerminal.getId())).isFalse();
        assertThat(jobRepository.existsById(oldQueued.getId())).isTrue();
        assertThat(jobRepository.existsById(oldRunning.getId())).isTrue();
    }

    private ResourceFixture createResources() {
        FileDataset dataset = datasetRepository.saveAndFlush(FileDataset.create(
                null, "运维监控数据集", FileDatasetType.CSV, CSV_OPTIONS, null
        ));
        FileDatasetFile file = fileRepository.saveAndFlush(FileDatasetFile.create(
                dataset.getId(), "monitor.csv", FileDatasetFormat.CSV, FileDatasetCompression.NONE,
                "private/monitor.csv", "text/csv", 10, null
        ));
        FileDatasetTable table = tableRepository.saveAndFlush(
                FileDatasetTable.create(dataset.getId(), "monitor", "监控表")
        );
        return new ResourceFixture(dataset.getId(), file.getId(), table.getId());
    }

    private static FileDatasetParseJob queued(ResourceFixture fixture, Instant queuedAt) {
        return FileDatasetParseJob.queueTableValidation(
                fixture.datasetId(), fixture.fileId(), fixture.tableId(),
                FileDatasetTableSourceLoadMode.APPEND, null,
                "monitor.csv", "FILE", "运维监控数据集", "监控表", "monitor.csv",
                3, queuedAt
        );
    }

    private static FileDatasetParseJob terminal(
            ResourceFixture fixture,
            Instant queuedAt,
            Terminal terminal
    ) {
        FileDatasetParseJob job = queued(fixture, queuedAt);
        Instant operationAt = queuedAt.plusSeconds(10);
        if (terminal == Terminal.CANCELLED) {
            job.cancel("业务操作取消", operationAt);
            return job;
        }
        job.claim("terminal-worker", operationAt, operationAt.plusSeconds(60));
        if (terminal == Terminal.SUCCEEDED) {
            job.succeed("terminal-worker", operationAt.plusSeconds(10));
        } else {
            job.fail("terminal-worker", "文件内容无效", operationAt.plusSeconds(10));
        }
        return job;
    }

    private void updateConfiguration(SystemConfigurationDefinition definition, String value) {
        SystemConfiguration configuration = configurationRepository.findByConfigKey(definition.getConfigKey())
                .orElseThrow();
        configuration.updateValue(value);
        configurationRepository.saveAndFlush(configuration);
    }

    private void clearJobsAndResources() {
        jobRepository.deleteAll();
        fieldRepository.deleteAll();
        tableRepository.deleteAll();
        fileRepository.deleteAll();
        datasetRepository.deleteAll();
    }

    private enum Terminal {
        SUCCEEDED,
        FAILED,
        CANCELLED
    }

    private record ResourceFixture(UUID datasetId, UUID fileId, UUID tableId) {
    }
}
