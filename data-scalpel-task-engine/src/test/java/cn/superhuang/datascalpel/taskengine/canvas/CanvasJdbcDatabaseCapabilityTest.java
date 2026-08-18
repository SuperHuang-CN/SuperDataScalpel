package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasJdbcDatabaseType;
import cn.superhuang.data.scalpel.contract.task.JdbcWriteMode;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.CrsReference;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanvasJdbcDatabaseCapabilityTest {

    @Test
    void overwriteKeepsExistingDatabasesAndRejectsNewDatabases() {
        for (CanvasJdbcDatabaseType type : List.of(
                CanvasJdbcDatabaseType.POSTGRESQL,
                CanvasJdbcDatabaseType.MYSQL,
                CanvasJdbcDatabaseType.OPENGAUSS,
                CanvasJdbcDatabaseType.KINGBASE
        )) {
            RecordingIssues issues = new RecordingIssues();
            CanvasNodeSupport.validateJdbcWriteMode(
                    JdbcWriteMode.OVERWRITE, type, "configuration.writeMode", issues);
            assertFalse(issues.hasErrors(), type.name());
        }
        for (CanvasJdbcDatabaseType type : List.of(
                CanvasJdbcDatabaseType.ORACLE,
                CanvasJdbcDatabaseType.SQL_SERVER,
                CanvasJdbcDatabaseType.CLICKHOUSE,
                CanvasJdbcDatabaseType.DAMENG
        )) {
            RecordingIssues issues = new RecordingIssues();
            CanvasNodeSupport.validateJdbcWriteMode(
                    JdbcWriteMode.OVERWRITE, type, "configuration.writeMode", issues);
            assertTrue(issues.codes.contains("OVERWRITE_DATABASE_NOT_SUPPORTED"), type.name());
        }
    }

    @Test
    void newDatabasesAllowScalarColumnsButRejectGeometry() {
        CanvasColumnSchema scalar = new CanvasColumnSchema(
                "id", PlatformDataType.LONG, null, null, null,
                false, null, false, false, null
        );
        CanvasColumnSchema geometry = new CanvasColumnSchema(
                "geom", PlatformDataType.GEOMETRY, null, null, null,
                true, null, false, false, null,
                new GeometryTypeDefinition(
                        GeometryKind.POINT,
                        CrsReference.epsg(4326),
                        CoordinateDimension.XY
                )
        );
        for (CanvasJdbcDatabaseType type : List.of(
                CanvasJdbcDatabaseType.ORACLE,
                CanvasJdbcDatabaseType.SQL_SERVER,
                CanvasJdbcDatabaseType.CLICKHOUSE,
                CanvasJdbcDatabaseType.DAMENG
        )) {
            RecordingIssues scalarIssues = new RecordingIssues();
            CanvasNodeSupport.validateJdbcGeometryDatabase(
                    List.of(scalar), type, "columns", scalarIssues);
            assertFalse(scalarIssues.hasErrors(), type.name());

            RecordingIssues geometryIssues = new RecordingIssues();
            CanvasNodeSupport.validateJdbcGeometryDatabase(
                    List.of(geometry), type, "columns", geometryIssues);
            assertTrue(geometryIssues.codes.contains("SPATIAL_JDBC_UNSUPPORTED"), type.name());
        }
    }

    private static final class RecordingIssues implements CanvasNodeIssueSink {
        private final List<String> codes = new ArrayList<>();

        @Override
        public void error(String code, String message, String path) {
            codes.add(code);
        }

        @Override
        public void warning(String code, String message, String path) {
        }

        @Override
        public boolean hasErrors() {
            return !codes.isEmpty();
        }
    }
}
