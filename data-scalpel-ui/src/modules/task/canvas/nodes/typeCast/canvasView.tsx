import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodePreviewList } from '../../components/nodeView/CanvasNodePrimitives';
import { columnTypeText, inputTable, resolvedNodeSize, typeDefinitionText } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.TypeCast>) => {
  const { sourceTableName, outputTableName, casts } = data.configuration;
  if (!sourceTableName) return <NodeEmpty>请选择来源表并配置类型转换</NodeEmpty>;
  const source = inputTable(data, sourceTableName);
  const setNullCount = casts.filter((item) => item.failureStrategy === 'SET_NULL').length;
  return <NodeContent variant="rules">
    <NodeFlow source={sourceTableName} operation="CAST" target={outputTableName} />
    <NodePreviewList items={casts.slice(0, 2).map((item, index) => ({ key: `${index}`, label: `${item.columnName || '字段'} · ${columnTypeText(source, item.columnName)}`, value: '→', meta: `${typeDefinitionText(item.targetType)} · ${item.failureStrategy ?? '?'}` }))} total={casts.length} />
    <NodeBadges><NodeBadge tone="warning">FAIL {casts.length - setNullCount}</NodeBadge><NodeBadge tone="info">SET_NULL {setNullCount}</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const typeCastCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.TypeCast> = {
  resolveSize: (configuration) => resolvedNodeSize({ width: 352, maxHeight: 224, configured: Boolean(configuration.sourceTableName), listCount: configuration.casts.length }),
  Body: body,
};
