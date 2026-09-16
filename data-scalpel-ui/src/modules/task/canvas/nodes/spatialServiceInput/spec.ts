import { parseNodeConfiguration } from './parser';
import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createSpatialServiceInputConfiguration } from './defaults';
import { collectSpatialServiceInputMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeSpatialServiceInput } from './summary';
import { createCanvasNodeSpec } from '../specFactory';
import { spatialServiceInputCanvasView } from './canvasView';

export const spatialServiceInputSpec = createCanvasNodeSpec({
  parseConfiguration: parseNodeConfiguration,
  type: CanvasNodeType.SpatialServiceInput,
  category: CanvasNodeCategory.Input,
  group: CanvasNodeGroup.InputApi,
  label: '空间服务输入',
  description: '读取 ArcGIS REST 或 OGC WFS 要素资源',
  searchKeywords: ['arcgis', 'wfs', 'ogc', '空间', '要素'],
  iconKey: CanvasNodeIconKey.Api,
  order: 11,
  canvasView: spatialServiceInputCanvasView,
  supportedModes: ['BATCH'],
  introducedInMinor: 0,
  graph: { minInputs: 0, maxInputs: 0, minOutputs: 1, maxOutputs: null },
  createDefaultConfiguration: createSpatialServiceInputConfiguration,
  summarize: summarizeSpatialServiceInput,
  collectMetadataReferences: collectSpatialServiceInputMetadataReferences,
  loadInspector: () => import('./inspector'),
});
