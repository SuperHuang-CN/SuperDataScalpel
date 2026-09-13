import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { createSnapTracksConfiguration } from '../nodeDefaults';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeSnapTracks } from '../nodeSummaries';
import { createCanvasNodeSpec } from '../specFactory';
import { snapTracksCanvasView } from './canvasView';

export const snapTracksSpec = createCanvasNodeSpec({
  type: CanvasNodeType.SnapTracks,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorSpatial,
  label: '吸附轨迹',
  description: '按时间、路网拓扑和可选通行方向，把 Point 轨迹联合匹配到道路网络',
  searchKeywords: ['空间', '轨迹', '路网', '道路', '吸附', '地图匹配', 'snap tracks'],
  iconKey: CanvasNodeIconKey.SnapTracks,
  order: 91,
  canvasView: snapTracksCanvasView,
  supportedModes: ['BATCH'],
  introducedInMinor: 74,
  graph: { minInputs: 1, maxInputs: null, minOutputs: 0, maxOutputs: null },
  createDefaultConfiguration: createSnapTracksConfiguration,
  summarize: summarizeSnapTracks,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
