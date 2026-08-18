import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodeTitleLine } from '../../components/nodeView/CanvasNodePrimitives';
import { fieldCountText, metadataSourceName, outputTable, resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.KafkaInput>) => {
  const { dataSourceId, topic, outputTableName, startingOffsets, valueSchema } = data.configuration;
  if (!dataSourceId && !topic) return <NodeEmpty>请选择 Kafka 数据源和 Topic</NodeEmpty>;
  const result = outputTable(data, outputTableName);
  return <NodeContent variant="source">
    <NodeTitleLine primary={metadataSourceName(data)} secondary={topic || '待设置 Topic'} accent />
    <NodeFlow source={topic || 'Topic'} operation="STREAM" target={outputTableName || '待设置输出表'} />
    <NodeBadges><NodeBadge tone="info">{startingOffsets ?? '待设置 Offset'}</NodeBadge><NodeBadge>{result ? fieldCountText(result) : `${valueSchema.columns.length} 个字段`}</NodeBadge>{result?.eventTimeColumn && <NodeBadge tone="strong">事件时间 {result.eventTimeColumn}</NodeBadge>}{result?.watermarkDelay && <NodeBadge>WM {result.watermarkDelay}</NodeBadge>}</NodeBadges>
  </NodeContent>;
};

export const kafkaInputCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.KafkaInput> = {
  resolveSize: (configuration) => resolvedNodeSize({ width: 332, maxHeight: 196, configured: Boolean(configuration.dataSourceId || configuration.topic) }),
  Body: body,
};
