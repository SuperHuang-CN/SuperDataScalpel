import { parseNodeConfiguration } from './parser';
import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { createTrackFindDwellConfiguration } from './defaults';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeTrackFindDwell } from './summary';
import { createCanvasNodeSpec } from '../specFactory';
import { trackFindDwellCanvasView } from './canvasView';

export const trackFindDwellSpec = createCanvasNodeSpec({
  parseConfiguration: parseNodeConfiguration,
  type: CanvasNodeType.TrackFindDwell,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorSpatial,
  label: '查找驻留',
  description: '从轨迹事件中识别满足空间和时长条件的驻留片段',
  searchKeywords: ['空间', '轨迹', 'dwell', '驻留', '停留'],
  iconKey: CanvasNodeIconKey.TrackFindDwell,
  order: 80,
  canvasView: trackFindDwellCanvasView,
  supportedModes: ['BATCH'],
  introducedInMinor: 16,
  graph: { minInputs: 1, maxInputs: null, minOutputs: 0, maxOutputs: null },
  createDefaultConfiguration: createTrackFindDwellConfiguration,
  summarize: summarizeTrackFindDwell,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
