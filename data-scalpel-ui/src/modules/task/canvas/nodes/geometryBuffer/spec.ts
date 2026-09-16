import { parseNodeConfiguration } from './parser';
import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createGeometryBufferConfiguration } from './defaults';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeGeometryBuffer } from './summary';
import { createCanvasNodeSpec } from '../specFactory';
import { geometryBufferCanvasView } from './canvasView';

export const geometryBufferSpec = createCanvasNodeSpec({
  parseConfiguration: parseNodeConfiguration,
  type: CanvasNodeType.GeometryBuffer,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorSpatial,
  label: 'Geometry Buffer',
  description: '按平面或椭球距离生成缓冲区',
  searchKeywords: ['空间', 'geometry', 'buffer', '缓冲区', '距离', '影响范围'],
  iconKey: CanvasNodeIconKey.GeometryBuffer,
  order: 50,
  canvasView: geometryBufferCanvasView,
  supportedModes: ['BATCH', 'STREAMING'],
  introducedInMinor: 0,
  graph: { minInputs: 1, maxInputs: null, minOutputs: 0, maxOutputs: null },
  createDefaultConfiguration: createGeometryBufferConfiguration,
  summarize: summarizeGeometryBuffer,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
