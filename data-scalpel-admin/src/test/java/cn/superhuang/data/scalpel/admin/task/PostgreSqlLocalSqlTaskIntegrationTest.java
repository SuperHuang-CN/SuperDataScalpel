package cn.superhuang.data.scalpel.admin.task;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceConnection;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourcePurpose;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceType;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.business.model.domain.PhysicalTableMode;
import cn.superhuang.data.scalpel.business.model.repository.DataModelFieldRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.model.service.ModelPhysicalTablePort;
import cn.superhuang.data.scalpel.business.task.domain.LocalSqlWriteMode;
import cn.superhuang.data.scalpel.business.task.domain.TaskType;
import cn.superhuang.data.scalpel.business.task.domain.TaskRun;
import cn.superhuang.data.scalpel.business.task.domain.TaskRunStatus;
import cn.superhuang.data.scalpel.business.task.repository.DataTaskRepository;
import cn.superhuang.data.scalpel.business.task.repository.LocalSqlTaskDefinitionRepository;
import cn.superhuang.data.scalpel.business.task.repository.LocalSqlTaskInputRepository;
import cn.superhuang.data.scalpel.business.task.repository.TaskRunRepository;
import cn.superhuang.data.scalpel.business.task.service.DataTaskService;
import cn.superhuang.data.scalpel.business.task.service.TaskRunDefinitionSnapshot;
import cn.superhuang.data.scalpel.business.task.service.TaskRunService;
import cn.superhuang.data.scalpel.business.task.web.request.CreateDataTaskRequest;
import cn.superhuang.data.scalpel.business.task.web.request.UpdateLocalSqlTaskDefinitionRequest;
import cn.superhuang.data.scalpel.business.task.web.response.TaskRunResponse;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionConfig;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opt-in full-path verification for LOCAL_SQL against a disposable PostgreSQL schema.
 *
 * <p>Set {@code DATASCALPEL_PG_INTEGRATION=true} together with the connection variables used by
 * {@code PostgreSqlTableChangeIntegrationTest}. The management database stays on the normal H2
 * test profile; only model physical tables and the task SQL execute in PostgreSQL.</p>
 */
@EnabledIfEnvironmentVariable(named = "DATASCALPEL_PG_INTEGRATION", matches = "(?i)true")
@ActiveProfiles("test")
@SpringBootTest
class PostgreSqlLocalSqlTaskIntegrationTest {

    private static final Duration RUN_TIMEOUT = Duration.ofSeconds(12);

    @Autowired
    private DataTaskService dataTaskService;

    @Autowired
    private TaskRunService taskRunService;

    @Autowired
    private DataSourceRepository dataSourceRepository;

    @Autowired
    private DataModelRepository modelRepository;

    @Autowired
    private DataModelFieldRepository fieldRepository;

    @Autowired
    private DataTaskRepository taskRepository;

    @Autowired
    private LocalSqlTaskDefinitionRepository definitionRepository;

    @Autowired
    private LocalSqlTaskInputRepository inputRepository;

    @Autowired
    private TaskRunRepository runRepository;

    @Autowired
    private ModelPhysicalTablePort physicalTablePort;

    @Autowired
    private ObjectMapper objectMapper;

    private final JdbcConnectionFactory connectionFactory = new JdbcConnectionFactory();

    private JdbcConnectionConfig postgres;

    @BeforeEach
    void setUp() throws SQLException, ClassNotFoundException {
        postgres = integrationConfig();
        ensureSchema();
        clearManagementData();
    }

    @AfterEach
    void tearDown() {
        clearManagementData();
    }

