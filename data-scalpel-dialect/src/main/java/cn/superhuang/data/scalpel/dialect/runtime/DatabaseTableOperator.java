package cn.superhuang.data.scalpel.dialect.runtime;

import cn.superhuang.data.scalpel.dialect.api.DatabaseCapability;
import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionConfig;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionFactory;
import cn.superhuang.data.scalpel.dialect.model.DdlPlan;
import cn.superhuang.data.scalpel.dialect.model.TableChangeCheck;
import cn.superhuang.data.scalpel.dialect.model.TableChangeCheckType;
import cn.superhuang.data.scalpel.dialect.model.TableChangeExecutionMode;
import cn.superhuang.data.scalpel.dialect.model.TableChangeExecutionOption;
import cn.superhuang.data.scalpel.dialect.model.TableChangePlan;
import cn.superhuang.data.scalpel.dialect.model.TableDdlAtomicity;
import cn.superhuang.data.scalpel.dialect.model.TableDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableMetadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** Executes only DDL rendered from a {@link TableDefinition}; it never accepts user SQL. */
public class DatabaseTableOperator {

    private final DialectRegistry registry;
    private final JdbcConnectionFactory connectionFactory;

    public DatabaseTableOperator(DialectRegistry registry, JdbcConnectionFactory connectionFactory) {
        this.registry = registry;
        this.connectionFactory = connectionFactory;
    }

    public DdlPlan planCreateTable(String databaseType, TableDefinition definition) {
        DatabaseDialect dialect = registry.require(databaseType);
        requireCreateTableCapability(dialect);
        return dialect.planCreateTable(definition);
    }

    public DdlPlan planCreateTable(
            String databaseType,
            JdbcConnectionConfig config,
            TableDefinition definition
    ) {
        DatabaseDialect dialect = registry.require(databaseType);
        requireCreateTableCapability(dialect);
        try (Connection connection = connectionFactory.open(dialect.createConnectionSpec(config))) {
            try {
                return dialect.planCreateTable(connection, definition);
            } catch (IllegalArgumentException exception) {
                throw new DatabaseAccessException("DDL_NOT_SUPPORTED", exception.getMessage(), exception);
            }
        } catch (DatabaseAccessException exception) {
            throw exception;
        } catch (ClassNotFoundException | LinkageError exception) {
            throw new DatabaseAccessException("DRIVER_NOT_AVAILABLE", "数据库驱动未安装", exception);
        } catch (IllegalArgumentException exception) {
            throw new DatabaseAccessException("INVALID_CONNECTION_CONFIG", exception.getMessage(), exception);
        } catch (SQLException exception) {
            throw new DatabaseAccessException(errorCode(exception), safeMessage(exception), exception);
        }
    }

    /**
     * Reads the physical structure and lets a dialect inspect connection-scoped runtime settings
     * before returning a controlled table-change plan.
     */
    public TableChangePlan planTableChange(
            String databaseType,
            JdbcConnectionConfig config,
            TableDefinition before,
            TableDefinition target
    ) {
        DatabaseDialect dialect = registry.require(databaseType);
        try (Connection connection = connectionFactory.open(dialect.createConnectionSpec(config))) {
            TableMetadata actual = new JdbcMetadataReader(dialect).readTable(connection, before.table());
            return dialect.planTableChange(connection, before, target, actual);
        } catch (DatabaseAccessException exception) {
            throw exception;
        } catch (ClassNotFoundException | LinkageError exception) {
            throw new DatabaseAccessException("DRIVER_NOT_AVAILABLE", "数据库驱动未安装", exception);
        } catch (IllegalArgumentException exception) {
            throw new DatabaseAccessException("INVALID_CONNECTION_CONFIG", exception.getMessage(), exception);
        } catch (SQLException exception) {
            throw new DatabaseAccessException(errorCode(exception), safeChangeMessage(exception), exception);
        }
    }

