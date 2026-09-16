import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";

export const summarizeFileDatasetInput = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.FileDatasetInput>,
) => {
  if (data.configuration.tables.length === 0) return '请选择文件数据集表';
  if (data.summary?.kind !== 'FILE_DATASET') return `文件表 ${data.configuration.tables.length} 张`;
  const geometry = data.summary.geometry;
  const spatial = geometry
    ? ` · ${geometry.fieldName}: ${geometry.kind} ${geometry.crs.authority}:${geometry.crs.code} ${geometry.dimension}`
    : '';
  return `${data.summary.fileDatasetName} · ${data.configuration.tables.length} 张表 · ${data.summary.status}${spatial}`;
};
