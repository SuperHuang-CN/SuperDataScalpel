import { parseNodeConfiguration } from './parser';
import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createJsonExtractConfiguration } from './defaults';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeJsonExtract } from './summary';
import { createCanvasNodeSpec } from '../specFactory';
import { jsonExtractCanvasView } from './canvasView';

export const jsonExtractSpec = createCanvasNodeSpec({
  parseConfiguration: parseNodeConfiguration,
  type: CanvasNodeType.JsonExtract,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorColumn,
  label: 'JSON 提取',
  description: '从 JSON 字符串按 Path 提取结构化字段',
  searchKeywords: ['json', 'path', 'extract', 'variant', 'JSON 提取', '字段解析'],
  iconKey: CanvasNodeIconKey.Json,
  order: 60,
  canvasView: jsonExtractCanvasView,
  supportedModes: ['BATCH', 'STREAMING'],
  introducedInMinor: 0,
  graph: { minInputs: 1, maxInputs: null, minOutputs: 0, maxOutputs: null },
  createDefaultConfiguration: createJsonExtractConfiguration,
  summarize: summarizeJsonExtract,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
