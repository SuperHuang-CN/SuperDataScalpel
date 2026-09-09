import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { createTrackDetectIncidentsConfiguration } from '../nodeDefaults';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeTrackDetectIncidents } from '../nodeSummaries';
import { createCanvasNodeSpec } from '../specFactory';
import { trackDetectIncidentsCanvasView } from './canvasView';

export const trackDetectIncidentsSpec = createCanvasNodeSpec({
  type: CanvasNodeType.TrackDetectIncidents,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorSpatial,
  label: '检测轨迹事件',
  description: '按受控条件识别轨迹中的事件区间',
  searchKeywords: ['空间', '轨迹', 'incident', '事件', '检测'],
  iconKey: CanvasNodeIconKey.TrackDetectIncidents,
  order: 81,
  canvasView: trackDetectIncidentsCanvasView,
  supportedModes: ['BATCH'],
  introducedInMinor: 17,
  graph: { minInputs: 1, maxInputs: null, minOutputs: 0, maxOutputs: null },
  createDefaultConfiguration: createTrackDetectIncidentsConfiguration,
  summarize: summarizeTrackDetectIncidents,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
