import { parseNodeConfiguration } from './parser';
import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createJoinConfiguration } from './defaults';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeJoin } from './summary';
import { createCanvasNodeSpec } from '../specFactory';
import { joinCanvasView } from './canvasView';

export const joinSpec = createCanvasNodeSpec({
  parseConfiguration: parseNodeConfiguration,
  type: CanvasNodeType.Join,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorRelational,
  label: 'Join 处理器',
  description: '按字段条件合并两张数据表',
  searchKeywords: ['join', '关联', '连接', '合并'],
  iconKey: CanvasNodeIconKey.Join,
  order: 10,
  canvasView: joinCanvasView,
  supportedModes: ['BATCH'],
  introducedInMinor: 0,
  graph: { minInputs: 1, maxInputs: null, minOutputs: 0, maxOutputs: null },
  createDefaultConfiguration: createJoinConfiguration,
  summarize: summarizeJoin,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
