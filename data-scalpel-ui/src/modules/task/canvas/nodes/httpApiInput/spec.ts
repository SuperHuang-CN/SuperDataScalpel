import { parseNodeConfiguration } from './parser';
import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createHttpApiInputConfiguration } from './defaults';
import { collectHttpApiInputMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeHttpApiInput } from './summary';
import { createCanvasNodeSpec } from '../specFactory';
import { httpApiInputCanvasView } from './canvasView';

export const httpApiInputSpec = createCanvasNodeSpec({
  parseConfiguration: parseNodeConfiguration,
  type: CanvasNodeType.HttpApiInput,
  category: CanvasNodeCategory.Input,
  group: CanvasNodeGroup.InputApi,
  label: 'HTTP API 输入',
  description: '调用 HTTP API 生成数据表',
  searchKeywords: ['http', 'api', '接口', '请求'],
  iconKey: CanvasNodeIconKey.Api,
  order: 10,
  canvasView: httpApiInputCanvasView,
  supportedModes: ['BATCH'],
  introducedInMinor: 0,
  graph: { minInputs: 0, maxInputs: 0, minOutputs: 1, maxOutputs: null },
  createDefaultConfiguration: createHttpApiInputConfiguration,
  summarize: summarizeHttpApiInput,
  collectMetadataReferences: collectHttpApiInputMetadataReferences,
  loadInspector: () => import('./inspector'),
});
