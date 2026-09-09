import {
  CanvasNodeType,
  type GeometrySimplifyAlgorithm,
} from '../../canvasTypes';
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

const algorithmLabels: Record<GeometrySimplifyAlgorithm, string> = {
  DOUGLAS_PEUCKER: 'Douglas-Peucker',
  TOPOLOGY_PRESERVING: '拓扑保持',
};

import { spatialDistanceUnitLabels as unitLabels } from '../spatialUnits';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.GeometrySimplify>) => {
  const configuration = data.configuration;
  if (!configuration.sourceTableName) {
    return <NodeEmpty>请选择 Geometry 字段并设置简化规则</NodeEmpty>;
  }
  const algorithm = configuration.algorithm
    ? algorithmLabels[configuration.algorithm]
    : '待选择算法';
  return (
    <NodeContent variant="spatial">
      <NodeFlow
        source={`${configuration.sourceTableName}.${configuration.geometryColumnName || '?'}`}
        operation="SIMPLIFY"
        target={`${configuration.outputTableName || '?'}.${configuration.outputColumnName || '?'}`}
      />
      <NodeHeroMetric
        value={configuration.tolerance == null ? '待填容差' : `${configuration.tolerance} ${configuration.toleranceUnit ? unitLabels[configuration.toleranceUnit] : '待选单位'}`}
        label="简化容差"
      />
      <NodeBadges>
        <NodeBadge tone="spatial">{algorithm}</NodeBadge>
        <NodeBadge>保留来源字段</NodeBadge>
      </NodeBadges>
    </NodeContent>
  );
};

export const geometrySimplifyCanvasView: CanvasNodeCanvasView<
  typeof CanvasNodeType.GeometrySimplify
> = {
  resolveSize: (configuration) => resolvedNodeSize({
    width: 332,
    maxHeight: 196,
    configured: Boolean(configuration.sourceTableName),
  }),
  Body: body,
};
