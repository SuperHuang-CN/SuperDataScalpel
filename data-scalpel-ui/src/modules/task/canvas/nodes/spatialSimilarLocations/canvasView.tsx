import { CanvasNodeType } from '../../canvasTypes';
import {
  NodeBadge,
  NodeBadges,
  NodeContent,
  NodeEmpty,
  NodeFlow,
  NodeHeroMetric,
  NodePreviewList,
} from '../../components/nodeView/CanvasNodePrimitives';
import { resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const methodLabels = {
  ATTRIBUTE_VALUES: '属性值',
  ATTRIBUTE_PROFILES: '属性轮廓',
} as const;

const resultLabels = {
  MOST_SIMILAR: '最相似',
  LEAST_SIMILAR: '最不相似',
  BOTH: '两端',
} as const;

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.SpatialSimilarLocations>) => {
  const configuration = data.configuration;
  if (!configuration.referenceTableName && !configuration.candidateTableName) {
    return <NodeEmpty>请选择参考位置和候选位置</NodeEmpty>;
  }
  return <NodeContent variant="spatial">
    <NodeFlow
      source={`${configuration.referenceTableName || '参考待选'} + ${configuration.candidateTableName || '候选待选'}`}
      operation="属性匹配"
      target={configuration.outputTableName || '待设置'}
    />
    <NodePreviewList items={configuration.analysisFields.map((field) => ({
      key: field.columnName,
      label: field.columnName || '待选择字段',
      value: '→',
      meta: field.outputColumnName || '待设置结果字段',
    }))} total={configuration.analysisFields.length} moreLabel={(count) => `另 ${count} 个分析字段`} />
    <NodeHeroMetric value={configuration.numberOfResults || '—'} label="每端候选结果" />
    <NodeBadges>
      <NodeBadge tone="spatial">
        {configuration.matchMethod ? methodLabels[configuration.matchMethod] : '方法待选'}
      </NodeBadge>
      <NodeBadge tone="strong">
        {configuration.resultMode ? resultLabels[configuration.resultMode] : '范围待选'}
      </NodeBadge>
      <NodeBadge>{configuration.analysisFields.length} 个分析字段</NodeBadge>
      <NodeBadge>{configuration.appendFields.length} 个附加字段</NodeBadge>
      {(configuration.referenceFilter || configuration.candidateFilter)
        && <NodeBadge>{Number(configuration.referenceFilter != null) + Number(configuration.candidateFilter != null)} 侧筛选</NodeBadge>}
    </NodeBadges>
  </NodeContent>;
};

export const spatialSimilarLocationsCanvasView: CanvasNodeCanvasView<
  typeof CanvasNodeType.SpatialSimilarLocations
> = {
  resolveSize: (configuration) => resolvedNodeSize({
    width: 392,
    maxHeight: 244,
    configured: Boolean(configuration.referenceTableName || configuration.candidateTableName),
    listCount: configuration.analysisFields.length,
  }),
  Body: body,
};
