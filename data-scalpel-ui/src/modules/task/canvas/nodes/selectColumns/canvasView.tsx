import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodePreviewList } from '../../components/nodeView/CanvasNodePrimitives';
import { inputTable, resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.SelectColumns>) => {
  const { sourceTableName, outputTableName, columns } = data.configuration;
  if (!sourceTableName) return <NodeEmpty>请选择来源表和保留字段</NodeEmpty>;
  const source = inputTable(data, sourceTableName);
  return <NodeContent variant="rules">
    <NodeFlow source={sourceTableName} operation="SELECT" target={outputTableName} />
    <NodePreviewList items={columns.slice(0, 2).map((column, index) => ({ key: `${index}`, label: column || '字段', value: '保留', meta: `序号 ${index + 1}` }))} total={columns.length} />
    <NodeBadges><NodeBadge tone="strong">保留 {columns.length} / {source?.columns.length ?? '?'}</NodeBadge><NodeBadge>按配置顺序输出</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const selectColumnsCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.SelectColumns> = {
  resolveSize: (configuration) => resolvedNodeSize({ width: 336, maxHeight: 210, configured: Boolean(configuration.sourceTableName), listCount: configuration.columns.length }),
  Body: body,
};
