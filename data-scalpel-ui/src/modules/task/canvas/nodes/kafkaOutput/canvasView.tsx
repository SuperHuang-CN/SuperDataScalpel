import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodePreviewList, NodeTitleLine } from '../../components/nodeView/CanvasNodePrimitives';
import { metadataSourceName, resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.KafkaOutput>) => {
  const { sourceTableName, dataSourceId, topic, valueSchema, keyColumnName, columnMappings } = data.configuration;
  if (!sourceTableName) return <NodeEmpty>请选择来源表和 Kafka Topic</NodeEmpty>;
  return <NodeContent variant="output">
    <NodeTitleLine primary={dataSourceId ? metadataSourceName(data) : '待选择 Kafka 数据源'} secondary={topic || '待设置 Topic'} accent />
    <NodeFlow source={sourceTableName} operation="STREAM" target={topic} />
    <NodePreviewList items={columnMappings.slice(0, 2).map((item, index) => ({ key: `${index}`, label: item.sourceColumnName || '来源字段', value: '→', meta: item.targetColumnName || 'Value 字段' }))} total={columnMappings.length} />
    <NodeBadges><NodeBadge tone="strong">KEY {keyColumnName || '待配置'}</NodeBadge><NodeBadge>{valueSchema.columns.length} 个 Schema 字段</NodeBadge><NodeBadge>{columnMappings.length} 个映射</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const kafkaOutputCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.KafkaOutput> = {
  resolveSize: (configuration) => resolvedNodeSize({ width: 352, maxHeight: 216, emptyHeight: 112, configured: Boolean(configuration.sourceTableName), listCount: configuration.columnMappings.length }),
  Body: body,
};
