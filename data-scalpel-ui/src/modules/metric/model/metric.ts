import type { PageResponse } from '../../../shared/api/pageResponse';
export type MetricKind = 'ATOMIC' | 'DERIVED' | 'COMPOSITE';
export type MetricStatus = 'DRAFT' | 'PUBLISHED' | 'DISABLED';
export type ResourceKind = 'MODEL' | 'MODEL_FIELD' | 'METRIC' | 'DATA_SERVICE';
export interface MetricReference { resourceKind: ResourceKind; resourceId: string; parentModelId?: string | null; targetVersion?: number | null; role?: string | null; note?: string | null }
export interface MetricDimension { key: string; name: string; description?: string | null; fieldId: string }
export interface MetricFilter { fieldId: string; operator: 'EQ' | 'NE' | 'GT' | 'GE' | 'LT' | 'LE' | 'IS_NULL' | 'IS_NOT_NULL'; value?: string | null }
export interface MetricBinding { modelId: string | null; valueFieldId: string | null; periodFieldId: string | null; dimensions: MetricDimension[]; supportingFieldIds: string[]; fixedFilters: MetricFilter[] }
export interface MetricDefinition {
 businessMeaning: string | null; calculation: string | null; statisticalScope: string | null; timeDescription: string | null;
 sourceGrain: string | null; grainDescription: string | null; unit: string | null;
 statisticalPeriod: 'NONE' | 'DAY' | 'WEEK' | 'MONTH' | 'QUARTER' | 'YEAR'; periodFormat: string | null;
 nullHandling: string | null; aggregationDescription: string | null; updateDescription: string | null;
 decimalPlaces: number; valueFormat: 'NUMBER' | 'RATIO' | 'PERCENT_VALUE'; binding: MetricBinding | null; references: MetricReference[];
}
export interface MetricIssue { code: string; path: string; message: string; blocking: boolean }
export interface MetricHealth { canPublish: boolean; bindingStatus: 'UNBOUND' | 'VALID' | 'INVALID'; issues: MetricIssue[] }
export interface MetricSnapshot { path: string; resourceKind: ResourceKind; resourceId: string; parentModelId: string | null; targetVersion: number | null; name: string | null; code: string | null; status: string | null; contract: string | null; resultBinding: boolean }
export interface MetricBasics { name: string; kind: MetricKind; directoryId: string | null; ownerName: string | null; summary: string | null }
export interface Metric extends MetricBasics { id: string; code: string; status: MetricStatus; publishedVersion: number | null; hasDraftChanges: boolean; definition: MetricDefinition; health: MetricHealth; references: MetricSnapshot[]; createdAt: string; updatedAt: string }
export interface MetricDraft { metricId: string; definition: MetricDefinition; fingerprint: string; health: MetricHealth; references: MetricSnapshot[] }
export interface MetricVersion { id: string; metricId: string; version: number; definition: MetricDefinition; references: MetricSnapshot[]; publishedAt: string; publishedBy: string; changeNote: string | null }
export interface MetricLocation { role: string; referenceType: string; nodeId: string | null; nodeName: string | null }
export interface MetricTask { taskId: string; taskName: string; taskType: string; taskStatus: string; definitionVersion: number; locations: MetricLocation[] }
export interface TaskMetricRelations { taskId: string; definitionVersion: number | null; resolution: string; metrics: PageResponse<Metric>; outputModels: { modelId: string; modelName: string; modelCode: string; locations: MetricLocation[] }[]; fieldEvidence: { definitionVersion: number; currentDefinition: boolean; fieldIds: string[]; source: string }[] }
export interface MetricFieldCandidate { id: string; code: string; name: string; fieldType: string }
export const metricKindLabels: Record<MetricKind, string> = { ATOMIC: '原子指标', DERIVED: '派生指标', COMPOSITE: '复合指标' };
export const metricStatusLabels: Record<MetricStatus, string> = { DRAFT: '草稿', PUBLISHED: '已发布', DISABLED: '已停用' };
export const bindingLabels = { UNBOUND: '未绑定结果', VALID: '元数据有效', INVALID: '需修订绑定' };
export const periodLabels: Record<MetricDefinition['statisticalPeriod'], string> = { NONE: '无周期', DAY: '日度', WEEK: '周度', MONTH: '月度', QUARTER: '季度', YEAR: '年度' };
export const metricDefinitionLabels: Partial<Record<keyof MetricDefinition, string>> = { businessMeaning: '业务含义', calculation: '计算口径', statisticalScope: '统计范围', timeDescription: '时间口径', sourceGrain: '来源粒度', grainDescription: '结果粒度', unit: '单位', statisticalPeriod: '统计周期', periodFormat: '时间格式', nullHandling: '空值与零值', aggregationDescription: '汇总说明', updateDescription: '更新说明', decimalPlaces: '小数位数', valueFormat: '数值显示', binding: '结果绑定', references: '参考资料' };
export const resourceLabels: Record<ResourceKind, string> = { MODEL: '来源模型', MODEL_FIELD: '来源字段', METRIC: '相关指标', DATA_SERVICE: '数据服务' };
export const referenceHref = (r: MetricSnapshot) => r.resourceKind === 'MODEL' ? `/model/${r.resourceId}` : r.resourceKind === 'MODEL_FIELD' ? `/model/${r.parentModelId}` : r.resourceKind === 'METRIC' ? `/metrics/${r.resourceId}` : `/dataservice/${r.resourceId}`;

export const metricLocationLabel = (location: MetricLocation): string => {
  switch (location.referenceType) {
    case 'LOCAL_SQL_OUTPUT': return 'LOCAL_SQL 输出声明';
    case 'CANVAS_NODE': return `Canvas 输出节点${location.nodeName ? ` · ${location.nodeName}` : ''}`;
    case 'SPARK_JAR_RESOURCE_BINDING': return 'Spark JAR 写权限声明';
    default: return '输出模型声明';
  }
};
