package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDatasetKind;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.SpatialDistanceMethod;
import cn.superhuang.data.scalpel.contract.task.SpatialPointClusterConfiguration;
import cn.superhuang.data.scalpel.contract.task.SpatialPointClusterNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SpatialPointClusterParameters;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import org.apache.sedona.stats.clustering.DBSCAN;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.functions;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class SpatialPointClusterNodeOperator implements CanvasNodeOperator {

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.SPATIAL_POINT_CLUSTER;
    }

    @Override
    public CanvasNodeCategory category() {
        return CanvasNodeCategory.PROCESSOR;
    }

    @Override
    public Set<CanvasExecutionMode> supportedModes() {
        return Set.of(CanvasExecutionMode.BATCH);
    }

    @Override
    public CanvasNodeOperationResult apply(
            CanvasNodeDefinition definition,
            Map<String, SparkCanvasTable> inputs,
            CanvasNodeOperationContext context
    ) {
        if (!(definition instanceof SpatialPointClusterNodeDefinition node)) {
            throw new IllegalArgumentException("SPATIAL_POINT_CLUSTER operator received " + definition.nodeType());
        }
        List<CanvasTableSchema> inputSchemas = CanvasNodeSupport.schemas(inputs);
        SpatialPointClusterConfiguration configuration = node.configuration();
        if (configuration == null) return CanvasNodeOperationResult.invalid(inputSchemas);
        CanvasNodeIssueSink issues = context.issues();
        validateBase(configuration, inputs, issues);
        SparkCanvasTable source = inputs.get(configuration.sourceTableName());
        if (!CanvasNodeSupport.blank(configuration.sourceTableName()) && source == null) {
            issues.error("TABLE_NOT_FOUND", "来源表不在上游数据中：" + configuration.sourceTableName(),
                    "configuration.sourceTableName");
        }
        if (source == null) return CanvasNodeOperationResult.invalid(inputSchemas);
        Map<String, CanvasColumnSchema> columns = CanvasNodeSupport.columns(source.schema());
        CanvasColumnSchema point = validateSource(source, configuration, columns, issues);
        double epsilon = validateParameters(configuration, point, issues);
        long durationMicros = validateTime(configuration, columns, issues);
        validateOutputColumns(configuration, columns, issues);
        if (issues.hasErrors()) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }
        boolean hdbscan = configuration.parameters() instanceof SpatialPointClusterParameters.Hdbscan;
        Dataset<Row> sourceDataset = source.dataset();
        Dataset<Row> result;
        if (context.runtimeValues().preview()) {
            result = hdbscan ? PointHdbscanSupport.schemaPlan(sourceDataset, configuration) : PointDbscanSupport.schemaPlan(sourceDataset, configuration);
        } else {
            if (!ensureCheckpointDirectory(sourceDataset, issues)) return CanvasNodeOperationResult.invalid(inputSchemas);
            result = hdbscan ? PointHdbscanSupport.run(sourceDataset, configuration)
                    : configuration.dbscan() == null || configuration.dbscan().mode() == cn.superhuang.data.scalpel.contract.task.SpatialDbscanOptions.Mode.LEGACY_SPATIAL
                    ? legacy(sourceDataset, configuration, (SpatialPointClusterParameters.Dbscan) configuration.parameters(), epsilon)
                    : PointDbscanSupport.run(sourceDataset, configuration, epsilon, durationMicros);
        }

        List<CanvasColumnSchema> outputColumns = new ArrayList<>(source.schema().columns());
        outputColumns.add(TrackNodeSupport.longColumn(configuration.clusterIdColumnName(), true));
        outputColumns.add(TrackNodeSupport.booleanColumn(configuration.noiseColumnName(), false));
        if (hdbscan) {
            var d = configuration.hdbscan();
            outputColumns.add(TrackNodeSupport.doubleColumn(d.probabilityColumnName(), false));
            outputColumns.add(TrackNodeSupport.doubleColumn(d.outlierColumnName(), false));
            outputColumns.add(TrackNodeSupport.booleanColumn(d.exemplarColumnName(), false));
            outputColumns.add(TrackNodeSupport.doubleColumn(d.stabilityColumnName(), true));
        }
        CanvasTableSchema outputSchema = new CanvasTableSchema(
                configuration.outputTableName(), null, outputColumns, CanvasDatasetKind.BOUNDED,
                source.schema().eventTimeColumn(), null);
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>(inputs);
        output.put(outputSchema.name(), new SparkCanvasTable(outputSchema, result));
        return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
    }

    private static Dataset<Row> legacy(Dataset<Row> sourceDataset, SpatialPointClusterConfiguration configuration,
            SpatialPointClusterParameters.Dbscan dbscan, double epsilon) {
        String identity = TrackNodeSupport.internalName(sourceDataset, "__datascalpel_identity_valid");
        Column id = sourceDataset.col(CanvasNodeSupport.quoteIdentifier(configuration.featureIdColumnName()));
        Column count = functions.count(functions.lit(1)).over(org.apache.spark.sql.expressions.Window.partitionBy(id));
        sourceDataset = sourceDataset.withColumn(identity, functions.when(id.isNull().or(count.notEqual(1)),
                functions.raise_error(functions.lit("SPATIAL_CLUSTER_FEATURE_ID_INVALID")).cast("boolean")).otherwise(true))
                .filter(functions.col(CanvasNodeSupport.quoteIdentifier(identity))).drop(identity);
        String coreName = TrackNodeSupport.internalName(sourceDataset, "__datascalpel_cluster_core");
        String rawClusterName = TrackNodeSupport.internalName(sourceDataset, "__datascalpel_cluster_id");
        Dataset<Row> clustered = DBSCAN.dbscan(
                sourceDataset,
                epsilon,
                dbscan.minimumFeatures(),
                configuration.pointGeometryColumnName(),
                true,
                configuration.distanceMethod() == SpatialDistanceMethod.GEODESIC,
                coreName,
                rawClusterName);
        Column rawCluster = clustered.col(CanvasNodeSupport.quoteIdentifier(rawClusterName));
        Column noise = rawCluster.isNull().or(rawCluster.lt(0));
        List<Column> projection = new ArrayList<>();
        for (String column : sourceDataset.columns()) {
            projection.add(clustered.col(CanvasNodeSupport.quoteIdentifier(column)));
        }
        projection.add(functions.when(noise, functions.lit(null).cast("long"))
                .otherwise(rawCluster.cast("long")).alias(configuration.clusterIdColumnName()));
        projection.add(noise.alias(configuration.noiseColumnName()));
        return clustered.select(projection.toArray(Column[]::new));
    }

    private static long validateTime(SpatialPointClusterConfiguration c, Map<String, CanvasColumnSchema> columns, CanvasNodeIssueSink issues) {
        if (!(c.parameters() instanceof SpatialPointClusterParameters.Dbscan) || c.dbscan() == null) return 0;
        var time = c.dbscan(); String path = "configuration.dbscan";
        if (time.mode() == null) issues.error("INVALID_SPATIAL_CLUSTER_TIME_MODE", "请选择空间或 Linear 时空聚类", path + ".mode");
        if (!time.usesTime()) return 0;
        CanvasNodeSupport.required(time.timeColumnName(), "请选择 TIMESTAMP 时间字段", path + ".timeColumnName", issues);
        var field = columns.get(time.timeColumnName());
        if (!CanvasNodeSupport.blank(time.timeColumnName()) && field == null) issues.error("COLUMN_NOT_FOUND", "时间字段不在来源表中", path + ".timeColumnName");
        else if (field != null && field.fieldType() != PlatformDataType.TIMESTAMP) issues.error("INVALID_SPATIAL_CLUSTER_TIME_COLUMN", "Linear 时空聚类需要 TIMESTAMP", path + ".timeColumnName");
        if (time.searchDuration() == null || time.searchDuration() <= 0 || time.searchDurationUnit() == null) {
            issues.error("INVALID_SPATIAL_CLUSTER_DURATION", "时间邻域需要正整数和明确时长单位", path + ".searchDuration"); return 0;
        }
        long factor = switch (time.searchDurationUnit()) {
            case MILLISECONDS -> 1000L; case SECONDS -> 1_000_000L; case MINUTES -> 60_000_000L;
            case HOURS -> 3_600_000_000L; case DAYS -> 86_400_000_000L;
            case WEEKS -> 604_800_000_000L;
        };
        try { return Math.multiplyExact(time.searchDuration(), factor); }
        catch (ArithmeticException exception) { issues.error("INVALID_SPATIAL_CLUSTER_DURATION", "时间邻域超出微秒时长范围", path + ".searchDuration"); return 0; }
    }

    private static boolean ensureCheckpointDirectory(
            Dataset<Row> dataset,
            CanvasNodeIssueSink issues
    ) {
        var sparkContext = dataset.sparkSession().sparkContext();
        if (sparkContext.getCheckpointDir().nonEmpty()) return true;
        String configured = sparkContext.getConf().contains("spark.checkpoint.dir")
                ? sparkContext.getConf().get("spark.checkpoint.dir")
                : null;
        if (!CanvasNodeSupport.blank(configured)) {
            sparkContext.setCheckpointDir(configured.trim());
            return true;
        }
        if (sparkContext.master().startsWith("local")) {
            String applicationId = sparkContext.applicationId().replaceAll("[^A-Za-z0-9._-]", "_");
            String local = Path.of(
                    System.getProperty("java.io.tmpdir"),
                    "datascalpel-spark-checkpoints",
                    applicationId).toUri().toString();
            sparkContext.setCheckpointDir(local);
            return true;
        }
        issues.error(
                "SPATIAL_CLUSTER_CHECKPOINT_NOT_CONFIGURED",
                "集群模式执行点聚类前必须配置 spark.checkpoint.dir，并指向所有执行器可访问的文件系统",
                "configuration.parameters.algorithm");
        return false;
    }

    private static void validateBase(
            SpatialPointClusterConfiguration configuration,
            Map<String, SparkCanvasTable> inputs,
            CanvasNodeIssueSink issues
    ) {
        CanvasNodeSupport.required(configuration.sourceTableName(), "请选择来源表", "configuration.sourceTableName", issues);
        CanvasNodeSupport.required(configuration.pointGeometryColumnName(), "请选择点 Geometry 字段", "configuration.pointGeometryColumnName", issues);
        CanvasNodeSupport.required(configuration.featureIdColumnName(), "请选择要素唯一字段", "configuration.featureIdColumnName", issues);
        CanvasNodeSupport.required(configuration.outputTableName(), "请输入输出表名", "configuration.outputTableName", issues);
        CanvasNodeSupport.required(configuration.clusterIdColumnName(), "请输入聚类 ID 字段名", "configuration.clusterIdColumnName", issues);
        CanvasNodeSupport.required(configuration.noiseColumnName(), "请输入噪声标记字段名", "configuration.noiseColumnName", issues);
        if (configuration.distanceMethod() == null) {
            issues.error("REQUIRED_CONFIGURATION", "请选择距离方法", "configuration.distanceMethod");
        }
        if (configuration.parameters() == null) {
            issues.error("REQUIRED_CONFIGURATION", "请选择聚类算法并配置参数", "configuration.parameters");
        }
        if (!CanvasNodeSupport.blank(configuration.outputTableName()) && inputs.containsKey(configuration.outputTableName())) {
            issues.error("DUPLICATE_TABLE_NAME", "输出表名已存在：" + configuration.outputTableName(), "configuration.outputTableName");
        }
    }

    private static CanvasColumnSchema validateSource(
            SparkCanvasTable source,
            SpatialPointClusterConfiguration configuration,
            Map<String, CanvasColumnSchema> columns,
            CanvasNodeIssueSink issues
    ) {
        if (source.schema().datasetKind() != CanvasDatasetKind.BOUNDED) {
            issues.error("BOUNDED_INPUT_REQUIRED", "点聚类只支持有界输入", "configuration.sourceTableName");
        }
        CanvasColumnSchema point = columns.get(configuration.pointGeometryColumnName());
        if (!CanvasNodeSupport.blank(configuration.pointGeometryColumnName()) && point == null) {
            issues.error("COLUMN_NOT_FOUND", "点 Geometry 字段不存在：" + configuration.pointGeometryColumnName(), "configuration.pointGeometryColumnName");
        } else if (point != null && (point.fieldType() != PlatformDataType.GEOMETRY || point.geometry() == null || point.geometry().crs() == null
                || point.geometry().kind() != GeometryKind.POINT || point.geometry().dimension() != CoordinateDimension.XY)) {
            issues.error("SPATIAL_POINT_XY_REQUIRED", "点聚类需要带完整元数据的 XY Point", "configuration.pointGeometryColumnName");
        }
        CanvasColumnSchema featureId = columns.get(configuration.featureIdColumnName());
        if (!CanvasNodeSupport.blank(configuration.featureIdColumnName()) && featureId == null) {
            issues.error("COLUMN_NOT_FOUND", "要素唯一字段不存在：" + configuration.featureIdColumnName(), "configuration.featureIdColumnName");
        } else if (featureId != null && featureId.fieldType() == PlatformDataType.GEOMETRY) {
            issues.error("GEOMETRY_FIELD_OPERATION_UNSUPPORTED", "Geometry 不能作为要素唯一字段", "configuration.featureIdColumnName");
        }
        if (point != null && point.geometry() != null && point.geometry().crs() != null && configuration.distanceMethod() == SpatialDistanceMethod.GEODESIC
                && (!("EPSG".equalsIgnoreCase(point.geometry().crs().authority())
                && point.geometry().crs().code() == 4326))) {
            issues.error("GEODESIC_REQUIRES_WGS84_XY", "测地线聚类只支持 EPSG:4326 XY", "configuration.distanceMethod");
        }
        return point;
    }

    private static double validateParameters(
            SpatialPointClusterConfiguration configuration,
            CanvasColumnSchema point,
            CanvasNodeIssueSink issues
    ) {
        SpatialPointClusterParameters parameters = configuration.parameters();
        if (parameters == null) return Double.NaN;
        if (parameters.minimumFeatures() < 2 || parameters.minimumFeatures() > 100_000) {
            issues.error("INVALID_SPATIAL_CLUSTER_MINIMUM_FEATURES", "最少要素数必须在 2 到 100000 之间", "configuration.parameters.minimumFeatures");
        }
        if (parameters instanceof SpatialPointClusterParameters.Hdbscan) {
            if (configuration.hdbscan() == null) issues.error("SPATIAL_HDBSCAN_DIAGNOSTICS_REQUIRED", "请配置 HDBSCAN 的四个诊断输出字段", "configuration.hdbscan");
            if (configuration.distanceMethod() == SpatialDistanceMethod.PLANAR && point != null && point.geometry() != null && point.geometry().crs() != null
                    && SpatialDistanceSupport.resolve(1, cn.superhuang.data.scalpel.contract.task.SpatialDistanceUnit.SOURCE_CRS_UNIT, point.geometry().crs()).angular())
                issues.warning("PLANAR_DISTANCE_USES_ANGULAR_UNITS", "地理 CRS 的平面聚类按角度计算", "configuration.distanceMethod");
            return Double.NaN;
        }
        if (parameters instanceof SpatialPointClusterParameters.MultiScale multiScale) {
            if (!Double.isFinite(multiScale.sensitivity()) || multiScale.sensitivity() < 0 || multiScale.sensitivity() > 100) {
                issues.error("INVALID_SPATIAL_CLUSTER_SENSITIVITY", "灵敏度必须在 0 到 100 之间", "configuration.parameters.sensitivity");
            }
            issues.error("SPATIAL_CLUSTER_ALGORITHM_NOT_AVAILABLE", "当前运行时尚未提供分布式 Multi-scale 聚类", "configuration.parameters.algorithm");
            return Double.NaN;
        }
        SpatialPointClusterParameters.Dbscan dbscan = (SpatialPointClusterParameters.Dbscan) parameters;
        if (!Double.isFinite(dbscan.searchDistance()) || dbscan.searchDistance() <= 0 || dbscan.searchDistanceUnit() == null) {
            issues.error("INVALID_SPATIAL_CLUSTER_DISTANCE", "DBSCAN 搜索距离必须是带单位的有限正数", "configuration.parameters.searchDistance");
            return Double.NaN;
        }
        if (point == null || point.geometry() == null || point.geometry().crs() == null) return Double.NaN;
        if (configuration.distanceMethod() == SpatialDistanceMethod.GEODESIC) {
            double metres = dbscan.searchDistance() * SpatialDistanceSupport.metresPerConfiguredUnit(dbscan.searchDistanceUnit());
            if (!Double.isFinite(metres)) {
                issues.error("INVALID_SPATIAL_CLUSTER_DISTANCE", "测地线聚类需要明确的线性距离单位", "configuration.parameters.searchDistanceUnit");
            }
            return metres;
        }
        SpatialDistanceSupport.Resolution resolution = SpatialDistanceSupport.resolve(
                dbscan.searchDistance(), dbscan.searchDistanceUnit(), point.geometry().crs());
        if (!resolution.valid()) {
            issues.error("INVALID_SPATIAL_CLUSTER_DISTANCE", resolution.error(), "configuration.parameters.searchDistanceUnit");
            return Double.NaN;
        }
        if (resolution.angular()) {
            issues.warning("PLANAR_DISTANCE_USES_ANGULAR_UNITS", "地理 CRS 的平面聚类按角度计算", "configuration.parameters.searchDistanceUnit");
        }
        return resolution.sourceCrsValue();
    }

    private static void validateOutputColumns(
            SpatialPointClusterConfiguration configuration,
            Map<String, CanvasColumnSchema> columns,
            CanvasNodeIssueSink issues
    ) {
        Set<String> names = new HashSet<>();
        columns.keySet().forEach(name -> names.add(name.toLowerCase(Locale.ROOT)));
        for (String name : new String[]{configuration.clusterIdColumnName(), configuration.noiseColumnName()}) {
            if (!CanvasNodeSupport.blank(name) && !names.add(name.toLowerCase(Locale.ROOT))) {
                issues.error("DUPLICATE_COLUMN_NAME", "输出字段名重复：" + name, "configuration");
            }
        }
        if (configuration.parameters() instanceof SpatialPointClusterParameters.Hdbscan && configuration.hdbscan() != null) {
            var d = configuration.hdbscan();
            String[] fields = { "probabilityColumnName", "outlierColumnName", "exemplarColumnName", "stabilityColumnName" };
            String[] values = { d.probabilityColumnName(), d.outlierColumnName(), d.exemplarColumnName(), d.stabilityColumnName() };
            for (int i = 0; i < fields.length; i++) {
                String path = "configuration.hdbscan." + fields[i];
                CanvasNodeSupport.required(values[i], "请输入诊断输出字段名", path, issues);
                if (!CanvasNodeSupport.blank(values[i]) && !names.add(values[i].toLowerCase(Locale.ROOT)))
                    issues.error("DUPLICATE_COLUMN_NAME", "诊断输出字段名重复", path);
            }
        }
    }
}
