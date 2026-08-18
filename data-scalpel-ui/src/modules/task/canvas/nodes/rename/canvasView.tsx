import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodePreviewList } from '../../components/nodeView/CanvasNodePrimitives';
import { resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.Rename>) => {
  const { sourceTableName, outputTableName, columnMappings } = data.configuration;
  if (!sourceTableName) return <NodeEmpty>请选择来源表并设置新名称</NodeEmpty>;
  return <NodeContent variant="rules">
    <NodeFlow source={sourceTableName} operation="RENAME" target={outputTableName} />
    <NodePreviewList items={columnMappings.slice(0, 2).map((item, index) => ({ key: `${index}`, label: item.sourceColumnName || '原字段', value: '→', meta: item.targetColumnName || '新字段' }))} total={columnMappings.length} empty="仅重命名数据表" />
    <NodeBadges><NodeBadge tone={sourceTableName === outputTableName ? 'neutral' : 'strong'}>{sourceTableName === outputTableName ? '表名不变' : '表已重命名'}</NodeBadge><NodeBadge>{columnMappings.length} 个字段</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const renameCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.Rename> = {
  resolveSize: (configuration) => resolvedNodeSize({ width: 336, maxHeight: 210, configured: Boolean(configuration.sourceTableName), listCount: configuration.columnMappings.length }),
  Body: body,
};
