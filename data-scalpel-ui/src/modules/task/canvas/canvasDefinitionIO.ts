import { usesExplicitWithinStatistics, requiresWithinWeightedDispersionVersion } from "./nodes/spatialSummarizeWithin/statisticOptions";
import { unsupportedSpatialUnitPaths, unsupportedSpatialDurationPaths } from "./parsing/spatialCompatibility";
import { CANVAS_LEGACY_SCHEMA_MINOR_VERSION, CANVAS_SCHEMA_MINOR_VERSION, CANVAS_SCHEMA_VERSION, CanvasNodeType, type CanvasDefinition, type CanvasEdgeDefinition, type CanvasNodeDefinition, type CanvasNodeType as CanvasNodeTypeValue } from "./canvasTypes";
import { canvasNodeRegistry } from "./nodes/nodeRegistry";
import { uuidPattern, isRecord, stringValue, parseLayout } from './canvasValueParsers';
export { isSensitiveRuntimeParameterName, stringValue, validateOptionalUuid, legacyTableName, parseJoinType, parseStreamJoinType, parseJoinConditions, parseJoinOutputColumns, parseWriteMode, parseFileOutputConflictPolicy, parseFileOutputFormatOptions, parseMappings, parseStringArray, parseRuntimeParameters, parseCanvasLiteral, parseFilterCondition, parseDerivations, parsePlatformTypeDefinition, parseTypeCasts, parseAggregations, parseUnionMode, parseDeduplicateKeepStrategy, parseSortFields, parseKafkaValueSchema } from './canvasValueParsers';

export type CanvasDefinitionParseResult =
  | { success: true; definition: CanvasDefinition }
  | { success: false; errors: string[] };

const supportedNodeTypes = new Set<string>(Object.values(CanvasNodeType));

const parseNode = (value: unknown, index: number, errors: string[]): CanvasNodeDefinition | null => {
  const path = `nodes[${index}]`;
  if (!isRecord(value)) {
    errors.push(`${path} 必须是对象`);
    return null;
  }
  const id = stringValue(value.id);
  if (!uuidPattern.test(id)) errors.push(`${path}.id 必须是 UUID`);
  if (typeof value.name !== 'string') errors.push(`${path}.name 必须是字符串`);
  const name = stringValue(value.name);
  const layout = parseLayout(value.layout, `${path}.layout`, errors);
  if (!layout) return null;

  const rawType = stringValue(value.type);
  if (!supportedNodeTypes.has(rawType)) {
    errors.push(`${path}.type 不是受支持的节点类型`);
    return null;
  }
  const type = rawType as CanvasNodeTypeValue;
  const parsedConfiguration = canvasNodeRegistry.require(type).parseConfiguration(
    value.configuration,
    `${path}.configuration`,
  );
  if (!parsedConfiguration.success) {
    errors.push(...parsedConfiguration.errors);
    return null;
  }

  return {
    id,
    type,
    name,
    layout,
    configuration: parsedConfiguration.value,
  } as CanvasNodeDefinition;
};

const parseEdge = (value: unknown, index: number, errors: string[]): CanvasEdgeDefinition | null => {
  const path = `edges[${index}]`;
  if (!isRecord(value)) {
    errors.push(`${path} 必须是对象`);
    return null;
  }
  const id = stringValue(value.id);
  const sourceNodeId = stringValue(value.sourceNodeId);
  const targetNodeId = stringValue(value.targetNodeId);
  if (!uuidPattern.test(id)) errors.push(`${path}.id 必须是 UUID`);
  if (!uuidPattern.test(sourceNodeId)) errors.push(`${path}.sourceNodeId 必须是 UUID`);
  if (!uuidPattern.test(targetNodeId)) errors.push(`${path}.targetNodeId 必须是 UUID`);
  return { id, sourceNodeId, targetNodeId };
};

