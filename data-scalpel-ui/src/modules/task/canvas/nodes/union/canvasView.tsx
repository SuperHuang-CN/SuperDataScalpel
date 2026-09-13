import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeDualFlow, NodeEmpty, NodePreviewList } from '../../components/nodeView/CanvasNodePrimitives';
import { fieldCountText, outputTable, resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.Union>) => {
  const { inputTableNames, outputTableName, mode, mergingTables } = data.configuration;
  if (inputTableNames.length === 0) return <NodeEmpty>请选择至少两张输入表</NodeEmpty>;
  const result = outputTable(data, outputTableName);
  return <NodeContent variant="dual">
    <NodeDualFlow left={inputTableNames[0]} right={inputTableNames[1]} leftLabel="INPUT 1" rightLabel="INPUT 2" operation={mode ?? 'UNION'} target={outputTableName} />
    <NodePreviewList items={inputTableNames.slice(0, 2).map((table, index) => ({ key: `${index}`, label: table || `输入表 ${index + 1}`, value: `#${index + 1}` }))} total={inputTableNames.length} />
    <NodeBadges><NodeBadge tone="strong">{mode ?? '待设置模式'}</NodeBadge><NodeBadge>{mergingTables === null ? '严格 Schema' : 'Merge Layers'}</NodeBadge><NodeBadge>{inputTableNames.length} 张表</NodeBadge><NodeBadge>{fieldCountText(result)}</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const unionCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.Union> = {
  resolveSize: (configuration) => resolvedNodeSize({ width: 336, maxHeight: 204, emptyHeight: 116, configured: configuration.inputTableNames.length > 0, listCount: configuration.inputTableNames.length }),
  Body: body,
};
