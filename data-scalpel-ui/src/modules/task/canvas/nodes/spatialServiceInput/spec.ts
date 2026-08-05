import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createSpatialServiceInputConfiguration } from '../nodeDefaults';
import { collectSpatialServiceInputMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeSpatialServiceInput } from '../nodeSummaries';
import { createCanvasNodeSpec } from '../specFactory';

export const spatialServiceInputSpec = createCanvasNodeSpec({
  type: CanvasNodeType.SpatialServiceInput,
  category: CanvasNodeCategory.Input,
  group: CanvasNodeGroup.InputApi,
  label: '空间服务输入',
  description: '读取 ArcGIS REST 或 OGC WFS 要素资源',
  searchKeywords: ['arcgis', 'wfs', 'ogc', '空间', '要素'],
  iconKey: CanvasNodeIconKey.Api,
  order: 11,
  defaultSize: { width: 240, height: 120 },
  supportedModes: ['BATCH'],
  introducedInMinor: 26,
  graph: { minInputs: 0, maxInputs: 0, minOutputs: 1, maxOutputs: null },
  createDefaultConfiguration: createSpatialServiceInputConfiguration,
  summarize: summarizeSpatialServiceInput,
  collectMetadataReferences: collectSpatialServiceInputMetadataReferences,
  loadInspector: () => import('./inspector'),
});