export const parseCanvasDefinition = (value: unknown): CanvasDefinitionParseResult => {
  const errors: string[] = [];
  if (!isRecord(value)) return { success: false, errors: ['Canvas 定义必须是 JSON 对象'] };
  if (value.schemaVersion !== CANVAS_SCHEMA_VERSION) {
    errors.push(`schemaVersion 仅支持 ${CANVAS_SCHEMA_VERSION}`);
  }
  const sourceSchemaMinorVersion = value.schemaMinorVersion === undefined
    ? CANVAS_LEGACY_SCHEMA_MINOR_VERSION
    : value.schemaMinorVersion;
  if (typeof sourceSchemaMinorVersion !== 'number'
      || !Number.isInteger(sourceSchemaMinorVersion)
      || sourceSchemaMinorVersion < CANVAS_LEGACY_SCHEMA_MINOR_VERSION
      || sourceSchemaMinorVersion > CANVAS_SCHEMA_MINOR_VERSION) {
    errors.push(
      `schemaMinorVersion 仅支持 ${CANVAS_LEGACY_SCHEMA_MINOR_VERSION} 到 ${CANVAS_SCHEMA_MINOR_VERSION}`,
    );
  }
  if (!Array.isArray(value.nodes)) errors.push('nodes 必须是数组');
  if (!Array.isArray(value.edges)) errors.push('edges 必须是数组');
  if (errors.length > 0) return { success: false, errors };

  const nodes = (value.nodes as unknown[]).flatMap((item, index): CanvasNodeDefinition[] => {
    const parsed = parseNode(item, index, errors);
    return parsed ? [parsed] : [];
  });
  const edges = (value.edges as unknown[]).flatMap((item, index): CanvasEdgeDefinition[] => {
    const parsed = parseEdge(item, index, errors);
    return parsed ? [parsed] : [];
  });

  if (typeof sourceSchemaMinorVersion === 'number') {
    nodes.forEach((node, index) => {
      const spec = canvasNodeRegistry.require(node.type);
      if (sourceSchemaMinorVersion < 41 && node.type === CanvasNodeType.SpatialSummarizeWithin && node.configuration.regions != null)
        errors.push(`SPATIAL_WITHIN_REGIONS_REQUIRE_SCHEMA_VERSION：nodes[${index}].configuration.regions 从 Canvas 4.41 开始支持`);
      if (sourceSchemaMinorVersion < 40 && node.type === CanvasNodeType.SpatialPointCluster && node.configuration.dbscan != null)
        errors.push(`SPATIAL_DBSCAN_OPTIONS_REQUIRE_SCHEMA_VERSION：nodes[${index}].configuration.dbscan 从 Canvas 4.40 开始支持`);
      if (sourceSchemaMinorVersion < 39 && (node.type === CanvasNodeType.SpatialBinAggregate || node.type === CanvasNodeType.SpatialSummarizeWithin)
          && node.configuration.temporalSlicing?.calendar != null) {
        errors.push(`SPATIAL_CALENDAR_WINDOW_REQUIRE_SCHEMA_VERSION：nodes[${index}].configuration.temporalSlicing.calendar 从 Canvas 4.39 开始支持`);
      }
      if (sourceSchemaMinorVersion < 38 && node.type === CanvasNodeType.SpatialBinAggregate && node.configuration.planarGrid != null) {
        errors.push(`SPATIAL_PLANAR_GRID_REQUIRE_SCHEMA_VERSION：nodes[${index}].configuration.planarGrid 从 Canvas 4.38 开始支持`);
      }
      if (sourceSchemaMinorVersion < 37 && node.type === CanvasNodeType.SpatialSummarizeWithin) {
        node.configuration.statistics.forEach((item, statisticIndex) => {
          if (requiresWithinWeightedDispersionVersion(item)) errors.push(`SPATIAL_WITHIN_WEIGHTED_DISPERSION_REQUIRE_SCHEMA_VERSION：nodes[${index}].configuration.statistics[${statisticIndex}].weighting 从 Canvas 4.37 开始支持`);
        });
      }
      for (const path of unsupportedSpatialUnitPaths(node, sourceSchemaMinorVersion)) {
        errors.push(`SPATIAL_EXTENDED_UNITS_REQUIRE_SCHEMA_VERSION：nodes[${index}].${path} 的扩展单位从 Canvas 4.36 开始支持`);
      }
      for (const path of unsupportedSpatialDurationPaths(node, sourceSchemaMinorVersion)) {
        errors.push(`SPATIAL_DURATION_WEEKS_REQUIRE_SCHEMA_VERSION：nodes[${index}].${path} 的固定周单位从 Canvas 4.47 开始支持`);
      }
      if (sourceSchemaMinorVersion < 35 && (node.type === CanvasNodeType.TrackReconstruct || node.type === CanvasNodeType.TrackFindDwell)
          && node.configuration.summaryStatistics.some(s => s.kind === 'COUNT_FIELD' || s.kind === 'ANY'))
        errors.push('TRACK_FIELD_STATISTICS_REQUIRE_SCHEMA_VERSION：轨迹字段 Count/Any 从 Canvas 4.35 开始支持');
      if (sourceSchemaMinorVersion < 34 && node.type === CanvasNodeType.SpatialBinAggregate
          && node.configuration.statistics.some(s => s.kind === 'COUNT_FIELD' || s.kind === 'ANY'))
        errors.push('SPATIAL_BIN_FIELD_STATISTICS_REQUIRE_SCHEMA_VERSION：格网字段 Count/Any 从 Canvas 4.34 开始支持');
      if (sourceSchemaMinorVersion < 33 && node.type === CanvasNodeType.SpatialBinAggregate
          && (node.configuration.binShape === 'H3' || node.configuration.h3 != null))
        errors.push('SPATIAL_H3_REQUIRE_SCHEMA_VERSION：H3 格网从 Canvas 4.33 开始支持');
      if (sourceSchemaMinorVersion < 32 && node.type === CanvasNodeType.SpatialCenterDispersion
          && node.configuration.analyses.some(a => a.centralFeatureColumns != null))
        errors.push('SPATIAL_CENTER_PROJECTION_REQUIRE_SCHEMA_VERSION：中央要素字段投影从 Canvas 4.32 开始支持');
      if (sourceSchemaMinorVersion < 31 && node.type === CanvasNodeType.SpatialCenterDispersion
          && (node.configuration.resultMode != null || node.configuration.analyses.some(a => a.outputTableName != null)))
        errors.push('SPATIAL_CENTER_RESULTS_REQUIRE_SCHEMA_VERSION：中心独立结果配置从 Canvas 4.31 开始支持');
      if (sourceSchemaMinorVersion < 30 && node.type === CanvasNodeType.SpatialNearest && node.configuration.matching != null) {
        errors.push('SPATIAL_NEAREST_MATCHING_REQUIRE_SCHEMA_VERSION：显式最近邻匹配策略从 Canvas 4.30 开始支持');
      }
      if (sourceSchemaMinorVersion < 48 && node.type === CanvasNodeType.SpatialNearest
          && node.configuration.matching?.geodesicGeometryMode === 'GEOMETRY') {
        errors.push('SPATIAL_NEAREST_GEODESIC_GEOMETRY_REQUIRE_SCHEMA_VERSION：非点 WGS84 真实最近位置从 Canvas 4.48 开始支持');
      }
      if (sourceSchemaMinorVersion < 49 && node.type === CanvasNodeType.GeometryBuffer
          && node.configuration.distanceUnit != null) {
        errors.push('GEOMETRY_BUFFER_UNIT_REQUIRE_SCHEMA_VERSION：Geometry Buffer 显式距离单位从 Canvas 4.49 开始支持');
      }
      if (sourceSchemaMinorVersion < 52 && node.type === CanvasNodeType.GeometryBuffer
          && (node.configuration.distanceSource != null
          || node.configuration.distanceFieldName != null
          || node.configuration.distanceExpression != null)) {
        errors.push('GEOMETRY_BUFFER_DISTANCE_SOURCE_REQUIRE_SCHEMA_VERSION：Geometry Buffer 逐行距离来源从 Canvas 4.52 开始支持');
      }
      if (sourceSchemaMinorVersion < 53 && node.type === CanvasNodeType.SpatialAggregate
          && node.configuration.dissolve != null) {
        errors.push('SPATIAL_AGGREGATE_DISSOLVE_REQUIRE_SCHEMA_VERSION：空间聚合 Dissolve 选项从 Canvas 4.53 开始支持');
      }
      if (sourceSchemaMinorVersion < 61 && node.type === CanvasNodeType.SpatialAggregate
          && node.configuration.dissolve?.groupingMode != null) {
        errors.push('SPATIAL_DISSOLVE_GROUPING_MODE_REQUIRE_SCHEMA_VERSION：空间聚合 Dissolve 分组方式从 Canvas 4.61 开始支持');
      }
      if (sourceSchemaMinorVersion < 62 && node.type === CanvasNodeType.Union
          && node.configuration.mergingTables != null) {
        errors.push('UNION_MERGE_LAYERS_REQUIRE_SCHEMA_VERSION：Union 的 Merge Layers 字段处理从 Canvas 4.62 开始支持');
      }
      if (sourceSchemaMinorVersion < 54 && node.type === CanvasNodeType.SpatialJoin
          && node.configuration.outputColumns != null) {
        errors.push('SPATIAL_JOIN_OUTPUT_COLUMNS_REQUIRE_SCHEMA_VERSION：空间连接输出字段投影从 Canvas 4.54 开始支持');
      }
      if (sourceSchemaMinorVersion < 55 && node.type === CanvasNodeType.SpatialJoin
          && node.configuration.attributeConditions != null) {
        errors.push('SPATIAL_JOIN_ATTRIBUTE_CONDITIONS_REQUIRE_SCHEMA_VERSION：空间连接属性匹配条件从 Canvas 4.55 开始支持');
      }
      if (sourceSchemaMinorVersion < 56 && node.type === CanvasNodeType.SpatialJoin
          && node.configuration.joinType === 'LEFT') {
        errors.push('SPATIAL_JOIN_KEEP_ALL_REQUIRE_SCHEMA_VERSION：空间连接保留全部目标要素从 Canvas 4.56 开始支持');
      }
      if (sourceSchemaMinorVersion < 57 && node.type === CanvasNodeType.SpatialJoin
          && node.configuration.joinOperation != null) {
        errors.push('SPATIAL_JOIN_OPERATION_REQUIRE_SCHEMA_VERSION：空间连接显式结果粒度从 Canvas 4.57 开始支持');
      }
      if (sourceSchemaMinorVersion < 58 && node.type === CanvasNodeType.SpatialJoin
          && (node.configuration.joinOperation === 'JOIN_ONE_TO_ONE'
          || node.configuration.oneToOne != null)) {
        errors.push('SPATIAL_JOIN_ONE_TO_ONE_REQUIRE_SCHEMA_VERSION：空间连接一对一规则从 Canvas 4.58 开始支持');
      }
      if (sourceSchemaMinorVersion < 59 && node.type === CanvasNodeType.SpatialJoin
          && node.configuration.temporalCondition != null) {
        errors.push('SPATIAL_JOIN_TEMPORAL_CONDITION_REQUIRE_SCHEMA_VERSION：空间连接时间关系从 Canvas 4.59 开始支持');
      }
      if (sourceSchemaMinorVersion < 60 && node.type === CanvasNodeType.SpatialJoin
          && (node.configuration.spatialNear != null
          || node.configuration.distanceOutput != null)) {
        errors.push('SPATIAL_JOIN_NEAR_REQUIRE_SCHEMA_VERSION：空间连接 Near 和距离输出从 Canvas 4.60 开始支持');
      }
      if (sourceSchemaMinorVersion < 50 && node.type === CanvasNodeType.SpatialMeasure
          && node.configuration.measurements.some((measurement) => (
            'outputUnit' in measurement && measurement.outputUnit != null
          ))) {
        errors.push('SPATIAL_MEASURE_UNIT_REQUIRE_SCHEMA_VERSION：空间测量显式输出单位从 Canvas 4.50 开始支持');
      }
      if (sourceSchemaMinorVersion < 51 && node.type === CanvasNodeType.SpatialClip
          && node.configuration.geometryPolicy != null) {
        errors.push('SPATIAL_CLIP_GEOMETRY_POLICY_REQUIRE_SCHEMA_VERSION：空间裁剪显式几何策略从 Canvas 4.51 开始支持');
      }
      if (sourceSchemaMinorVersion < 77 && node.type === CanvasNodeType.SpatialClip
          && node.configuration.maskCombination != null) {
        errors.push('SPATIAL_CLIP_MASK_COMBINATION_REQUIRE_SCHEMA_VERSION：空间裁剪多 Mask 组合方式从 Canvas 4.77 开始支持');
      }
      if (sourceSchemaMinorVersion < 29 && (
        node.type === CanvasNodeType.GeometryDerive && node.configuration.derivations.some(item => item.geometryPolicy != null)
        || node.type === CanvasNodeType.GeometrySimplify && node.configuration.geometryPolicy != null
      )) errors.push('GEOMETRY_UNARY_POLICY_REQUIRE_SCHEMA_VERSION：显式一元几何策略从 Canvas 4.29 开始支持');
      if (sourceSchemaMinorVersion < 46 && node.type === CanvasNodeType.TrackDetectIncidents
          && (node.configuration.conditionWindows?.length ?? 0) > 0) {
        errors.push('TRACK_INCIDENT_WINDOWS_REQUIRE_SCHEMA_VERSION：事件窗口指标从 Canvas 4.46 开始支持');
      }
      if (sourceSchemaMinorVersion < 63 && node.type === CanvasNodeType.TrackDetectIncidents
          && (node.configuration.conditionWindows ?? []).some(window => window.source === 'TRACK_DISTANCE')) {
        errors.push('TRACK_INCIDENT_DISTANCE_WINDOWS_REQUIRE_SCHEMA_VERSION：事件轨迹距离窗口从 Canvas 4.63 开始支持');
      }
      if (sourceSchemaMinorVersion < 64 && node.type === CanvasNodeType.TrackDetectIncidents
          && (node.configuration.conditionWindows ?? []).some(window => window.source === 'TRACK_SPEED')) {
        errors.push('TRACK_INCIDENT_SPEED_WINDOWS_REQUIRE_SCHEMA_VERSION：事件轨迹速度窗口从 Canvas 4.64 开始支持');
      }
      if (sourceSchemaMinorVersion < 65 && node.type === CanvasNodeType.TrackDetectIncidents
          && (node.configuration.conditionWindows ?? []).some(window => window.source === 'TRACK_ACCELERATION')) {
        errors.push('TRACK_INCIDENT_ACCELERATION_WINDOWS_REQUIRE_SCHEMA_VERSION：事件轨迹加速度窗口从 Canvas 4.65 开始支持');
      }
      if (sourceSchemaMinorVersion < 66 && node.type === CanvasNodeType.TrackDetectIncidents
          && (node.configuration.conditionScalars?.length ?? 0) > 0) {
        errors.push('TRACK_INCIDENT_SCALARS_REQUIRE_SCHEMA_VERSION：事件轨迹标量从 Canvas 4.66 开始支持');
      }
      if (sourceSchemaMinorVersion < 67 && node.type === CanvasNodeType.TrackDetectIncidents
          && (node.configuration.conditionScalars ?? []).some(scalar => scalar.source === 'TRACK_POINT_X_AT'
            || scalar.source === 'TRACK_POINT_Y_AT')) {
        errors.push('TRACK_INCIDENT_POINT_COORDINATES_REQUIRE_SCHEMA_VERSION：事件 Point 坐标标量从 Canvas 4.67 开始支持');
      }
      if (sourceSchemaMinorVersion < 45 && node.type === CanvasNodeType.SpatialPointCluster && node.configuration.hdbscan != null) {
        errors.push('SPATIAL_HDBSCAN_OPTIONS_REQUIRE_SCHEMA_VERSION：HDBSCAN 诊断配置从 Canvas 4.45 开始支持');
      }
      if (sourceSchemaMinorVersion < 44 && node.type === CanvasNodeType.TrackReconstruct && node.configuration.reconstruction?.areaGeometry?.geodesicBoundary != null) {
        errors.push('TRACK_GEODESIC_AREA_REQUIRE_SCHEMA_VERSION：测地面边界配置从 Canvas 4.44 开始支持');
      }
      if (sourceSchemaMinorVersion < 43 && node.type === CanvasNodeType.TrackReconstruct && (node.configuration.reconstruction?.areaGeometry?.windowBindings?.length ?? 0) > 0) {
        errors.push('TRACK_BUFFER_WINDOWS_REQUIRE_SCHEMA_VERSION：轨迹缓冲窗口绑定从 Canvas 4.43 开始支持');
      }
      if (sourceSchemaMinorVersion < 42 && node.type === CanvasNodeType.TrackReconstruct && node.configuration.reconstruction?.areaGeometry != null) {
        errors.push('TRACK_AREA_GEOMETRY_REQUIRE_SCHEMA_VERSION：显式面轨迹从 Canvas 4.42 开始支持');
      }
      if (sourceSchemaMinorVersion < 28 && node.type === CanvasNodeType.TrackReconstruct && node.configuration.reconstruction?.pathGeometry != null) {
        errors.push('TRACK_PATH_GEOMETRY_REQUIRE_SCHEMA_VERSION：显式轨迹路径从 Canvas 4.28 开始支持');
      }
      if (sourceSchemaMinorVersion < 27 && node.type === CanvasNodeType.TrackReconstruct && node.configuration.reconstruction != null) {
        errors.push('TRACK_RECONSTRUCT_OPTIONS_REQUIRE_SCHEMA_VERSION：显式轨迹重建次序与拆分从 Canvas 4.27 开始支持');
      }
      if (sourceSchemaMinorVersion < 26 && node.type === CanvasNodeType.SpatialOverlay
          && (node.configuration.geometryPolicy != null || node.configuration.operation === 'IDENTITY'
            || node.configuration.operation === 'SYMMETRICAL_DIFFERENCE')) {
        errors.push('SPATIAL_OVERLAY_FAMILY_REQUIRE_SCHEMA_VERSION：五模式及显式几何输出从 Canvas 4.26 开始支持');
      }
      if (sourceSchemaMinorVersion < 25 && node.type === CanvasNodeType.SpatialSummarizeWithin
          && node.configuration.groupResult != null) {
        errors.push('SPATIAL_WITHIN_GROUP_RESULT_REQUIRE_SCHEMA_VERSION：区域关联分组结果从 Canvas 4.25 开始支持');
      }
      if (sourceSchemaMinorVersion < 24 && node.type === CanvasNodeType.SpatialSummarizeWithin
          && usesExplicitWithinStatistics(node.configuration.statistics)) {
        errors.push('SPATIAL_WITHIN_STATISTICS_REQUIRE_SCHEMA_VERSION：显式区域统计从 Canvas 4.24 开始支持');
      }
      if (sourceSchemaMinorVersion < 23 && node.type === CanvasNodeType.TrackMotionStatistics
          && (node.configuration.motionSemantics != null || node.configuration.windowOptions != null)) {
        errors.push('TRACK_MOTION_STATISTICS 历史窗口配置从 Canvas 4.23 开始支持');
      }
      if (sourceSchemaMinorVersion < 22 && node.type === CanvasNodeType.TrackFindDwell
          && (node.configuration.dwellSemantics != null || node.configuration.rangeOptions != null)) {
        errors.push('TRACK_FIND_DWELL 候选范围配置从 Canvas 4.22 开始支持');
      }
      if (sourceSchemaMinorVersion < 21 && node.type === CanvasNodeType.SpatialBinAggregate
          && node.configuration.binSizeSemantics != null) {
        errors.push('SPATIAL_BIN_AGGREGATE 显式格网尺寸语义从 Canvas 4.21 开始支持');
      }
      if (sourceSchemaMinorVersion < 21 && 'boundaries' in node.configuration
          && node.configuration.boundaries?.fixedTimeBoundary != null) {
        errors.push(`${node.type} 固定时间边界从 Canvas 4.21 开始支持`);
      }
      if (sourceSchemaMinorVersion < 21
          && node.type === CanvasNodeType.TrackDetectIncidents
          && (node.configuration.incidentSemantics === 'CONDITION_LIFECYCLE'
            || node.configuration.incidentStatusColumnName != null
            || (node.configuration.orderByColumns?.length ?? 0) > 0)) {
        errors.push('TRACK_DETECT_INCIDENTS 事件生命周期配置从 Canvas 4.21 开始支持');
      }
      if (sourceSchemaMinorVersion < spec.introducedInMinor) {
        errors.push(
          `${spec.type} 从 Canvas ${CANVAS_SCHEMA_VERSION}.${spec.introducedInMinor} 开始支持`,
        );
      }
      if (sourceSchemaMinorVersion < 1
          && node.type === CanvasNodeType.JdbcInput
          && node.configuration.tables.some((table) => table.readOptions.length > 0)) {
        errors.push(`JDBC_INPUT.readOptions 从 Canvas ${CANVAS_SCHEMA_VERSION}.1 开始支持`);
      }
      if (sourceSchemaMinorVersion < 2
          && node.type === CanvasNodeType.TypeCast
          && (node.configuration.operations ?? []).some((operation) => (
            operation.casts.some((cast) => cast.epochTimestampUnit != null)
          ))) {
        errors.push(`TYPE_CAST.epochTimestampUnit 从 Canvas ${CANVAS_SCHEMA_VERSION}.2 开始支持`);
      }
      if (sourceSchemaMinorVersion >= 2
          && sourceSchemaMinorVersion < 8
          && node.type === CanvasNodeType.TypeCast
          && (node.configuration.operations ?? []).some((operation) => (
            operation.casts.some((cast) => (
              cast.epochTimestampUnit != null && cast.targetType.type === 'LONG'
            ))
          ))) {
        errors.push(`TYPE_CAST DATE/TIMESTAMP 转 LONG 从 Canvas ${CANVAS_SCHEMA_VERSION}.8 开始支持`);
      }
      if (sourceSchemaMinorVersion < 3
          && node.type === CanvasNodeType.TypeCast
          && (node.configuration.operations ?? []).some((operation) => (
            operation.casts.some((cast) => cast.stringTemporalParseOptions != null)
          ))) {
        errors.push(`TYPE_CAST.stringTemporalParseOptions 从 Canvas ${CANVAS_SCHEMA_VERSION}.3 开始支持`);
      }
      if (sourceSchemaMinorVersion < 7
          && node.type === CanvasNodeType.TypeCast
          && (node.configuration.operations ?? []).some((operation) => (
            operation.casts.some((cast) => cast.temporalStringFormatOptions != null)
          ))) {
        errors.push(`TYPE_CAST.temporalStringFormatOptions 从 Canvas ${CANVAS_SCHEMA_VERSION}.7 开始支持`);
      }
      if (sourceSchemaMinorVersion < 4 && node.type === CanvasNodeType.KafkaInput) {
        const rawNode = (value.nodes as unknown[]).find((candidate) => (
          isRecord(candidate) && candidate.id === node.id
        ));
        const rawConfiguration = isRecord(rawNode) && isRecord(rawNode.configuration)
          ? rawNode.configuration : null;
        if (rawConfiguration
          && (rawConfiguration.valueFormat != null || rawConfiguration.metadataFields != null)) {
          errors.push(`KAFKA_INPUT.valueFormat/metadataFields 从 Canvas ${CANVAS_SCHEMA_VERSION}.4 开始支持`);
        }
      }
      if (sourceSchemaMinorVersion < 5 && node.type === CanvasNodeType.TdEngineTmqInput) {
        const rawNode = (value.nodes as unknown[]).find((candidate) => (
          isRecord(candidate) && candidate.id === node.id
        ));
        const rawConfiguration = isRecord(rawNode) && isRecord(rawNode.configuration)
          ? rawNode.configuration : null;
        if (rawConfiguration
          && (rawConfiguration.eventTimeColumn != null
            || rawConfiguration.watermarkDelaySeconds != null)) {
          errors.push(`TDENGINE_TMQ_INPUT 事件时间配置从 Canvas ${CANVAS_SCHEMA_VERSION}.5 开始支持`);
        }
      }
      if (sourceSchemaMinorVersion < 6 && node.type === CanvasNodeType.KafkaOutput) {
        const rawNode = (value.nodes as unknown[]).find((candidate) => (
          isRecord(candidate) && candidate.id === node.id
        ));
        const rawConfiguration = isRecord(rawNode) && isRecord(rawNode.configuration)
          ? rawNode.configuration : null;
        const rawWrites = rawConfiguration && Array.isArray(rawConfiguration.writes)
          ? rawConfiguration.writes : [];
        if (rawWrites.some((write) => isRecord(write)
          && (Object.prototype.hasOwnProperty.call(write, 'valueFormat')
            || Object.prototype.hasOwnProperty.call(write, 'valueColumnNames')))) {
          errors.push(`KAFKA_OUTPUT.valueFormat/valueColumnNames 从 Canvas ${CANVAS_SCHEMA_VERSION}.6 开始支持`);
        }
      }
    });
  }

  const nodeIds = new Set<string>();
  nodes.forEach((node) => {
    if (nodeIds.has(node.id)) errors.push(`节点 ID ${node.id} 重复`);
    nodeIds.add(node.id);
  });
  const edgeIds = new Set<string>();
  edges.forEach((edge) => {
    if (edgeIds.has(edge.id)) errors.push(`连线 ID ${edge.id} 重复`);
    edgeIds.add(edge.id);
    if (!nodeIds.has(edge.sourceNodeId) || !nodeIds.has(edge.targetNodeId)) {
      errors.push(`连线 ${edge.id} 引用了不存在的节点`);
    }
  });

  return errors.length > 0
    ? { success: false, errors }
    : {
      success: true,
      definition: {
        schemaVersion: CANVAS_SCHEMA_VERSION,
        schemaMinorVersion: CANVAS_SCHEMA_MINOR_VERSION,
        nodes,
        edges,
      },
    };
};

export const parseCanvasDefinitionJson = (content: string): CanvasDefinitionParseResult => {
  try {
    return parseCanvasDefinition(JSON.parse(content) as unknown);
  } catch (error) {
    return {
      success: false,
      errors: [`JSON 解析失败：${error instanceof Error ? error.message : '未知错误'}`],
    };
  }
};

export const formatCanvasDefinition = (definition: CanvasDefinition) => JSON.stringify(definition, null, 2);

export const CANVAS_DEFINITION_FILE_NAME = 'canvas-task-definition.json';

export const downloadCanvasDefinition = (definition: CanvasDefinition) => {
  const blob = new Blob([formatCanvasDefinition(definition)], { type: 'application/json;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = CANVAS_DEFINITION_FILE_NAME;
  anchor.click();
  URL.revokeObjectURL(url);
};