    public void createTable(String databaseType, JdbcConnectionConfig config, TableDefinition definition) {
        DatabaseDialect dialect = registry.require(databaseType);
        requireCreateTableCapability(dialect);
        try (Connection connection = connectionFactory.open(dialect.createConnectionSpec(config))) {
            DdlPlan plan;
            try {
                plan = dialect.planCreateTable(connection, definition);
            } catch (IllegalArgumentException exception) {
                throw new DatabaseAccessException("DDL_NOT_SUPPORTED", exception.getMessage(), exception);
            }
            try (Statement statement = connection.createStatement()) {
                try {
                    statement.setQueryTimeout(20);
                } catch (SQLException ignored) {
                    // A few JDBC drivers do not expose statement timeouts.
                }
                for (String sql : plan.statements()) {
                    statement.execute(sql);
                }
            }
        } catch (ClassNotFoundException | LinkageError exception) {
            throw new DatabaseAccessException("DRIVER_NOT_AVAILABLE", "数据库驱动未安装", exception);
        } catch (IllegalArgumentException exception) {
            throw new DatabaseAccessException("INVALID_CONNECTION_CONFIG", exception.getMessage(), exception);
        } catch (SQLException exception) {
            throw new DatabaseAccessException(errorCode(exception), safeMessage(exception), exception);
        }
    }

    /**
     * Executes a dialect-rendered in-place change inside the target database transaction and verifies both endpoints.
     */
    public void executeTableChange(
            String databaseType,
            JdbcConnectionConfig config,
            TableChangePlan plan,
            TableChangeExecutionMode mode
    ) {
        DatabaseDialect dialect = registry.require(databaseType);
        TableChangeExecutionOption option;
        try {
            option = plan.requireExecutionOption(mode);
        } catch (IllegalArgumentException exception) {
            throw new DatabaseAccessException("EXECUTION_MODE_NOT_AVAILABLE", "当前计划不提供所选执行方式", exception);
        }
        if (option.atomicity() == TableDdlAtomicity.ATOMIC_SINGLE_STATEMENT) {
            executeAtomicSingleStatement(dialect, config, plan, option);
            return;
        }
        if (option.atomicity() != TableDdlAtomicity.TRANSACTIONAL_BATCH) {
            throw new DatabaseAccessException(
                    "DDL_ATOMICITY_NOT_SUPPORTED", "当前执行器仅支持事务型物理表变更", null
            );
        }

        try (Connection connection = connectionFactory.open(dialect.createConnectionSpec(config))) {
            boolean originalAutoCommit = connection.getAutoCommit();
            try {
                connection.setAutoCommit(false);
                JdbcMetadataReader metadataReader = new JdbcMetadataReader(dialect);
                TableMetadata before = metadataReader.readTable(connection, plan.before().table());
                verifyStructure(dialect, plan, before);
                verifyChecks(connection, dialect, plan, before);

                try (Statement statement = connection.createStatement()) {
                    try {
                        statement.setQueryTimeout(mode == TableChangeExecutionMode.REBUILD ? 300 : 30);
                    } catch (SQLException ignored) {
                        // Some JDBC drivers do not expose statement-level timeouts.
                    }
                    for (String sql : option.statements()) {
                        statement.execute(sql);
                    }
                }

                TableMetadata after = metadataReader.readTable(connection, plan.target().table());
                if (!dialect.compareTable(plan.target(), after).compatible()) {
                    throw new DatabaseAccessException("POSTCHECK_FAILED", "物理表变更后的结构未达到目标定义，已回滚", null);
                }
                connection.commit();
            } catch (DatabaseAccessException exception) {
                rollback(connection);
                throw exception;
            } catch (SQLException exception) {
                rollback(connection);
                throw new DatabaseAccessException(errorCode(exception), safeChangeMessage(exception), exception);
            } catch (IllegalArgumentException exception) {
                rollback(connection);
                throw new DatabaseAccessException("INVALID_CHANGE_PLAN", exception.getMessage(), exception);
            } finally {
                restoreAutoCommit(connection, originalAutoCommit);
            }
        } catch (ClassNotFoundException | LinkageError exception) {
            throw new DatabaseAccessException("DRIVER_NOT_AVAILABLE", "数据库驱动未安装", exception);
        } catch (IllegalArgumentException exception) {
            throw new DatabaseAccessException("INVALID_CONNECTION_CONFIG", exception.getMessage(), exception);
        } catch (SQLException exception) {
            throw new DatabaseAccessException(errorCode(exception), safeChangeMessage(exception), exception);
        }
    }

