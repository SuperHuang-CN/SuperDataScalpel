import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createSpatialMeasureConfiguration } from '../nodeDefaults';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeSpatialMeasure } from '../nodeSummaries';
import { createCanvasNodeSpec } from '../specFactory';

export const spatialMeasureSpec = createCanvasNodeSpec({
  type: CanvasNodeType.SpatialMeasure,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorSpatial,
  label: '空间测量',
  description: '计算 Geometry 的面积、长度、周长、距离或坐标',
  searchKeywords: ['空间', 'geometry', 'measure', 'area', 'length', 'distance', '面积', '距离'],
  iconKey: CanvasNodeIconKey.SpatialMeasure,
  order: 70,
  defaultSize: { width: 250, height: 120 },
  supportedModes: ['BATCH', 'STREAMING'],
  introducedInMinor: 21,
  graph: { minInputs: 1, maxInputs: 1, minOutputs: 1, maxOutputs: null },
  createDefaultConfiguration: createSpatialMeasureConfiguration,
  summarize: summarizeSpatialMeasure,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
