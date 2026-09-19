import { CanvasNodeType, type WindowFunctionItem } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodePreviewList, NodeSplit } from '../../components/nodeView/CanvasNodePrimitives';
import { resolvedNodeSize, sortText } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const functionMeta = (item: WindowFunctionItem): string => {
  if ('frame' in item) return `${item.frame.start.kind} → ${item.frame.end.kind}`;
  if ('offset' in item) return `OFFSET ${item.offset}`;
  return 'WINDOW';
};

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.Window>) => {
  const { sourceTableName, outputTableName, partitionByColumns, orderBy, functions } = data.configuration;
  if (!sourceTableName) return <NodeEmpty>请选择来源表并配置窗口函数</NodeEmpty>;
  return <NodeContent variant="aggregate">
    <NodeFlow source={sourceTableName} operation="WINDOW" target={outputTableName} />
    <NodeSplit leftLabel="PARTITION" left={partitionByColumns.slice(0, 2).join(', ') || '全局'} rightLabel="ORDER" right={sortText(orderBy[0])} />
    <NodePreviewList items={functions.slice(0, 2).map((item, index) => ({ key: `${index}`, label: item.kind, value: '→', meta: `${item.outputColumnName || '输出字段'} · ${functionMeta(item)}` }))} total={functions.length} />
    <NodeBadges><NodeBadge tone="strong">{partitionByColumns.length} 个分区字段</NodeBadge><NodeBadge>{orderBy.length} 个排序</NodeBadge><NodeBadge>{functions.length} 个函数</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const windowCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.Window> = {
  resolveSize: (configuration) => resolvedNodeSize({ width: 368, maxHeight: 240, configured: Boolean(configuration.sourceTableName), listCount: configuration.functions.length }),
  Body: body,
};