    private void executeAtomicSingleStatement(
            DatabaseDialect dialect,
            JdbcConnectionConfig config,
            TableChangePlan plan,
            TableChangeExecutionOption option
    ) {
        if (option.statements().size() != 1) {
            throw new DatabaseAccessException(
                    "INVALID_CHANGE_PLAN", "原子单语句执行方式只能包含一条 DDL", null
            );
        }
        try (Connection connection = connectionFactory.open(dialect.createConnectionSpec(config))) {
            JdbcMetadataReader metadataReader = new JdbcMetadataReader(dialect);
            TableMetadata before = metadataReader.readTable(connection, plan.before().table());
            verifyStructure(dialect, plan, before);
            verifyChecks(connection, dialect, plan, before);
            try (Statement statement = connection.createStatement()) {
                try {
                    statement.setQueryTimeout(30);
                } catch (SQLException ignored) {
                    // Some JDBC drivers do not expose statement-level timeouts.
                }
                statement.execute(option.statements().getFirst());
            }
            TableMetadata after = metadataReader.readTable(connection, plan.target().table());
            if (!dialect.compareTable(plan.target(), after).compatible()) {
                throw new DatabaseAccessException(
                        "POSTCHECK_FAILED", "物理表变更后的结构未达到目标定义，需要人工核对", null
                );
            }
        } catch (DatabaseAccessException exception) {
            throw exception;
        } catch (ClassNotFoundException | LinkageError exception) {
            throw new DatabaseAccessException("DRIVER_NOT_AVAILABLE", "数据库驱动未安装", exception);
        } catch (IllegalArgumentException exception) {
            throw new DatabaseAccessException("INVALID_CONNECTION_CONFIG", exception.getMessage(), exception);
        } catch (SQLException exception) {
            throw new DatabaseAccessException(errorCode(exception), safeChangeMessage(exception), exception);
        }
    }

    private static void verifyStructure(DatabaseDialect dialect, TableChangePlan plan, TableMetadata before) {
        if (!dialect.compareTable(plan.before(), before).compatible()
                || !dialect.snapshotTableDefinition(before).structureFingerprint().equals(plan.beforeFingerprint())) {
            throw new DatabaseAccessException("TABLE_STRUCTURE_DRIFTED", "物理表结构已变化，请重新生成变更计划", null);
        }
    }

    private static void verifyChecks(
            Connection connection,
            DatabaseDialect dialect,
            TableChangePlan plan,
            TableMetadata before
    ) throws SQLException {
        for (TableChangeCheck check : plan.checks()) {
            boolean passed;
            if (check.type() == TableChangeCheckType.STRUCTURE_FINGERPRINT_MATCH) {
                passed = dialect.snapshotTableDefinition(before).structureFingerprint().equals(check.expectedFingerprint());
            } else {
                passed = dialect.checkTableChange(connection, plan.before().table(), check);
            }
            if (!passed) {
                throw new DatabaseAccessException("PRECHECK_FAILED", "执行前检查未通过：" + check.description(), null);
            }
        }
    }

    private static void rollback(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // The original exception remains more useful to the caller.
        }
    }

    private static void restoreAutoCommit(Connection connection, boolean originalAutoCommit) {
        try {
            connection.setAutoCommit(originalAutoCommit);
        } catch (SQLException ignored) {
            // The connection is about to be closed.
        }
    }

    private static void requireCreateTableCapability(DatabaseDialect dialect) {
        if (!dialect.definition().capabilities().contains(DatabaseCapability.CREATE_TABLE)) {
            throw new DatabaseAccessException(
                    "DDL_NOT_SUPPORTED",
                    dialect.definition().displayName() + "暂不支持由平台创建物理表",
                    null
            );
        }
    }

    private static String errorCode(SQLException exception) {
        String state = exception.getSQLState();
        if (state != null && state.startsWith("28")) return "AUTHENTICATION_FAILED";
        if (state != null && state.startsWith("3D")) return "DATABASE_NOT_FOUND";
        if (state != null && state.startsWith("08")) return "NETWORK_ERROR";
        if (state != null && (state.equals("42P07") || state.equals("42S01"))) return "TABLE_ALREADY_EXISTS";
        return "DATABASE_ERROR";
    }

    private static String safeMessage(SQLException exception) {
        return switch (errorCode(exception)) {
            case "AUTHENTICATION_FAILED" -> "认证失败，请检查用户名和密码";
            case "DATABASE_NOT_FOUND" -> "数据库不存在或当前用户无权访问";
            case "NETWORK_ERROR" -> "连接失败，请检查主机、端口和网络";
            case "TABLE_ALREADY_EXISTS" -> "物理表已存在";
            default -> "执行建表失败（SQLState: " + (exception.getSQLState() == null ? "未知" : exception.getSQLState()) + "）";
        };
    }

    private static String safeChangeMessage(SQLException exception) {
        return "执行物理表变更失败（SQLState: "
                + (exception.getSQLState() == null ? "未知" : exception.getSQLState()) + "）";
    }
}