    @Test
    void executesPlainAndCteAppendUsingQueryColumnOrder() throws Exception {
        try (Fixture fixture = fixture()) {
            fixture.insertSource("10.00", "first");
            fixture.insertSource("20.00", "second");

            UUID plainTask = createTask(fixture, "plain_append");
            saveDefinition(plainTask, fixture, "SELECT id, amount, label FROM " + fixture.sourceTable(), LocalSqlWriteMode.APPEND, 5);
            dataTaskService.publish(plainTask);
            TaskRun plainRun = awaitTerminal(taskRunService.run(plainTask).id());

            assertEquals(TaskRunStatus.SUCCESS, plainRun.getStatus());
            assertEquals(List.of("first|10.00", "second|20.00"), fixture.targetRows());

            fixture.insertSource("30.00", "third");
            UUID cteTask = createTask(fixture, "cte_append");
            saveDefinition(
                    cteTask,
                    fixture,
                    "WITH chosen AS (SELECT id, amount, label FROM " + fixture.sourceTable() + " WHERE amount >= 20) "
                            + "SELECT amount, label, id || '_cte' AS id FROM chosen",
                    LocalSqlWriteMode.APPEND,
                    5
            );
            dataTaskService.publish(cteTask);
            TaskRun cteRun = awaitTerminal(taskRunService.run(cteTask).id());

            assertEquals(TaskRunStatus.SUCCESS, cteRun.getStatus());
            assertEquals(List.of("first|10.00", "second|20.00", "second|20.00", "third|30.00"), fixture.targetRows());
        }
    }

    @Test
    void refusesMissingPrimaryKeyExtraAndIncompatibleOutputColumnsAtPublication() throws Exception {
        try (Fixture fixture = fixture()) {
            UUID task = createTask(fixture, "invalid_columns");

            saveDefinition(task, fixture, "SELECT amount FROM " + fixture.sourceTable(), LocalSqlWriteMode.APPEND, 5);
            assertPublicationRejected(task);

            saveDefinition(
                    task, fixture, "SELECT id, amount, label, 1 AS unexpected FROM " + fixture.sourceTable(), LocalSqlWriteMode.APPEND, 5
            );
            assertPublicationRejected(task);

            saveDefinition(
                    task, fixture, "SELECT id, label AS amount, label FROM " + fixture.sourceTable(), LocalSqlWriteMode.APPEND, 5
            );
            assertPublicationRejected(task);
        }
    }

    @Test
    void recordsRuntimeFailureTimeoutAndConcurrentRunRejection() throws Exception {
        try (Fixture fixture = fixture()) {
            fixture.installTargetTrigger();

            UUID failureTask = createTask(fixture, "runtime_failure");
            saveDefinition(failureTask, fixture, "SELECT id, amount, label FROM " + fixture.sourceTable(), LocalSqlWriteMode.APPEND, 5);
            dataTaskService.publish(failureTask);
            fixture.insertSource("10.00", "fail");
            TaskRun failed = awaitTerminal(taskRunService.run(failureTask).id());
            assertEquals(TaskRunStatus.FAILED, failed.getStatus());
            assertEquals(List.of(), fixture.targetRows());

            fixture.truncateTables();
            UUID timeoutTask = createTask(fixture, "timeout");
            saveDefinition(timeoutTask, fixture, "SELECT id, amount, label FROM " + fixture.sourceTable(), LocalSqlWriteMode.APPEND, 1);
            dataTaskService.publish(timeoutTask);
            fixture.insertSource("11.00", "slow");
            TaskRun timedOut = awaitTerminal(taskRunService.run(timeoutTask).id());
            assertEquals(TaskRunStatus.TIMED_OUT, timedOut.getStatus());
            assertNotNull(timedOut.getEndedAt());
            assertEquals(List.of(), fixture.targetRows());

            fixture.truncateTables();
            UUID concurrentTask = createTask(fixture, "concurrent");
            saveDefinition(concurrentTask, fixture, "SELECT id, amount, label FROM " + fixture.sourceTable(), LocalSqlWriteMode.APPEND, 5);
            dataTaskService.publish(concurrentTask);
            fixture.insertSource("12.00", "slow");
            TaskRunResponse first = taskRunService.run(concurrentTask);
            ResponseStatusException rejection = assertThrows(
                    ResponseStatusException.class, () -> taskRunService.run(concurrentTask)
            );
            assertEquals(HttpStatus.CONFLICT, rejection.getStatusCode());
            assertEquals(TaskRunStatus.SUCCESS, awaitTerminal(first.id()).getStatus());
        }
    }

