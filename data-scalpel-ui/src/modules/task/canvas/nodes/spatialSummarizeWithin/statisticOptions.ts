import type { SpatialWithinStatistic } from '../../canvasTypes';

export const withinStatisticNeedsSource = (kind: SpatialWithinStatistic['kind']) =>
  !['COUNT', 'LENGTH_WITHIN', 'AREA_WITHIN'].includes(kind);

export const usesExplicitWithinStatistics = (statistics: SpatialWithinStatistic[]) =>
  statistics.some((item) => item.valueTreatment != null || item.weighting != null
    || item.kind === 'COUNT_FIELD' || item.kind === 'ANY');

export const supportsWithinWeighting = (kind: SpatialWithinStatistic['kind']) =>
  kind === 'MEAN' || kind === 'VARIANCE' || kind === 'STDDEV';

export const requiresWithinWeightedDispersionVersion = (item: SpatialWithinStatistic) =>
  item.weighting === 'INTERSECTION_FRACTION' && (item.kind === 'VARIANCE' || item.kind === 'STDDEV');

export const withinWeightedDispersionHelp = 'p=交叠测度/整个来源测度；均值 μ=Σ(p×x)/Σp。方差=Σ[p×(x−μ)²]/[((n−1)/n)×Σp]，标准差为其平方根，n 是所选字段的有效正权重记录数。NULL、非有限数和零权重不计入 n 或权重和；n<2 时方差/标准差为 NULL。按 Enterprise 11.3 公式图实现，该页部分文字算例结果与公式不一致，尚未完成服务结果对照。不支持总量分摊后再加权。';

export const parseWithinStatisticOptions = (
  item: Record<string, unknown>, path: string, errors: string[],
): Pick<SpatialWithinStatistic, 'valueTreatment' | 'weighting'> => {
  const treatment = item.valueTreatment;
  const weighting = item.weighting;
  if (treatment != null && treatment !== 'ORIGINAL_VALUE' && treatment !== 'APPORTION_TOTAL') {
    errors.push(`${path}.valueTreatment 不是受支持的数量处理方式`);
  }
  if (weighting != null && weighting !== 'NONE' && weighting !== 'INTERSECTION_FRACTION') {
    errors.push(`${path}.weighting 不是受支持的加权方式`);
  }
  return {
    ...(treatment !== undefined ? {
      valueTreatment: treatment === 'ORIGINAL_VALUE' || treatment === 'APPORTION_TOTAL' ? treatment : null,
    } : {}),
    ...(weighting !== undefined ? {
      weighting: weighting === 'NONE' || weighting === 'INTERSECTION_FRACTION' ? weighting : null,
    } : {}),
  };
};

export const withinStatisticProblems = (item: SpatialWithinStatistic, lineOrPolygon?: boolean): string[] => {
  const problems: string[] = [];
  const apportioned = item.valueTreatment === 'APPORTION_TOTAL';
  const weighted = item.weighting === 'INTERSECTION_FRACTION';
  if (lineOrPolygon === false && (apportioned || weighted)) problems.push('形状分摊和加权要求线或面');
  if (apportioned && weighted) problems.push('暂不支持分摊后再次加权');
  if (weighted && !supportsWithinWeighting(item.kind)) problems.push('交叠比例加权支持 MEAN、VARIANCE 和 STDDEV');
  if (apportioned && ['COUNT', 'COUNT_FIELD', 'ANY', 'LENGTH_WITHIN', 'AREA_WITHIN'].includes(item.kind)) {
    problems.push('计数、字符串和形状统计不使用总量分摊');
  }
  if (withinStatisticNeedsSource(item.kind) && !item.sourceColumnName?.trim()) problems.push('请选择来源字段');
  if (!withinStatisticNeedsSource(item.kind) && item.sourceColumnName != null) problems.push('此统计不使用来源字段，请清空');
  if (!item.outputColumnName.trim()) problems.push('请输入输出字段名');
  return problems;
};
