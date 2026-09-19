import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeDualFlow, NodeEmpty, NodePreviewList } from '../../components/nodeView/CanvasNodePrimitives';
import { fieldCountText, outputTable, resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.Join>) => {
  const {
    leftTableName,
    rightTableName,
    outputTableName,
    joinType,
    conditions,
    outputColumns,
  } = data.configuration;
  if (!leftTableName && !rightTableName) return <NodeEmpty>请选择两张输入表</NodeEmpty>;
  const result = outputTable(data, outputTableName);
  return <NodeContent variant="dual">
    <NodeDualFlow left={leftTableName} right={rightTableName} leftLabel="LEFT" rightLabel="RIGHT" operation={`${joinType ?? '?'} JOIN`} target={outputTableName} />
    <NodePreviewList items={conditions.slice(0, 2).map((item, index) => ({ key: `${index}`, label: item.leftColumnName || '左字段', value: '=', meta: item.rightColumnName || '右字段' }))} total={conditions.length} empty="尚未配置 Join 条件" />
    <NodeBadges><NodeBadge tone="strong">{joinType ?? '待设置类型'}</NodeBadge><NodeBadge>{conditions.length} 个条件</NodeBadge><NodeBadge>{result ? fieldCountText(result) : `${outputColumns.filter((column) => column.included).length} 个输出字段`}</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const joinCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.Join> = {
  resolveSize: (configuration) => resolvedNodeSize({ width: 360, maxHeight: 216, emptyHeight: 116, configured: Boolean(configuration.leftTableName || configuration.rightTableName), listCount: configuration.conditions.length }),
  Body: body,
};
