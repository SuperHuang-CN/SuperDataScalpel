import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeDualFlow, NodeEmpty, NodePreviewList } from '../../components/nodeView/CanvasNodePrimitives';
import { fieldCountText, inputTable, outputTable, resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.StreamJoin>) => {
  const {
    leftTableName,
    rightTableName,
    outputTableName,
    joinType,
    conditions,
    outputColumns,
  } = data.configuration;
  if (!leftTableName && !rightTableName) return <NodeEmpty>请选择流表和静态维表</NodeEmpty>;
  const stream = inputTable(data, leftTableName);
  const result = outputTable(data, outputTableName);
  return <NodeContent variant="dual">
    <NodeDualFlow left={leftTableName} right={rightTableName} leftLabel="STREAM" rightLabel="LOOKUP" operation={`${joinType ?? '?'} JOIN`} target={outputTableName} />
    <NodePreviewList items={conditions.slice(0, 2).map((item, index) => ({ key: `${index}`, label: item.leftColumnName || '流字段', value: '=', meta: item.rightColumnName || '维字段' }))} total={conditions.length} />
    <NodeBadges><NodeBadge tone="info">STREAM + LOOKUP</NodeBadge><NodeBadge>{stream?.eventTimeColumn ? `事件时间 ${stream.eventTimeColumn}` : '等待事件时间'}</NodeBadge><NodeBadge>{result ? fieldCountText(result) : `${outputColumns.filter((column) => column.included).length} 个输出字段`}</NodeBadge>{result?.watermarkDelay && <NodeBadge>WM {result.watermarkDelay}</NodeBadge>}</NodeBadges>
  </NodeContent>;
};

export const streamJoinCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.StreamJoin> = {
  resolveSize: (configuration) => resolvedNodeSize({ width: 360, maxHeight: 216, emptyHeight: 116, configured: Boolean(configuration.leftTableName || configuration.rightTableName), listCount: configuration.conditions.length }),
  Body: body,
};
