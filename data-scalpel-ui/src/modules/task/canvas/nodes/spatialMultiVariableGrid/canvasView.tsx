import { CanvasNodeType } from '../../canvasTypes';
import {
  NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodeHeroMetric, NodePreviewList,
} from '../../components/nodeView/CanvasNodePrimitives';
import { resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const kindLabels = {
  DISTANCE_TO_NEAREST: '最近距离',
  ATTRIBUTE_OF_NEAREST: '最近属性',
  ATTRIBUTE_SUMMARY_OF_RELATED: '关联汇总',
} as const;

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.SpatialMultiVariableGrid>) => {
  const configuration = data.configuration;
  if (configuration.variables.length === 0) return <NodeEmpty>请添加格网变量</NodeEmpty>;
  const sourceCount = new Set(configuration.variables.map(variable => variable.sourceTableName)
    .filter(Boolean)).size;
  return <NodeContent variant="spatial">
    <NodeFlow source={`${sourceCount} 张来源表`} operation="统一格网"
      target={configuration.outputTableName || '待设置'} />
    <NodePreviewList items={configuration.variables.map(variable => ({
      key: variable.variableId,
      label: variable.sourceTableName || '待选择来源',
      value: '→',
      meta: `${variable.outputColumnName || '待设置'} · ${variable.kind ? kindLabels[variable.kind] : '待配置'}`,
    }))} total={configuration.variables.length} moreLabel={count => `另 ${count} 个变量`} />
    <NodeHeroMetric value={configuration.variables.length} label="格网变量" />
    <NodeBadges>
      <NodeBadge tone="spatial">{configuration.binShape === 'HEXAGON' ? '六边形' : '方格'} {configuration.binSize} {configuration.binSizeUnit}</NodeBadge>
      <NodeBadge>{configuration.variables.filter(variable => variable.searchDistance != null).length} 个半径搜索</NodeBadge>
      <NodeBadge>{configuration.variables.filter(variable => variable.filter != null).length} 个筛选</NodeBadge>
    </NodeBadges>
  </NodeContent>;
};

export const spatialMultiVariableGridCanvasView: CanvasNodeCanvasView<
  typeof CanvasNodeType.SpatialMultiVariableGrid
> = {
  resolveSize: configuration => resolvedNodeSize({
    width: 384,
    maxHeight: 244,
    configured: configuration.variables.length > 0,
    listCount: configuration.variables.length,
  }),
  Body: body,
};
