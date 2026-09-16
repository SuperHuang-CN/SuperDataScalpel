import { parseNodeConfiguration } from './parser';
import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { createTrackReconstructConfiguration } from './defaults';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeTrackReconstruct } from './summary';
import { createCanvasNodeSpec } from '../specFactory';
import { trackReconstructCanvasView } from './canvasView';

export const trackReconstructSpec = createCanvasNodeSpec({
  parseConfiguration: parseNodeConfiguration,
  type: CanvasNodeType.TrackReconstruct,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorSpatial,
  label: '轨迹重建',
  description: '按实体和时间将观测重建为轨迹线或平面轨迹面',
  searchKeywords: ['空间', '轨迹', 'track', 'reconstruct', '线路'],
  iconKey: CanvasNodeIconKey.TrackReconstruct,
  order: 78,
  canvasView: trackReconstructCanvasView,
  supportedModes: ['BATCH'],
  introducedInMinor: 14,
  graph: { minInputs: 1, maxInputs: null, minOutputs: 0, maxOutputs: null },
  createDefaultConfiguration: createTrackReconstructConfiguration,
  summarize: summarizeTrackReconstruct,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
