package cn.superhuang.data.scalpel.admin.filedataset;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseJob;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseJobStatus;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetTableSourceLoadMode;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetParseJobRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class FileDatasetParseJobPersistenceIntegrationTests {

    @Autowired
    private FileDatasetParseJobRepository repository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        repository.deleteAll();
    }

    @Test
    @Transactional
    void persistsQueueSnapshotAndLifecycleChanges() {
        Instant queuedAt = Instant.parse("2026-07-19T01:00:00Z");
        FileDatasetParseJob saved = repository.saveAndFlush(FileDatasetParseJob.queueTableValidation(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                FileDatasetTableSourceLoadMode.REPLACE_SOURCE, UUID.randomUUID(),
                "roads.csv", "FILE", "道路数据", "道路", "roads.csv",
                3, queuedAt
        ));

        saved.claim("admin-1:worker-1", queuedAt, queuedAt.plusSeconds(60));
        repository.saveAndFlush(saved);
        UUID jobId = saved.getId();
        entityManager.clear();

        FileDatasetParseJob reloaded = repository.findById(jobId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(FileDatasetParseJobStatus.RUNNING);
        assertThat(reloaded.getLoadMode()).isEqualTo(FileDatasetTableSourceLoadMode.REPLACE_SOURCE);
        assertThat(reloaded.getTargetSourceId()).isNotNull();
        assertThat(reloaded.getDatasetNameSnapshot()).isEqualTo("道路数据");
        assertThat(reloaded.getTableNameSnapshot()).isEqualTo("道路");
        assertThat(reloaded.getFileNameSnapshot()).isEqualTo("roads.csv");
        assertThat(reloaded.getAttemptCount()).isEqualTo(1);
        assertThat(reloaded.getMaxAttempts()).isEqualTo(3);
        assertThat(reloaded.getLeaseOwner()).isEqualTo("admin-1:worker-1");
        assertThat(reloaded.getLeaseExpiresAt()).isEqualTo(queuedAt.plusSeconds(60));
    }
}
