package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceConnection;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourcePurpose;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceType;
import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.business.model.service.ModelPhysicalTablePort;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.CrsReference;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.business.model.domain.PhysicalTableMode;
import cn.superhuang.data.scalpel.business.task.domain.LocalSqlWriteMode;
import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;
import cn.superhuang.data.scalpel.dialect.builtin.BuiltInDialects;
import cn.superhuang.data.scalpel.dialect.model.LogicalType;
import cn.superhuang.data.scalpel.dialect.query.QueryColumn;
import cn.superhuang.data.scalpel.dialect.query.QueryInspection;
import cn.superhuang.data.scalpel.dialect.query.ReadOnlySelectQueryParser;
import cn.superhuang.data.scalpel.dialect.runtime.JdbcQueryInspector;
import org.junit.jupiter.api.Test;

import java.sql.Types;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class JdbcLocalSqlDefinitionInspectionPortTest {

    private static final DatabaseDialect POSTGRESQL = BuiltInDialects.registry().require("POSTGRESQL");

    @Test
    void acceptsAnOutputSubsetWhenItContainsThePrimaryKey() {
        LocalSqlDefinitionInspection inspection = inspect(
                outputFields(true),
                List.of(stringColumn("id", false), stringColumn("name", true))
        );

        assertTrue(inspection.valid());
        assertEquals(List.of("id", "name"), inspection.targetColumns());
        assertEquals(
                "INSERT INTO \"public\".\"target_user\" (\"id\", \"name\") SELECT id, name FROM source_user",
                inspection.generatedInsertSql()
        );
    }

    @Test
    void rejectsMissingPrimaryKeysAndColumnsOutsideTheOutputModel() {
        LocalSqlDefinitionInspection inspection = inspect(
                outputFields(true),
                List.of(stringColumn("name", true), stringColumn("unexpected", true))
        );

        assertFalse(inspection.valid());
        assertEquals(
                Set.of("UNEXPECTED_OUTPUT_COLUMN", "MISSING_PRIMARY_KEY_COLUMN"),
                inspection.problems().stream().map(LocalSqlDefinitionInspectionProblem::code).collect(java.util.stream.Collectors.toSet())
        );
        assertFalse(inspection.problems().stream().anyMatch(problem -> problem.code().equals("MISSING_OUTPUT_COLUMN")));
    }

    @Test
    void rejectsAnOutputModelWithoutAPrimaryKey() {
        LocalSqlDefinitionInspection inspection = inspect(
                outputFields(false),
                List.of(stringColumn("id", false), stringColumn("name", true))
        );

        assertFalse(inspection.valid());
        assertEquals(
                List.of("OUTPUT_MODEL_PRIMARY_KEY_REQUIRED"),
                inspection.problems().stream().map(LocalSqlDefinitionInspectionProblem::code).toList()
        );
    }

    @Test
    void rejectsGeometryModelsBeforeInspectingSql() {
        DataModel geometryModel = outputModel();
        DataModelField geometryField = DataModelField.create(
                UUID.randomUUID(),
                "shape",
                "空间位置",
                PlatformDataType.GEOMETRY,
                null,
                null,
                null,
                new GeometryTypeDefinition(
                        GeometryKind.POINT,
                        CrsReference.epsg(4326),
                        CoordinateDimension.XY
                ),
                true,
                false,
                0,
                null
        );
        JdbcLocalSqlDefinitionInspectionPort port = new JdbcLocalSqlDefinitionInspectionPort(
                new DialectRegistry(List.of(POSTGRESQL)),
                mock(JdbcQueryInspector.class),
                mock(ModelPhysicalTablePort.class)
        );

        LocalSqlDefinitionInspection inspection = port.inspect(new LocalSqlDefinitionInspectionRequest(
                dataSource(),
                List.of(),
                new LocalSqlDefinitionInspectionRequest.ModelWithFields(
                        geometryModel,
                        List.of(geometryField)
                ),
                "SELECT shape FROM source_user",
                LocalSqlWriteMode.APPEND,
                Duration.ofSeconds(30)
        ));

        assertFalse(inspection.valid());
        assertEquals(
                List.of("SPATIAL_FIELD_UNSUPPORTED"),
                inspection.problems().stream().map(LocalSqlDefinitionInspectionProblem::code).toList()
        );
    }

    private static LocalSqlDefinitionInspection inspect(
            List<DataModelField> outputFields,
            List<QueryColumn> queryColumns
    ) {
        LocalSqlDefinitionInspectionRequest request = new LocalSqlDefinitionInspectionRequest(
                dataSource(),
                List.of(),
                new LocalSqlDefinitionInspectionRequest.ModelWithFields(outputModel(), outputFields),
                "SELECT id, name FROM source_user",
                LocalSqlWriteMode.APPEND,
                Duration.ofSeconds(30)
        );
        return JdbcLocalSqlDefinitionInspectionPort.compareOutput(
                POSTGRESQL,
                request,
                ReadOnlySelectQueryParser.parse(request.sql()),
                new QueryInspection(queryColumns)
        );
    }

    private static DataSource dataSource() {
        return DataSource.create(
                "task_storage", "任务存储", null, Set.of(DataSourcePurpose.STORAGE),
                DataSourceType.POSTGRESQL, true, null,
                DataSourceConnection.jdbc("localhost", 5432, "business", "public", "reader", "secret", Map.of())
        );
    }

    private static DataModel outputModel() {
        return DataModel.create(
                "target_user", "目标用户", null, UUID.randomUUID(), null, "public", "target_user",
                PhysicalTableMode.MANAGED, null
        );
    }

    private static List<DataModelField> outputFields(boolean withPrimaryKey) {
        UUID modelId = UUID.randomUUID();
        return List.of(
                DataModelField.create(
                        modelId, "id", "主键", PlatformDataType.STRING, 64, null, null,
                        false, withPrimaryKey, 0, null
                ),
                DataModelField.create(
                        modelId, "name", "名称", PlatformDataType.STRING, 100, null, null,
                        true, false, 1, null
                ),
                DataModelField.create(
                        modelId, "age", "年龄", PlatformDataType.INTEGER, null, null, null,
                        true, false, 2, null
                )
        );
    }

    private static QueryColumn stringColumn(String label, boolean nullable) {
        return new QueryColumn(label, Types.VARCHAR, "varchar", LogicalType.STRING, nullable);
    }
}
