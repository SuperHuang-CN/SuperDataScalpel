import type { SparkJarDevelopmentKitGeneration } from '../model/task';

export const formatSparkJarBytes = (value: number): string => {
  if (value >= 1024 * 1024) return `${(value / 1024 / 1024).toFixed(2)} MiB`;
  if (value >= 1024) return `${(value / 1024).toFixed(2)} KiB`;
  return `${value} B`;
};

export const sparkJarKitStageLabels: Record<SparkJarDevelopmentKitGeneration['stage'], string> = {
  QUEUED: '等待生成',
  VALIDATING: '校验定义',
  COUNTING: '统计数据量',
  EXPORTING: '导出样例',
  PACKAGING: '组装工程',
  UPLOADING: '上传制品',
  COMPLETED: '生成完成',
  FAILED: '生成失败',
  EXPIRED: '已过期',
};
