import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodeTitleLine } from '../../components/nodeView/CanvasNodePrimitives';
import { fieldCountText, metadataSourceName, outputTable, resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.TdEngineTmqInput>) => {
  const { dataSourceId, topicName, supertableName, outputTableName, startingOffsets, eventTimeColumn } = data.configuration;
  if (!dataSourceId && !topicName) return <NodeEmpty>请选择 TDengine 数据源和 TMQ Topic</NodeEmpty>;
  const result = outputTable(data, outputTableName);
  return <NodeContent variant="source">
    <NodeTitleLine primary={metadataSourceName(data)} secondary={topicName || '待设置 Topic'} accent />
    <NodeFlow source={supertableName || '超级表'} operation="TMQ" target={outputTableName || '待设置输出表'} />
    <NodeBadges>
      <NodeBadge tone="info">{startingOffsets}</NodeBadge>
      {eventTimeColumn ? <NodeBadge tone="info">事件时间 {eventTimeColumn}</NodeBadge> : null}
      <NodeBadge>{result ? fieldCountText(result) : `${data.summary?.kind === 'TDENGINE_TMQ' ? data.summary.fieldCount : 0} 个字段`}</NodeBadge>
    </NodeBadges>
  </NodeContent>;
};

export const tdEngineTmqInputCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.TdEngineTmqInput> = {
  resolveSize: (configuration) => resolvedNodeSize({ width: 348, maxHeight: 196, configured: Boolean(configuration.dataSourceId || configuration.topicName) }),
  Body: body,
};
