import { CanvasNodeType } from '../../canvasTypes';
import { usesCalendarWindow } from '../spatialCalendarWindow';
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
import { binSizeLabel } from './binSizeSemantics';
import { h3SizeSummary } from './h3';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.SpatialBinAggregate>) => {
  const configuration = data.configuration;
  if (!configuration.sourceTableName) return <NodeEmpty>请选择点表</NodeEmpty>;
  return (
    <NodeContent variant="spatial">
      <NodeFlow
        source={configuration.sourceTableName}
        operation={configuration.binShape === 'H3' ? 'H3 格网' : configuration.binShape === 'HEXAGON' ? '六边形格网' : '方格'}
        target={configuration.outputTableName || '待设置'}
      />
      <NodeHeroMetric value={configuration.statistics.length} label="统计项" />
      <NodeBadges>
        <NodeBadge tone="spatial">
          {configuration.binShape === 'H3' ? h3SizeSummary(configuration) : configuration.binSize > 0 ? `${binSizeLabel(configuration)} ${configuration.binSize} ${configuration.binSizeUnit}` : '待设置大小'}
        </NodeBadge>
        <NodeBadge>{configuration.groupSummary ? '分组' : '不分组'}</NodeBadge>
        <NodeBadge>{configuration.temporalSlicing ? usesCalendarWindow(configuration.temporalSlicing) ? '日历切片' : '固定切片' : '无时间切片'}</NodeBadge>
        {configuration.includeEmptyBins && <NodeBadge>含空格网</NodeBadge>}
        {configuration.binShape !== 'H3' && configuration.planarGrid && <NodeBadge>
          {configuration.planarGrid.extent?.mode === 'EXPLICIT_BOUNDS' ? '业务范围' : '指定原点'}
        </NodeBadge>}
      </NodeBadges>
    </NodeContent>
  );
};

export const spatialBinAggregateCanvasView: CanvasNodeCanvasView<
  typeof CanvasNodeType.SpatialBinAggregate
> = {
  resolveSize: (configuration) => resolvedNodeSize({
    width: 360,
    maxHeight: 224,
    configured: Boolean(configuration.sourceTableName),
  }),
  Body: body,
};
