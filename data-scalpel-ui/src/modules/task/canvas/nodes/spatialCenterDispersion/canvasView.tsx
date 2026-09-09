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

const kindLabels = {
  MEAN_CENTER: '平均中心',
  MEDIAN_CENTER: '中位中心',
  CENTRAL_FEATURE: '中央要素',
  STANDARD_DISTANCE: '标准距离',
  DIRECTIONAL_ELLIPSE: '方向椭圆',
} as const;

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.SpatialCenterDispersion>) => {
  const configuration = data.configuration;
  if (!configuration.sourceTableName) return <NodeEmpty>请选择来源 Geometry</NodeEmpty>;
  const separate = configuration.resultMode === 'ANALYSIS_TABLES';
  return (
    <NodeContent variant="spatial">
      {separate ? configuration.analyses.slice(0, 2).map(a => <NodeFlow key={a.analysisId} source={kindLabels[a.kind]} operation="→" target={a.outputTableName || '待设置'} />)
        : <NodeFlow source={configuration.sourceTableName} operation="空间统计" target={configuration.outputTableName || '待设置'} />}
      <NodeHeroMetric value={configuration.analyses.length} label="分析项" />
      <NodeBadges>
        {configuration.analyses.slice(0, 2).map((analysis) => (
          <NodeBadge tone="spatial" key={analysis.analysisId}>{kindLabels[analysis.kind]}</NodeBadge>
        ))}
        {configuration.analyses.length > 2 && <NodeBadge>另 {configuration.analyses.length - 2} 项</NodeBadge>}
        <NodeBadge>{configuration.groupByColumns.length > 0
          ? `${configuration.groupByColumns.length} 个分组字段` : '全局统计'}</NodeBadge>
        {configuration.weightColumnName && <NodeBadge>加权</NodeBadge>}
        <NodeBadge>{separate ? '独立结果表' : '旧版宽表'}</NodeBadge>
        {separate && configuration.analyses.filter(a => a.kind === 'CENTRAL_FEATURE' && a.centralFeatureColumns != null).map(a =>
          <NodeBadge key={`fields-${a.analysisId}`}>原字段 {a.centralFeatureColumns?.filter(c => c.included).length}</NodeBadge>)}
      </NodeBadges>
    </NodeContent>
  );
};

export const spatialCenterDispersionCanvasView: CanvasNodeCanvasView<
  typeof CanvasNodeType.SpatialCenterDispersion
> = {
  resolveSize: (configuration) => resolvedNodeSize({
    width: 368,
    maxHeight: 224,
    configured: Boolean(configuration.sourceTableName),
    listCount: configuration.resultMode === 'ANALYSIS_TABLES' ? configuration.analyses.length : undefined,
  }),
  Body: body,
};
