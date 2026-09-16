import { parseNodeConfiguration } from './parser';
import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { createTrackMotionStatisticsConfiguration } from './defaults';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeTrackMotionStatistics } from './summary';
import { createCanvasNodeSpec } from '../specFactory';
import { trackMotionStatisticsCanvasView } from './canvasView';

export const trackMotionStatisticsSpec = createCanvasNodeSpec({
  parseConfiguration: parseNodeConfiguration,
  type: CanvasNodeType.TrackMotionStatistics,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorSpatial,
  label: '运动统计',
  description: '按轨迹顺序计算距离、速度、方向等逐点指标',
  searchKeywords: ['空间', '轨迹', 'motion', '速度', '距离', '方向'],
  iconKey: CanvasNodeIconKey.TrackMotionStatistics,
  order: 79,
  canvasView: trackMotionStatisticsCanvasView,
  supportedModes: ['BATCH'],
  introducedInMinor: 15,
  graph: { minInputs: 1, maxInputs: null, minOutputs: 0, maxOutputs: null },
  createDefaultConfiguration: createTrackMotionStatisticsConfiguration,
  summarize: summarizeTrackMotionStatistics,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
