import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodeTitleLine } from '../../components/nodeView/CanvasNodePrimitives';
import { fieldCountText, geometryColumn, geometryText, outputTable, resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.FileDatasetInput>) => {
  if (!data.configuration.fileDatasetTableId) return <NodeEmpty>请选择文件数据集表</NodeEmpty>;
  const result = outputTable(data);
  const summary = data.summary?.kind === 'FILE_DATASET' ? data.summary : null;
  const spatial = geometryText(geometryColumn(result)) ?? (summary?.geometry
    ? `${summary.geometry.kind} · ${summary.geometry.crs.authority}:${summary.geometry.crs.code} · ${summary.geometry.dimension}`
    : null);
  return <NodeContent variant="source">
    <NodeTitleLine primary={summary?.fileDatasetName ?? '等待数据集元数据'} secondary={summary ? `${summary.tableName} · ${summary.tableCode}` : undefined} accent />
    <NodeFlow source={summary?.status ?? 'WAITING'} operation="FILE" target={result?.name ?? summary?.tableCode ?? '等待输出表'} />
    <NodeBadges><NodeBadge tone="info">{summary?.datasetType ?? 'FILE'}</NodeBadge><NodeBadge>{summary?.status ?? '等待解析'}</NodeBadge><NodeBadge>{fieldCountText(result)}</NodeBadge>{spatial && <NodeBadge tone="spatial">{spatial}</NodeBadge>}</NodeBadges>
  </NodeContent>;
};

export const fileDatasetInputCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.FileDatasetInput> = {
  resolveSize: (configuration) => resolvedNodeSize({ width: 320, maxHeight: 188, configured: Boolean(configuration.fileDatasetTableId) }),
  Body: body,
};
