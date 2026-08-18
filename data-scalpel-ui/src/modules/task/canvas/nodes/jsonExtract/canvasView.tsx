import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodePreviewList } from '../../components/nodeView/CanvasNodePrimitives';
import { resolvedNodeSize, typeDefinitionText } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.JsonExtract>) => {
  const { sourceTableName, outputTableName, sourceColumnName, extractions, failureStrategy } = data.configuration;
  if (!sourceTableName) return <NodeEmpty>请选择 JSON 来源字段</NodeEmpty>;
  return <NodeContent variant="rules">
    <NodeFlow source={`${sourceTableName}.${sourceColumnName || '?'}`} operation="JSON PATH" target={outputTableName} />
    <NodePreviewList items={extractions.slice(0, 2).map((item, index) => ({ key: `${index}`, label: item.jsonPath || 'JSONPath', value: '→', meta: `${item.outputColumnName || '输出字段'} · ${typeDefinitionText(item.targetType)}` }))} total={extractions.length} />
    <NodeBadges><NodeBadge tone="strong">{failureStrategy}</NodeBadge><NodeBadge>{extractions.length} 个提取字段</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const jsonExtractCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.JsonExtract> = {
  resolveSize: (configuration) => resolvedNodeSize({ width: 352, maxHeight: 224, configured: Boolean(configuration.sourceTableName), listCount: configuration.extractions.length }),
  Body: body,
};
