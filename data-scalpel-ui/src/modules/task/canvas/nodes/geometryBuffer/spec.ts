import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createGeometryBufferConfiguration } from '../nodeDefaults';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeGeometryBuffer } from '../nodeSummaries';
import { createCanvasNodeSpec } from '../specFactory';

export const geometryBufferSpec = createCanvasNodeSpec({
  type: CanvasNodeType.GeometryBuffer,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorSpatial,
  label: 'Geometry Buffer',
  description: '按平面或椭球距离生成缓冲区',
  searchKeywords: ['空间', 'geometry', 'buffer', '缓冲区', '距离', '影响范围'],
  iconKey: CanvasNodeIconKey.GeometryBuffer,
  order: 50,
  defaultSize: { width: 250, height: 120 },
  supportedModes: ['BATCH', 'STREAMING'],
  introducedInMinor: 22,
  graph: { minInputs: 1, maxInputs: 1, minOutputs: 1, maxOutputs: null },
  createDefaultConfiguration: createGeometryBufferConfiguration,
  summarize: summarizeGeometryBuffer,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
