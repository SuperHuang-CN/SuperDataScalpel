import { ClockCircleOutlined, TableOutlined } from '@ant-design/icons';
import { Tooltip } from 'antd';
import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeTitleLine } from '../../components/nodeView/CanvasNodePrimitives';
import { metadataSourceName, resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.JdbcIncrementalInput>) => {
  const configuration = data.configuration;
  if (!configuration.tableName) return <NodeEmpty>请选择数据源和增量表</NodeEmpty>;
  return <NodeContent variant="source">
    <NodeTitleLine primary={metadataSourceName(data)} secondary="时间字段轮询" accent />
    <Tooltip title={configuration.tableName}>
      <div className="canvas-jdbc-input-table"><TableOutlined /><span>{configuration.tableName}</span></div>
    </Tooltip>
    <div className="canvas-jdbc-input-table">
      <ClockCircleOutlined /><span>{configuration.incrementalTimeColumn || '未选择时间字段'}</span>
    </div>
    <NodeBadges>
      <NodeBadge tone="info">{configuration.triggerIntervalSeconds}s</NodeBadge>
      <NodeBadge>延迟 {configuration.visibilityDelaySeconds}s</NodeBadge>
      <NodeBadge>{configuration.startPosition}</NodeBadge>
    </NodeBadges>
  </NodeContent>;
};

export const jdbcIncrementalInputCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.JdbcIncrementalInput> = {
  resolveSize: (configuration) => resolvedNodeSize({
    width: 320, maxHeight: 220, configured: Boolean(configuration.tableName),
  }),
  Body: body,
};
