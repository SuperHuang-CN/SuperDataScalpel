import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";

export const summarizeSpatialJoin = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.SpatialJoin>,
) => {
  const {
    leftTableName,
    rightTableName,
    outputTableName,
    joinType,
    conditions,
    attributeConditions,
    outputColumns,
    joinOperation,
    oneToOne,
    temporalCondition,
    spatialNear,
    distanceOutput,
  } = data.configuration;
  if (!leftTableName || !rightTableName || !outputTableName
      || conditions.length === 0 && !spatialNear) {
    return '请选择两张空间表并配置空间拓扑条件或 Near';
  }
  const predicates = [...new Set(conditions.map((condition) => condition.predicate)
    .filter(Boolean))].join('/');
  const projection = outputColumns == null
    ? '旧版全字段'
    : `${outputColumns.filter((column) => column.included).length} 个输出字段`;
  const attributeSummary = attributeConditions?.length
    ? ` · ${attributeConditions.length} 条属性等值`
    : '';
  const temporalSummary = temporalCondition
    ? ` · 时间 ${temporalCondition.relationship ?? '待配置'}`
    : '';
  const nearSummary = spatialNear
    ? ` · ${spatialNear.distanceMethod === 'GEODESIC' ? 'Near Geodesic' : 'Near'}`
    : '';
  const distanceSummary = distanceOutput?.enabled ? ' · 输出距离' : '';
  const grain = joinOperation !== 'JOIN_ONE_TO_ONE'
    ? '一对多'
    : oneToOne?.mode === 'SUMMARIZE_MATCHES'
      ? `一对一汇总 · Join Count + ${oneToOne.summaryStatistics?.length ?? 0} 项统计`
      : `一对一保留 · ${oneToOne?.keepRule?.strategy ?? '规则待配置'}`;
  return `${leftTableName} ${joinType} ${rightTableName} → ${outputTableName} · ${grain} · ${conditions.length} 条 ${predicates || '拓扑'}${nearSummary}${attributeSummary}${temporalSummary}${distanceSummary} · ${projection}`;
};