    @Test
    void commitsPostgreSqlOverwriteAndRollsItBackWhenInsertFails() throws Exception {
        try (Fixture fixture = fixture()) {
            fixture.addTargetAmountLimit();
            fixture.insertTarget("50.00", "preserved-before-success");
            UUID task = createTask(fixture, "overwrite");
            saveDefinition(task, fixture, "SELECT id, amount, label FROM " + fixture.sourceTable(), LocalSqlWriteMode.OVERWRITE, 5);
            dataTaskService.publish(task);

            fixture.insertSource("20.00", "replaced");
            TaskRun successful = awaitTerminal(taskRunService.run(task).id());
            assertEquals(TaskRunStatus.SUCCESS, successful.getStatus());
            assertEquals(List.of("replaced|20.00"), fixture.targetRows());

            fixture.truncateTables();
            fixture.insertTarget("50.00", "must-survive");
            fixture.insertSource("200.00", "too-large");
            TaskRun failed = awaitTerminal(taskRunService.run(task).id());
            assertEquals(TaskRunStatus.FAILED, failed.getStatus());
            assertEquals(List.of("must-survive|50.00"), fixture.targetRows());
        }
    }

    @Test
    void disablesThenReenablesNewDefinitionWithoutChangingExistingRunSnapshot() throws Exception {
        try (Fixture fixture = fixture()) {
            UUID task = createTask(fixture, "definition_version");
            String originalSql = "SELECT id, amount, label FROM " + fixture.sourceTable();
            saveDefinition(task, fixture, originalSql, LocalSqlWriteMode.APPEND, 5);
            dataTaskService.publish(task);
            fixture.insertSource("10.00", "first-version");
            TaskRun first = awaitTerminal(taskRunService.run(task).id());
            assertEquals(TaskRunStatus.SUCCESS, first.getStatus());
            assertEquals(1, first.getDefinitionVersion());
            assertEquals(originalSql, readSnapshot(first).sql());
            assertFalse(first.getDefinitionSnapshot().contains("\"password\""));
            assertFalse(first.getDefinitionSnapshot().contains("\"secret\""));

            dataTaskService.disable(task);
            ResponseStatusException disabledRejection = assertThrows(
                    ResponseStatusException.class, () -> taskRunService.run(task)
            );
            assertEquals(HttpStatus.CONFLICT, disabledRejection.getStatusCode());

            String versionTwoSql = "WITH selected AS (SELECT id, amount, label FROM " + fixture.sourceTable()
                    + ") SELECT id, amount, label FROM selected";
            saveDefinition(task, fixture, versionTwoSql, LocalSqlWriteMode.APPEND, 5);
            assertEquals(2, dataTaskService.getDefinition(task).version());
            dataTaskService.enable(task);
            assertEquals(1, runRepository.findById(first.getId()).orElseThrow().getDefinitionVersion());
            assertEquals(originalSql, readSnapshot(runRepository.findById(first.getId()).orElseThrow()).sql());

            fixture.truncateTables();
            fixture.insertSource("22.00", "second-version");
            TaskRun second = awaitTerminal(taskRunService.run(task).id());
            assertEquals(TaskRunStatus.SUCCESS, second.getStatus());
            assertEquals(2, second.getDefinitionVersion());
            assertEquals(List.of("second-version|22.00"), fixture.targetRows());
        }
    }

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        DataSource source = dataSourceRepository.saveAndFlush(DataSource.create(
                "pg_task_" + suffix, "PostgreSQL 任务验收", null, Set.of(DataSourcePurpose.STORAGE),
                DataSourceType.POSTGRESQL, true, null,
                DataSourceConnection.jdbc(
                        postgres.host(), postgres.port(), postgres.databaseName(), postgres.schemaName(), postgres.username(),
                        postgres.password(), postgres.options()
                )
        ));
        DataModel input = modelRepository.saveAndFlush(DataModel.create(
                "task_input_" + suffix, "任务输入", null, source.getId(), null, null,
                "task_input_" + suffix, PhysicalTableMode.MANAGED, null
        ));
        DataModel output = modelRepository.saveAndFlush(DataModel.create(
                "task_output_" + suffix, "任务输出", null, source.getId(), null, null,
                "task_output_" + suffix, PhysicalTableMode.MANAGED, null
        ));
        List<DataModelField> inputFields = List.of(
                stringField(input.getId(), "id", 0), decimalField(input.getId(), "amount", 1),
                stringField(input.getId(), "label", 2)
        );
        // Keep the model order deliberately different from the query order. PostgreSQL itself can
        // resolve the explicit target list, and this is essential for position-based ClickHouse too.
        List<DataModelField> outputFields = List.of(
                stringField(output.getId(), "label", 0), primaryKeyStringField(output.getId(), "id", 1),
                decimalField(output.getId(), "amount", 2)
        );
        fieldRepository.saveAllAndFlush(inputFields);
        fieldRepository.saveAllAndFlush(outputFields);
        physicalTablePort.create(source, input, inputFields);
        physicalTablePort.create(source, output, outputFields);
        input.publish();
        output.publish();
        modelRepository.saveAllAndFlush(List.of(input, output));
        return new Fixture(source, input, output);
    }

    private UUID createTask(Fixture fixture, String name) {
        return dataTaskService.create(new CreateDataTaskRequest(
                name, null, TaskType.LOCAL_SQL, "PostgreSQL LOCAL_SQL 集成验收"
        )).id();
    }

    private void saveDefinition(UUID taskId, Fixture fixture, String sql, LocalSqlWriteMode mode, int timeoutSeconds) {
        dataTaskService.updateDefinition(taskId,
                new UpdateLocalSqlTaskDefinitionRequest(sql, List.of(fixture.input.getId()), fixture.output.getId(), mode, timeoutSeconds));
    }

    private void assertPublicationRejected(UUID taskId) {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> dataTaskService.publish(taskId));
        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    private TaskRun awaitTerminal(UUID runId) throws InterruptedException {
        Instant deadline = Instant.now().plus(RUN_TIMEOUT);
        TaskRun latest = null;
        while (Instant.now().isBefore(deadline)) {
            latest = runRepository.findById(runId).orElseThrow();
            if (latest.getStatus() == TaskRunStatus.SUCCESS
                    || latest.getStatus() == TaskRunStatus.FAILED
                    || latest.getStatus() == TaskRunStatus.TIMED_OUT) {
                return latest;
            }
            Thread.sleep(40);
        }
        throw new AssertionError("任务运行在 " + RUN_TIMEOUT + " 内未进入终态："
                + (latest == null ? "不存在" : latest.getStatus()));
    }

    private TaskRunDefinitionSnapshot readSnapshot(TaskRun run) {
        return objectMapper.readValue(run.getDefinitionSnapshot(), TaskRunDefinitionSnapshot.class);
    }

    private void clearManagementData() {
        runRepository.deleteAll();
        inputRepository.deleteAll();
        definitionRepository.deleteAll();
        taskRepository.deleteAll();
        fieldRepository.deleteAll();
        modelRepository.deleteAll();
        dataSourceRepository.deleteAll();
    }

    private static DataModelField decimalField(UUID modelId, String code, int order) {
        return DataModelField.create(modelId, code, code, PlatformDataType.DECIMAL, null, 18, 2, true, false, order, null);
    }

    private static DataModelField stringField(UUID modelId, String code, int order) {
        return DataModelField.create(modelId, code, code, PlatformDataType.STRING, 100, null, null, true, false, order, null);
    }

    private static DataModelField primaryKeyStringField(UUID modelId, String code, int order) {
        return DataModelField.create(modelId, code, code, PlatformDataType.STRING, 100, null, null, false, true, order, null);
    }

    private JdbcConnectionConfig integrationConfig() {
        return new JdbcConnectionConfig(
                requiredEnvironment("DATASCALPEL_PG_HOST"),
                integerEnvironment("DATASCALPEL_PG_PORT", 5432),
                requiredEnvironment("DATASCALPEL_PG_DATABASE"),
                System.getenv().getOrDefault("DATASCALPEL_PG_SCHEMA", "datascalpel_task_test"),
                requiredEnvironment("DATASCALPEL_PG_USERNAME"),
                requiredEnvironment("DATASCALPEL_PG_PASSWORD"),
                Map.of("sslmode", System.getenv().getOrDefault("DATASCALPEL_PG_SSLMODE", "disable"))
        );
    }

    private static String requiredEnvironment(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少 PostgreSQL 集成测试环境变量：" + key);
        }
        return value;
    }

    private static int integerEnvironment(String key, int defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : Integer.parseInt(value);
    }

    private void ensureSchema() throws SQLException, ClassNotFoundException {
        try (Connection connection = connectionFactory.open(cn.superhuang.data.scalpel.dialect.builtin.BuiltInDialects.registry()
                .require("POSTGRESQL").createConnectionSpec(postgres));
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS " + Fixture.quote(postgres.schemaName()));
        }
    }

    private final class Fixture implements AutoCloseable {

        private final DataSource source;
        private final DataModel input;
        private final DataModel output;
        private final String schema;
        private final String triggerFunction;

        private Fixture(DataSource source, DataModel input, DataModel output) {
            this.source = source;
            this.input = input;
            this.output = output;
            this.schema = postgres.schemaName();
            this.triggerFunction = "task_target_guard_" + suffix();
        }

        String suffix() {
            return input.getPhysicalTableName().substring("task_input_".length());
        }

        String sourceTable() {
            return qualified(input.getPhysicalTableName());
        }

        void insertSource(String amount, String label) throws SQLException, ClassNotFoundException {
            insert(sourceTable(), UUID.randomUUID().toString(), amount, label);
        }

        void insertTarget(String amount, String label) throws SQLException, ClassNotFoundException {
            insert(qualified(output.getPhysicalTableName()), UUID.randomUUID().toString(), amount, label);
        }

        void truncateTables() throws SQLException, ClassNotFoundException {
            execute("TRUNCATE TABLE " + sourceTable() + ", " + qualified(output.getPhysicalTableName()));
        }

        List<String> targetRows() throws SQLException, ClassNotFoundException {
            try (Connection connection = open();
                 Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery(
                         "SELECT \"label\", \"amount\" FROM " + qualified(output.getPhysicalTableName()) + " ORDER BY \"label\", \"amount\""
                 )) {
                List<String> rows = new ArrayList<>();
                while (resultSet.next()) {
                    rows.add(resultSet.getString(1) + "|" + resultSet.getBigDecimal(2).setScale(2).toPlainString());
                }
                return rows;
            }
        }

        void installTargetTrigger() throws SQLException, ClassNotFoundException {
            String function = qualified(triggerFunction);
            execute("CREATE FUNCTION " + function + "() RETURNS trigger LANGUAGE plpgsql AS $$ "
                    + "BEGIN "
                    + "IF NEW.\"label\" = 'fail' THEN RAISE EXCEPTION 'test target insert failure'; END IF; "
                    + "IF NEW.\"label\" = 'slow' THEN PERFORM pg_sleep(2); END IF; "
                    + "RETURN NEW; END; $$");
            execute("CREATE TRIGGER " + quote("task_target_trigger_" + suffix()) + " BEFORE INSERT ON "
                    + qualified(output.getPhysicalTableName()) + " FOR EACH ROW EXECUTE FUNCTION " + function + "()");
        }

        void addTargetAmountLimit() throws SQLException, ClassNotFoundException {
            execute("ALTER TABLE " + qualified(output.getPhysicalTableName()) + " ADD CONSTRAINT "
                    + quote("task_target_amount_limit_" + suffix()) + " CHECK (\"amount\" < 100)");
        }

        @Override
        public void close() throws Exception {
            try {
                execute("DROP TABLE IF EXISTS " + sourceTable());
                execute("DROP TABLE IF EXISTS " + qualified(output.getPhysicalTableName()));
                execute("DROP FUNCTION IF EXISTS " + qualified(triggerFunction) + "()");
            } catch (SQLException | ClassNotFoundException exception) {
                throw new AssertionError("无法清理 PostgreSQL 本地 SQL 任务测试表", exception);
            }
        }

        private void insert(String table, String id, String amount, String label) throws SQLException, ClassNotFoundException {
            try (Connection connection = open();
                 PreparedStatement statement = connection.prepareStatement(
                         "INSERT INTO " + table + " (\"id\", \"amount\", \"label\") VALUES (?, ?, ?)"
                 )) {
                statement.setString(1, id);
                statement.setBigDecimal(2, new BigDecimal(amount));
                statement.setString(3, label);
                statement.executeUpdate();
            }
        }

        private void execute(String sql) throws SQLException, ClassNotFoundException {
            try (Connection connection = open(); Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
        }

        private Connection open() throws SQLException, ClassNotFoundException {
            return connectionFactory.open(cn.superhuang.data.scalpel.dialect.builtin.BuiltInDialects.registry()
                    .require("POSTGRESQL").createConnectionSpec(postgres));
        }

        private String qualified(String table) {
            return quote(schema) + "." + quote(table);
        }

        private static String quote(String value) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
    }
}
