package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceType;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.task.domain.TaskTmqConsumerGroupCleanup;
import cn.superhuang.data.scalpel.business.task.domain.TmqConsumerGroupCleanupState;
import cn.superhuang.data.scalpel.business.task.repository.CanvasTaskDefinitionRepository;
import cn.superhuang.data.scalpel.business.task.repository.TaskTmqConsumerGroupCleanupRepository;
import cn.superhuang.data.scalpel.contract.task.CanvasDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.KafkaOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.ModelOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.TdEngineTmqConsumerGroupIdentity;
import cn.superhuang.data.scalpel.contract.task.TdEngineTmqInputNodeDefinition;
import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionConfig;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class TmqConsumerGroupCleanupService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TmqConsumerGroupCleanupService.class);
    private static final List<TmqConsumerGroupCleanupState> DUE_STATES = List.of(
            TmqConsumerGroupCleanupState.PENDING, TmqConsumerGroupCleanupState.FAILED);

    private final TaskTmqConsumerGroupCleanupRepository repository;
    private final CanvasTaskDefinitionRepository definitionRepository;
    private final DataSourceRepository dataSourceRepository;
    private final DialectRegistry dialectRegistry;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transaction;
    private final TransactionTemplate readTransaction;
    private final JdbcConnectionFactory connectionFactory = new JdbcConnectionFactory();

    public TmqConsumerGroupCleanupService(
            TaskTmqConsumerGroupCleanupRepository repository,
            CanvasTaskDefinitionRepository definitionRepository,
            DataSourceRepository dataSourceRepository,
            DialectRegistry dialectRegistry,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager
    ) {
        this.repository = repository;
        this.definitionRepository = definitionRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.dialectRegistry = dialectRegistry;
        this.objectMapper = objectMapper;
        this.transaction = new TransactionTemplate(transactionManager);
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
    }

    @Transactional
    public void enqueueRemovedGroups(
            UUID taskId,
            CanvasDefinition previous,
            CanvasDefinition current
    ) {
        if (taskId == null || previous == null) return;
        Set<GroupReference> retained = groups(taskId, current);
        for (GroupReference removed : groups(taskId, previous)) {
            if (retained.contains(removed)) continue;
            TaskTmqConsumerGroupCleanup cleanup = repository
                    .findByDataSourceIdAndTopicNameAndConsumerGroupId(
                            removed.dataSourceId(), removed.topicName(), removed.consumerGroupId())
                    .orElseGet(() -> TaskTmqConsumerGroupCleanup.create(
                            taskId, removed.dataSourceId(), removed.topicName(), removed.consumerGroupId()));
            if (cleanup.getId() != null
                    && cleanup.getState() != TmqConsumerGroupCleanupState.RUNNING) {
                cleanup.requeue();
            }
            repository.save(cleanup);
        }
    }

    public boolean runOne() {
        CleanupClaim claim = transaction.execute(status -> {
            Instant now = Instant.now();
            repository.findFirstByStateAndClaimedAtLessThanEqualOrderByClaimedAtAsc(
                            TmqConsumerGroupCleanupState.RUNNING, now.minus(Duration.ofMinutes(5)))
                    .ifPresent(cleanup -> cleanup.fail("上次清理中断，将自动重试", now));
            return repository.findFirstByStateInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                            DUE_STATES, now)
                    .map(cleanup -> {
                        cleanup.claim(now);
                        repository.saveAndFlush(cleanup);
                        return CleanupClaim.from(cleanup);
                    }).orElse(null);
        });
        if (claim == null) return false;
        try {
            if (!isStillReferenced(claim)) {
                dropConsumerGroup(claim);
            }
            transaction.executeWithoutResult(status -> repository.findById(claim.id())
                    .ifPresent(cleanup -> cleanup.succeed(Instant.now())));
        } catch (Exception exception) {
            Instant retryAt = Instant.now().plus(retryDelay(claim.attempts()));
            String safeError = "TMQ Consumer Group 清理失败（"
                    + exception.getClass().getSimpleName() + "）";
            transaction.executeWithoutResult(status -> repository.findById(claim.id())
                    .ifPresent(cleanup -> cleanup.fail(safeError, retryAt)));
            LOGGER.warn(
                    "TMQ Consumer Group cleanup failed: taskId={}, dataSourceId={}, topic={}, groupId={}, attempts={}",
                    claim.taskId(), claim.dataSourceId(), claim.topicName(),
                    claim.consumerGroupId(), claim.attempts());
        }
        return true;
    }

    public CleanupCounts counts(UUID taskId) {
        long pending = repository.countByTaskIdAndStateIn(taskId, List.of(
                TmqConsumerGroupCleanupState.PENDING, TmqConsumerGroupCleanupState.RUNNING));
        long failed = repository.countByTaskIdAndStateIn(taskId, List.of(TmqConsumerGroupCleanupState.FAILED));
        return new CleanupCounts(pending, failed);
    }

    private boolean isStillReferenced(CleanupClaim claim) {
        return Boolean.TRUE.equals(readTransaction.execute(status -> definitionRepository
                .findByTaskId(claim.taskId())
                .map(persisted -> {
                    try {
                        CanvasDefinition definition = objectMapper.readValue(
                                persisted.getDefinitionJson(), CanvasDefinition.class);
                        return groups(claim.taskId(), definition).contains(claim.reference());
                    } catch (Exception exception) {
                        throw new IllegalStateException("无法检查当前 Canvas TMQ Consumer Group 引用", exception);
                    }
                }).orElse(false)));
    }

    private void dropConsumerGroup(CleanupClaim claim) throws Exception {
        CleanupConnection connection = readTransaction.execute(status -> dataSourceRepository
                .findById(claim.dataSourceId())
                .map(source -> {
                    if (source.getType() != DataSourceType.TDENGINE_WEBSOCKET) {
                        throw new IllegalStateException("清理目标不再是 TDengine WebSocket 数据源");
                    }
                    return new CleanupConnection(
                            source.getType().name(), source.getConnection().toJdbcConnectionConfig());
                })
                .orElseThrow(() -> new IllegalStateException("清理目标数据源不存在")));
        if (connection == null) throw new IllegalStateException("清理目标数据源不存在");
        var dialect = dialectRegistry.require(connection.databaseType());
        try (var jdbc = connectionFactory.open(dialect.createConnectionSpec(connection.config()));
             Statement statement = jdbc.createStatement()) {
            statement.setQueryTimeout(30);
            statement.execute("DROP CONSUMER GROUP IF EXISTS "
                    + identifier(claim.consumerGroupId()) + " ON " + identifier(claim.topicName()));
        }
    }

    private static Set<GroupReference> groups(UUID taskId, CanvasDefinition definition) {
        if (definition == null || definition.nodes() == null || definition.edges() == null) return Set.of();
        TdEngineTmqInputNodeDefinition source = definition.nodes().stream()
                .filter(TdEngineTmqInputNodeDefinition.class::isInstance)
                .map(TdEngineTmqInputNodeDefinition.class::cast)
                .findFirst().orElse(null);
        if (source == null || source.configuration() == null) return Set.of();
        UUID dataSourceId;
        UUID sourceNodeId;
        try {
            dataSourceId = UUID.fromString(source.configuration().dataSourceId());
            sourceNodeId = UUID.fromString(source.id());
        } catch (RuntimeException exception) {
            return Set.of();
        }
        if (source.configuration().topicName() == null || source.configuration().topicName().isBlank()) {
            return Set.of();
        }
        Map<String, List<String>> successors = new HashMap<>();
        definition.edges().forEach(edge -> successors
                .computeIfAbsent(edge.sourceNodeId(), ignored -> new ArrayList<>())
                .add(edge.targetNodeId()));
        Set<String> reachable = new HashSet<>();
        ArrayDeque<String> pending = new ArrayDeque<>();
        reachable.add(source.id());
        pending.add(source.id());
        while (!pending.isEmpty()) {
            for (String target : successors.getOrDefault(pending.removeFirst(), List.of())) {
                if (reachable.add(target)) pending.addLast(target);
            }
        }
        Set<GroupReference> result = new HashSet<>();
        for (CanvasNodeDefinition node : definition.nodes()) {
            if (!reachable.contains(node.id())) continue;
            UUID outputNodeId;
            try {
                outputNodeId = UUID.fromString(node.id());
            } catch (RuntimeException exception) {
                continue;
            }
            List<String> writeIds = switch (node) {
                case JdbcOutputNodeDefinition output -> output.configuration().writes().stream()
                        .map(write -> write.writeId()).toList();
                case ModelOutputNodeDefinition output -> output.configuration().writes().stream()
                        .map(write -> write.writeId()).toList();
                case KafkaOutputNodeDefinition output -> output.configuration().writes().stream()
                        .map(write -> write.writeId()).toList();
                default -> List.of();
            };
            for (String writeId : writeIds) {
                try {
                    String groupId = TdEngineTmqConsumerGroupIdentity.groupId(
                            taskId, sourceNodeId, outputNodeId, UUID.fromString(writeId));
                    result.add(new GroupReference(dataSourceId, source.configuration().topicName(), groupId));
                } catch (RuntimeException ignored) {
                    // Invalid drafts never produce a runtime Consumer Group.
                }
            }
        }
        return Set.copyOf(result);
    }

    private static String identifier(String value) {
        return "`" + value.replace("`", "``") + "`";
    }

    private static Duration retryDelay(int attempts) {
        long seconds = Math.min(3600L, 30L * (1L << Math.min(6, Math.max(0, attempts - 1))));
        return Duration.ofSeconds(seconds);
    }

    public record CleanupCounts(long pending, long failed) {
    }

    private record CleanupConnection(String databaseType, JdbcConnectionConfig config) {
    }

    private record GroupReference(UUID dataSourceId, String topicName, String consumerGroupId) {
    }

    private record CleanupClaim(
            UUID id,
            UUID taskId,
            UUID dataSourceId,
            String topicName,
            String consumerGroupId,
            int attempts
    ) {
        static CleanupClaim from(TaskTmqConsumerGroupCleanup cleanup) {
            return new CleanupClaim(
                    cleanup.getId(), cleanup.getTaskId(), cleanup.getDataSourceId(),
                    cleanup.getTopicName(), cleanup.getConsumerGroupId(), cleanup.getAttempts());
        }

        GroupReference reference() {
            return new GroupReference(dataSourceId, topicName, consumerGroupId);
        }
    }
}
