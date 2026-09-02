import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFieldCount, NodePreviewList, NodeTitleLine } from '../../components/nodeView/CanvasNodePrimitives';
import { geometryColumn, geometryText, resourceListNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.FileDatasetInput>) => {
  if (data.configuration.tables.length === 0) return <NodeEmpty>请选择一个或多个文件数据集表</NodeEmpty>;
  const summary = data.summary?.kind === 'FILE_DATASET' ? data.summary : null;
  const items = data.configuration.tables.map((selection, index) => {
    const metadataTable = summary?.tables?.find(
      (table) => table.fileDatasetTableId === selection.fileDatasetTableId,
    );
    const compiledTable = data.compilation?.outputTables.find(
      (table) => table.origin?.kind === 'FILE_DATASET'
        && table.origin.fileDatasetTableId === selection.fileDatasetTableId,
    ) ?? data.compilation?.outputTables[index];
    const table = compiledTable ?? metadataTable?.schema;
    return {
      key: selection.fileDatasetTableId,
      label: table?.name ?? metadataTable?.tableCode
        ?? (index === 0 ? summary?.tableCode : null)
        ?? selection.fileDatasetTableId.slice(0, 8),
      value: geometryText(geometryColumn(table)) ?? metadataTable?.datasetType
        ?? (index === 0 ? summary?.datasetType : undefined),
      meta: <NodeFieldCount table={table} unresolvedLabel="字段加载中" />,
    };
  });
  return <NodeContent variant="source">
    <NodeTitleLine primary={summary?.fileDatasetName ?? '等待数据集元数据'} secondary={`${data.configuration.tables.length} 个逻辑表`} accent />
    <NodePreviewList items={items} total={items.length} limit={3} moreLabel={(remaining) => `另 ${remaining} 张表`} />
    <NodeBadges><NodeBadge tone="info">{summary?.datasetType ?? 'FILE'}</NodeBadge><NodeBadge tone="strong">{items.length} 张表</NodeBadge><NodeBadge>{summary?.status ?? 'WAITING'}</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const fileDatasetInputCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.FileDatasetInput> = {
  resolveSize: (configuration) => resourceListNodeSize({ width: 344, count: configuration.tables.length }),
  Body: body,
};
