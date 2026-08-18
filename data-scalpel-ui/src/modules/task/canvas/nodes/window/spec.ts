import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createWindowConfiguration } from '../nodeDefaults';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeWindow } from '../nodeSummaries';
import { createCanvasNodeSpec } from '../specFactory';
import { windowCanvasView } from './canvasView';

export const windowSpec = createCanvasNodeSpec({
  type: CanvasNodeType.Window,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorAggregate,
  label: '窗口计算',
  description: '按分区和排序追加排名、前后行值与窗口指标',
  searchKeywords: ['window', '窗口', '排名', 'lag', 'lead', 'rank'],
  iconKey: CanvasNodeIconKey.Window,
  order: 20,
  canvasView: windowCanvasView,
  supportedModes: ['BATCH'],
  introducedInMinor: 0,
  graph: { minInputs: 1, maxInputs: 1, minOutputs: 1, maxOutputs: null },
  createDefaultConfiguration: createWindowConfiguration,
  summarize: summarizeWindow,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
