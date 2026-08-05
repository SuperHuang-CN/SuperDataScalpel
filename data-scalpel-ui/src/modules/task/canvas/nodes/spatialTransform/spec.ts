import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createSpatialTransformConfiguration } from '../nodeDefaults';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeSpatialTransform } from '../nodeSummaries';
import { createCanvasNodeSpec } from '../specFactory';

export const spatialTransformSpec = createCanvasNodeSpec({
  type: CanvasNodeType.SpatialTransform,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorSpatial,
  label: '空间转换',
  description: '显式转换 Geometry 字段的 EPSG 坐标系',
  searchKeywords: ['空间', 'geometry', 'transform', 'crs', 'epsg', '坐标转换'],
  iconKey: CanvasNodeIconKey.SpatialTransform,
  order: 20,
  defaultSize: { width: 250, height: 120 },
  supportedModes: ['BATCH'],
  introducedInMinor: 20,
  graph: { minInputs: 1, maxInputs: 1, minOutputs: 1, maxOutputs: null },
  createDefaultConfiguration: createSpatialTransformConfiguration,
  summarize: summarizeSpatialTransform,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
