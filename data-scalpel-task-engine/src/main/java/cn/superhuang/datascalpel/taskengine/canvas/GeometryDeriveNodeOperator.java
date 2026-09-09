package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.GeometryDerivation;
import cn.superhuang.data.scalpel.contract.task.GeometryDeriveConfiguration;
import cn.superhuang.data.scalpel.contract.task.GeometryDeriveNodeDefinition;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class GeometryDeriveNodeOperator implements CanvasNodeOperator {

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.GEOMETRY_DERIVE;
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
        if (!(definition instanceof GeometryDeriveNodeDefinition node)) {
            throw new IllegalArgumentException(
                    "GEOMETRY_DERIVE operator received " + definition.nodeType());
        }
        List<CanvasTableSchema> inputSchemas = CanvasNodeSupport.schemas(inputs);
        GeometryDeriveConfiguration configuration = node.configuration();
        if (configuration == null) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        CanvasNodeIssueSink issues = context.issues();
        CanvasNodeSupport.required(configuration.sourceTableName(), "请选择来源表",
                "configuration.sourceTableName", issues);
        CanvasNodeSupport.required(configuration.outputTableName(), "请输入输出表名",
                "configuration.outputTableName", issues);
        if (!CanvasNodeSupport.blank(configuration.outputTableName())
                && inputs.containsKey(configuration.outputTableName())) {
            issues.error(
                    "DUPLICATE_TABLE_NAME",
                    "输出表名已存在：" + configuration.outputTableName(),
                    "configuration.outputTableName"
            );
        }

        SparkCanvasTable source = CanvasNodeSupport.blank(configuration.sourceTableName())
                ? null : inputs.get(configuration.sourceTableName());
        if (!CanvasNodeSupport.blank(configuration.sourceTableName()) && source == null) {
            issues.error(
                    "TABLE_NOT_FOUND",
                    "来源表不在上游数据中：" + configuration.sourceTableName(),
                    "configuration.sourceTableName"
            );
        }

        List<GeometryDerivation> derivations = configuration.derivations();
        if (derivations == null) {
            issues.error(
                    "GEOMETRY_DERIVATIONS_REQUIRED",
                    "Geometry 派生项必须是数组",
                    "configuration.derivations"
            );
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }
        if (derivations.isEmpty()) {
            issues.error(
                    "GEOMETRY_DERIVATIONS_REQUIRED",
                    "至少配置一个 Geometry 派生项",
                    "configuration.derivations"
            );
        }
        if (derivations.size() > GeometryDeriveConfiguration.MAX_DERIVATIONS) {
            issues.error(
                    "GEOMETRY_DERIVATION_COUNT_EXCEEDED",
                    "Geometry 派生项不能超过 "
                            + GeometryDeriveConfiguration.MAX_DERIVATIONS + " 项",
                    "configuration.derivations"
            );
        }

        Map<String, CanvasColumnSchema> sourceColumns = source == null
                ? Map.of() : CanvasNodeSupport.columns(source.schema());
        Set<String> outputNames = new HashSet<>();
        sourceColumns.keySet().stream()
                .map(name -> name.toLowerCase(Locale.ROOT))
                .forEach(outputNames::add);
        Set<UUID> derivationIds = new HashSet<>();
        for (int index = 0; index < derivations.size(); index++) {
            GeometryDerivation derivation = derivations.get(index);
            String path = "configuration.derivations[" + index + "]";
            if (derivation == null) {
                issues.error("INVALID_GEOMETRY_DERIVATION", "Geometry 派生项不能为空", path);
                continue;
            }
            validateDerivationId(derivation.derivationId(), path + ".derivationId",
                    derivationIds, issues);
            if (derivation.kind() == null) {
                issues.error(
                        "INVALID_GEOMETRY_DERIVATION",
                        "请选择 Geometry 派生类型",
                        path + ".kind"
                );
            }
            CanvasNodeSupport.required(
                    derivation.sourceColumnName(), "请选择来源 Geometry 字段",
                    path + ".sourceColumnName", issues);
            CanvasNodeSupport.required(
                    derivation.outputColumnName(), "请输入派生输出字段名",
                    path + ".outputColumnName", issues);

            CanvasColumnSchema sourceColumn = CanvasNodeSupport.blank(derivation.sourceColumnName())
                    ? null : sourceColumns.get(derivation.sourceColumnName());
            if (source != null && !CanvasNodeSupport.blank(derivation.sourceColumnName())) {
                if (sourceColumn == null) {
                    issues.error(
                            "COLUMN_NOT_FOUND",
                            "Geometry 字段不存在：" + derivation.sourceColumnName(),
                            path + ".sourceColumnName"
                    );
                } else if (sourceColumn.fieldType() != PlatformDataType.GEOMETRY) {
                    issues.error(
                            "GEOMETRY_FIELD_OPERATION_UNSUPPORTED",
                            "所选字段不是 Geometry：" + derivation.sourceColumnName(),
                            path + ".sourceColumnName"
                    );
                } else {
                    if (UnaryGeometrySupport.checked(derivation.geometryPolicy()))
                        UnaryGeometrySupport.validateSource(sourceColumn, path + ".sourceColumnName", issues);
                    else CanvasNodeSupport.validateSupportedGeometry(List.of(sourceColumn), path + ".sourceColumnName", issues);
                    UnaryGeometrySupport.validate(sourceColumn.geometry(), derivation.geometryPolicy(), derivation.kind(), path, issues);
                }
            }
            if (!CanvasNodeSupport.blank(derivation.outputColumnName())
                    && !outputNames.add(derivation.outputColumnName().toLowerCase(Locale.ROOT))) {
                issues.error(
                        "DUPLICATE_COLUMN_NAME",
                        "派生输出字段名重复：" + derivation.outputColumnName(),
                        path + ".outputColumnName"
                );
            }
        }
        if (source == null || issues.hasErrors()) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        Dataset<Row> sourceDataset = source.dataset();
        List<Column> projection = new ArrayList<>(
                source.schema().columns().size() + derivations.size());
        for (CanvasColumnSchema column : source.schema().columns()) {
            projection.add(sourceDataset.col(CanvasNodeSupport.quoteIdentifier(column.name())));
        }
        List<CanvasColumnSchema> outputColumns = new ArrayList<>(source.schema().columns());
        for (GeometryDerivation derivation : derivations) {
            CanvasColumnSchema sourceColumn = sourceColumns.get(derivation.sourceColumnName());
            GeometryTypeDefinition sourceGeometry = sourceColumn.geometry();
            Column sourceExpression = sourceDataset.col(
                    CanvasNodeSupport.quoteIdentifier(derivation.sourceColumnName()));
            var kind = derivation.kind();
            var policy = derivation.geometryPolicy();
            var dimension = sourceGeometry.dimension();
            Column computed = UnaryGeometrySupport.checked(policy)
                    ? functions.udf((UDF1<Geometry, Geometry>) input -> UnaryGeometrySupport.derive(input, kind, policy, dimension),
                            sourceDataset.schema().apply(derivation.sourceColumnName()).dataType()).apply(sourceExpression)
                    : deriveExpression(derivation, sourceExpression);
            Column derived = st_functions.ST_SetSRID(
                    computed,
                    functions.lit(sourceGeometry.crs().code())
            ).alias(derivation.outputColumnName());
            projection.add(derived);
            outputColumns.add(new CanvasColumnSchema(
                    derivation.outputColumnName(),
                    PlatformDataType.GEOMETRY,
                    null,
                    null,
                    null,
                    sourceColumn.nullable(),
                    null,
                    false,
                    false,
                    null,
                    new GeometryTypeDefinition(
                            outputKind(derivation),
                            sourceGeometry.crs(),
                            UnaryGeometrySupport.dimension(policy, sourceGeometry.dimension())
                    )
            ));
        }

        Dataset<Row> derivedDataset = sourceDataset.select(projection.toArray(Column[]::new));
        CanvasTableSchema outputSchema = new CanvasTableSchema(
                configuration.outputTableName(),
                null,
                outputColumns,
                source.schema().datasetKind(),
                source.schema().eventTimeColumn(),
                source.schema().watermarkDelay()
        );
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>(inputs);
        output.put(outputSchema.name(), new SparkCanvasTable(outputSchema, derivedDataset));
        return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
    }

    private static void validateDerivationId(
            String value,
            String path,
            Set<UUID> derivationIds,
            CanvasNodeIssueSink issues
    ) {
        if (CanvasNodeSupport.blank(value)) {
            issues.error("INVALID_GEOMETRY_DERIVATION_ID", "派生项 ID 必须是 UUID", path);
            return;
        }
        UUID derivationId;
        try {
            derivationId = UUID.fromString(value.trim());
        } catch (IllegalArgumentException exception) {
            issues.error("INVALID_GEOMETRY_DERIVATION_ID", "派生项 ID 必须是 UUID", path);
            return;
        }
        if (!derivationIds.add(derivationId)) {
            issues.error("DUPLICATE_GEOMETRY_DERIVATION_ID", "派生项 ID 必须在节点内唯一", path);
        }
    }

    private static Column deriveExpression(
            GeometryDerivation derivation,
            Column source
    ) {
        return switch (derivation.kind()) {
            case CENTROID -> st_functions.ST_Centroid(source);
            case POINT_ON_SURFACE -> st_functions.ST_PointOnSurface(source);
            case ENVELOPE -> st_functions.ST_Envelope(source);
            case CONVEX_HULL -> st_functions.ST_ConvexHull(source);
            case BOUNDARY -> st_functions.ST_Boundary(source);
        };
    }

    private static GeometryKind outputKind(GeometryDerivation derivation) {
        return switch (derivation.kind()) {
            case CENTROID, POINT_ON_SURFACE -> GeometryKind.POINT;
            case ENVELOPE, CONVEX_HULL, BOUNDARY -> GeometryKind.GEOMETRY;
        };
    }
}
