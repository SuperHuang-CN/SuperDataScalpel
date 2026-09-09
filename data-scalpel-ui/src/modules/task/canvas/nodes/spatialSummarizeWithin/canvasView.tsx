import { CanvasNodeType } from '../../canvasTypes';
import { usesCalendarWindow } from '../spatialCalendarWindow';
import { usesLinkedWithinGroups } from './groupResult';
import { usesGridRegions } from './regions';
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

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.SpatialSummarizeWithin>) => {
  const configuration = data.configuration;
  if ((!usesGridRegions(configuration) && !configuration.areaTableName) || !configuration.summaryTableName) {
    return <NodeEmpty>请选择区域表和被汇总要素表</NodeEmpty>;
  }
  return (
    <NodeContent variant="spatial">
      <NodeFlow
        source={usesGridRegions(configuration) ? (configuration.regions?.binShape === 'HEXAGON' ? '六边形区域' : '方格区域') : configuration.areaTableName}
        operation="WITHIN"
        target={configuration.summaryTableName}
      />
      <NodeHeroMetric value={configuration.statistics.length} label="统计项" />
      <NodeFlow source="汇总" operation="→" target={configuration.outputTableName || '待设置'} />
      {usesLinkedWithinGroups(configuration) && <NodeFlow source="分组" operation="→"
        target={configuration.groupResult?.outputTableName || '待设置'} />}
      <NodeBadges>
        <NodeBadge tone="spatial">
          {configuration.distanceMethod === 'GEODESIC' ? '测地线' : '平面'}
        </NodeBadge>
        <NodeBadge>{configuration.includeEmptyAreas ? '保留空区域' : '仅命中区域'}</NodeBadge>
        {configuration.statistics.some((item) => item.valueTreatment === 'APPORTION_TOTAL') && <NodeBadge>
          分摊 {configuration.statistics.filter((item) => item.valueTreatment === 'APPORTION_TOTAL').length} 项
        </NodeBadge>}
        {configuration.statistics.some((item) => item.weighting === 'INTERSECTION_FRACTION') && <NodeBadge>
          加权 {configuration.statistics.filter((item) => item.weighting === 'INTERSECTION_FRACTION').length} 项
        </NodeBadge>}
        {configuration.groupSummary && <NodeBadge>{usesLinkedWithinGroups(configuration) ? '关联双表' : '扁平分组'}</NodeBadge>}
        {configuration.temporalSlicing && <NodeBadge>{usesCalendarWindow(configuration.temporalSlicing) ? '日历切片' : '固定切片'}</NodeBadge>}
      </NodeBadges>
    </NodeContent>
  );
};

export const spatialSummarizeWithinCanvasView: CanvasNodeCanvasView<
  typeof CanvasNodeType.SpatialSummarizeWithin
> = {
  resolveSize: (configuration) => resolvedNodeSize({
    width: 376,
    maxHeight: (configuration.statistics.some((item) => item.valueTreatment === 'APPORTION_TOTAL'
      || item.weighting === 'INTERSECTION_FRACTION') ? 248 : 224) + (usesLinkedWithinGroups(configuration) ? 32 : 0),
    configured: Boolean((usesGridRegions(configuration) || configuration.areaTableName) && configuration.summaryTableName),
  }),
  Body: body,
};
