import { CanvasNodeType } from '../../canvasTypes';
import {
  NodeBadge, NodeBadges, NodeContent, NodeDualFlow, NodeEmpty, NodePreviewList,
} from '../../components/nodeView/CanvasNodePrimitives';
import { resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.SpatialEnrichFromGrid>) => {
  const configuration = data.configuration;
  if (!configuration.pointTableName && !configuration.gridTableName) {
    return <NodeEmpty>请选择 Point 表和多变量格网</NodeEmpty>;
  }
  return <NodeContent variant="spatial">
    <NodeDualFlow
      left={configuration.pointTableName || '待选择'}
      right={configuration.gridTableName || '待选择'}
      leftLabel="Point"
      rightLabel="变量格网"
      operation="相交回填"
      target={configuration.outputTableName || '待设置'}
    />
    <NodePreviewList items={configuration.enrichFields.map((field, index) => ({
      key: `${field.sourceColumnName}-${index}`,
      label: field.sourceColumnName || '待选择字段',
      value: '→',
      meta: field.outputColumnName || '待设置结果字段',
    }))} total={configuration.enrichFields.length} moreLabel={count => `另 ${count} 个字段`} />
    <NodeBadges>
      <NodeBadge tone="spatial">Point 保持粒度</NodeBadge>
      <NodeBadge>{configuration.enrichFields.length} 个丰富字段</NodeBadge>
      <NodeBadge tone="info">未命中保留</NodeBadge>
    </NodeBadges>
  </NodeContent>;
};

export const spatialEnrichFromGridCanvasView: CanvasNodeCanvasView<
  typeof CanvasNodeType.SpatialEnrichFromGrid
> = {
  resolveSize: configuration => resolvedNodeSize({
    width: 384,
    maxHeight: 232,
    configured: Boolean(configuration.pointTableName || configuration.gridTableName),
    listCount: configuration.enrichFields.length,
  }),
  Body: body,
};
