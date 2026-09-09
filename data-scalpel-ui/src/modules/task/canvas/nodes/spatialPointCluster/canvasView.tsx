import { CanvasNodeType } from '../../canvasTypes';
import {
  NodeBadge,
  NodeBadges,
  NodeContent,
  NodeEmpty,
  NodeFlow,
  NodeHeroMetric,
} from '../../components/nodeView/CanvasNodePrimitives';
import { resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';
import { dbscanModeLabel } from './dbscanOptions';

const algorithmLabel = {
  DBSCAN: 'DBSCAN',
  HDBSCAN: 'HDBSCAN',
  MULTI_SCALE: 'Multi-scale（暂不可用）',
} as const;

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.SpatialPointCluster>) => {
  const configuration = data.configuration;
  if (!configuration.sourceTableName) return <NodeEmpty>请选择点表</NodeEmpty>;
  return (
    <NodeContent variant="spatial">
      <NodeFlow
        source={configuration.sourceTableName}
        operation={configuration.parameters.algorithm === 'DBSCAN' ? dbscanModeLabel(configuration) : algorithmLabel[configuration.parameters.algorithm]}
        target={configuration.outputTableName || '待设置'}
      />
      <NodeHeroMetric value={configuration.parameters.minimumFeatures} label="最少要素" />
      <NodeBadges>
        <NodeBadge tone="spatial">
          {configuration.distanceMethod === 'GEODESIC' ? '测地线' : '平面'}
        </NodeBadge>
        {configuration.parameters.algorithm === 'DBSCAN' && (
          <NodeBadge>
            {configuration.parameters.searchDistance > 0
              ? `${configuration.parameters.searchDistance} ${configuration.parameters.searchDistanceUnit}`
              : '待设置搜索距离'}
          </NodeBadge>
        )}
        <NodeBadge>{configuration.clusterIdColumnName || '待设置簇字段'}</NodeBadge>
        <NodeBadge>保留噪声点</NodeBadge>
        {configuration.parameters.algorithm === 'HDBSCAN' && <NodeBadge>{configuration.hdbscan ? '4 项诊断' : '诊断待配置'}</NodeBadge>}
      </NodeBadges>
    </NodeContent>
  );
};

export const spatialPointClusterCanvasView: CanvasNodeCanvasView<
  typeof CanvasNodeType.SpatialPointCluster
> = {
  resolveSize: (configuration) => resolvedNodeSize({
    width: 352,
    maxHeight: 216,
    configured: Boolean(configuration.sourceTableName),
  }),
  Body: body,
};
