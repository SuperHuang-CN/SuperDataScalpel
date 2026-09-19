import type { SpatialJoinOneToOneOptions } from '../../canvasTypes';

export const createSpatialJoinOneToOneOptions = (): SpatialJoinOneToOneOptions => ({
  mode: 'SUMMARIZE_MATCHES',
  joinCountColumnName: 'join_count',
  summaryStatistics: [],
  keepRule: {
    strategy: 'FIRST',
    orderByColumnName: null,
    stableOrder: [],
  },
});

export const spatialJoinOneToOneSummary = (
  value: SpatialJoinOneToOneOptions | null | undefined,
) => {
  if (!value?.mode) return '待配置';
  if (value.mode === 'SUMMARIZE_MATCHES') {
    return `汇总 · Join Count + ${value.summaryStatistics?.length ?? 0} 项统计`;
  }
  const strategy = value.keepRule?.strategy ?? '待选择';
  const field = value.keepRule?.orderByColumnName;
  return strategy === 'FIRST' || !field
    ? `保留 · ${strategy}`
    : `保留 · ${strategy}(${field})`;
};
