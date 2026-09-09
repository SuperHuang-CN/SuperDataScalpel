package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.GeometrySimplifyConfiguration;
import cn.superhuang.data.scalpel.contract.task.GeometrySimplifyNodeDefinition;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.sedona_sql.expressions.st_functions;
import org.apache.spark.sql.api.java.UDF1;
import org.locationtech.jts.geom.Geometry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class GeometrySimplifyNodeOperator implements CanvasNodeOperator {

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.GEOMETRY_SIMPLIFY;
    }

    @Override
    public CanvasNodeCategory category() {
        return CanvasNodeCategory.PROCESSOR;
    }

    @Override
    public Set<CanvasExecutionMode> supportedModes() {
        return Set.of(CanvasExecutionMode.BATCH, CanvasExecutionMode.STREAMING);
    }

    @Override
    public CanvasNodeOperationResult apply(
            CanvasNodeDefinition definition,
            Map<String, SparkCanvasTable> inputs,
            CanvasNodeOperationContext context
    ) {
        if (!(definition instanceof GeometrySimplifyNodeDefinition node)) {
            throw new IllegalArgumentException(
                    "GEOMETRY_SIMPLIFY operator received " + definition.nodeType());
        }
        List<CanvasTableSchema> inputSchemas = CanvasNodeSupport.schemas(inputs);
        GeometrySimplifyConfiguration configuration = node.configuration();
        if (configuration == null) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        CanvasNodeIssueSink issues = context.issues();
        CanvasNodeSupport.required(configuration.sourceTableName(), "请选择来源表",
                "configuration.sourceTableName", issues);
        CanvasNodeSupport.required(configuration.geometryColumnName(), "请选择 Geometry 字段",
                "configuration.geometryColumnName", issues);
        CanvasNodeSupport.required(configuration.outputTableName(), "请输入输出表名",
                "configuration.outputTableName", issues);
        CanvasNodeSupport.required(configuration.outputColumnName(), "请输入简化结果字段名",
                "configuration.outputColumnName", issues);
        if (configuration.algorithm() == null) {
            issues.error("REQUIRED_CONFIGURATION", "请选择简化算法", "configuration.algorithm");
        }
        if (configuration.tolerance() == null || !Double.isFinite(configuration.tolerance()) || configuration.tolerance() <= 0) {
            issues.error("INVALID_GEOMETRY_SIMPLIFY_TOLERANCE",
                    "简化容差必须是有限正数", "configuration.tolerance");
        }
        if (configuration.toleranceUnit() == null) {
            issues.error("REQUIRED_CONFIGURATION", "请选择容差单位",
                    "configuration.toleranceUnit");
        }
        if (!CanvasNodeSupport.blank(configuration.outputTableName())
                && inputs.containsKey(configuration.outputTableName())) {
            issues.error("DUPLICATE_TABLE_NAME",
                    "输出表名已存在：" + configuration.outputTableName(),
                    "configuration.outputTableName");
        }

        SparkCanvasTable source = CanvasNodeSupport.blank(configuration.sourceTableName())
                ? null : inputs.get(configuration.sourceTableName());
        if (!CanvasNodeSupport.blank(configuration.sourceTableName()) && source == null) {
            issues.error("TABLE_NOT_FOUND",
                    "来源表不在上游数据中：" + configuration.sourceTableName(),
                    "configuration.sourceTableName");
        }

        Map<String, CanvasColumnSchema> sourceColumns = source == null
                ? Map.of() : CanvasNodeSupport.columns(source.schema());
        CanvasColumnSchema geometryColumn = CanvasNodeSupport.blank(configuration.geometryColumnName())
                ? null : sourceColumns.get(configuration.geometryColumnName());
        if (source != null && !CanvasNodeSupport.blank(configuration.geometryColumnName())) {
            if (geometryColumn == null) {
                issues.error("COLUMN_NOT_FOUND",
                        "Geometry 字段不存在：" + configuration.geometryColumnName(),
                        "configuration.geometryColumnName");
            } else if (geometryColumn.fieldType() != PlatformDataType.GEOMETRY) {
                issues.error("GEOMETRY_FIELD_OPERATION_UNSUPPORTED",
                        "所选字段不是 Geometry：" + configuration.geometryColumnName(),
                        "configuration.geometryColumnName");
            } else {
                if (UnaryGeometrySupport.checked(configuration.geometryPolicy()))
                    UnaryGeometrySupport.validateSource(geometryColumn, "configuration.geometryColumnName", issues);
                else CanvasNodeSupport.validateSupportedGeometry(List.of(geometryColumn), "configuration.geometryColumnName", issues);
                UnaryGeometrySupport.validateSimplify(geometryColumn.geometry(), configuration.geometryPolicy(), configuration.algorithm(), issues);
            }
        }
        if (!CanvasNodeSupport.blank(configuration.outputColumnName())) {
            String outputName = configuration.outputColumnName().toLowerCase(Locale.ROOT);
            if (sourceColumns.keySet().stream()
                    .map(name -> name.toLowerCase(Locale.ROOT))
                    .anyMatch(outputName::equals)) {
                issues.error("DUPLICATE_COLUMN_NAME",
                        "输出字段名重复：" + configuration.outputColumnName(),
                        "configuration.outputColumnName");
            }
        }

        SpatialDistanceSupport.Resolution tolerance = null;
        if (geometryColumn != null && geometryColumn.geometry() != null
                && configuration.toleranceUnit() != null
                && configuration.tolerance() != null
                && Double.isFinite(configuration.tolerance()) && configuration.tolerance() > 0) {
            tolerance = SpatialDistanceSupport.resolve(
                    configuration.tolerance(),
                    configuration.toleranceUnit(),
                    geometryColumn.geometry().crs());
            if (!tolerance.valid()) {
                issues.error("SPATIAL_DISTANCE_UNIT_UNSUPPORTED", tolerance.error() == null ? "容差换算后不是有限数值，请调整容差或单位" : tolerance.error(),
                        "configuration.toleranceUnit");
            } else if (tolerance.sourceCrsValue() <= 0) {
                issues.error("INVALID_GEOMETRY_SIMPLIFY_TOLERANCE",
                        "容差换算后必须仍为正数，请增大容差或调整单位", "configuration.tolerance");
            } else if (tolerance.angular()) {
                issues.warning("GEOMETRY_SIMPLIFY_USES_ANGULAR_UNITS",
                        "简化容差使用来源 CRS 的角度单位，结果随纬度变化；不会自动换算为米",
                        "configuration.toleranceUnit");
            }
        }
        if (source == null || geometryColumn == null || tolerance == null
                || !tolerance.valid() || issues.hasErrors()) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        GeometryTypeDefinition sourceGeometry = geometryColumn.geometry();
        Dataset<Row> sourceDataset = source.dataset();
        Column geometry = sourceDataset.col(
                CanvasNodeSupport.quoteIdentifier(configuration.geometryColumnName()));
        Column toleranceLiteral = functions.lit(tolerance.sourceCrsValue());
        Column simplified = switch (configuration.algorithm()) {
            case DOUGLAS_PEUCKER -> st_functions.ST_Simplify(geometry, toleranceLiteral);
            case TOPOLOGY_PRESERVING ->
                    st_functions.ST_SimplifyPreserveTopology(geometry, toleranceLiteral);
        };
        var policy = configuration.geometryPolicy();
        if (UnaryGeometrySupport.checked(policy)) {
            var algorithm = configuration.algorithm();
            double resolvedTolerance = tolerance.sourceCrsValue();
            var dimension = sourceGeometry.dimension();
            simplified = functions.udf((UDF1<Geometry, Geometry>) input -> UnaryGeometrySupport.simplify(input, algorithm,
                    resolvedTolerance, policy, dimension), sourceDataset.schema().apply(configuration.geometryColumnName()).dataType()).apply(geometry);
        }
        Column normalized = st_functions.ST_SetSRID(
                simplified,
                functions.lit(sourceGeometry.crs().code())
        ).alias(configuration.outputColumnName());
        List<Column> projection = new ArrayList<>(source.schema().columns().size() + 1);
        for (CanvasColumnSchema column : source.schema().columns()) {
            projection.add(sourceDataset.col(CanvasNodeSupport.quoteIdentifier(column.name())));
        }
        projection.add(normalized);

        List<CanvasColumnSchema> outputColumns = new ArrayList<>(source.schema().columns());
        outputColumns.add(new CanvasColumnSchema(
                configuration.outputColumnName(),
                PlatformDataType.GEOMETRY,
                null,
                null,
                null,
                geometryColumn.nullable(),
                null,
                false,
                false,
                null,
                new GeometryTypeDefinition(
                        GeometryKind.GEOMETRY,
                        sourceGeometry.crs(),
                        UnaryGeometrySupport.dimension(policy, sourceGeometry.dimension())
                )
        ));
        CanvasTableSchema outputSchema = new CanvasTableSchema(
                configuration.outputTableName(),
                null,
                outputColumns,
                source.schema().datasetKind(),
                source.schema().eventTimeColumn(),
                source.schema().watermarkDelay()
        );
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>(inputs);
        output.put(outputSchema.name(), new SparkCanvasTable(
                outputSchema,
                sourceDataset.select(projection.toArray(Column[]::new))
        ));
        return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
    }
}
