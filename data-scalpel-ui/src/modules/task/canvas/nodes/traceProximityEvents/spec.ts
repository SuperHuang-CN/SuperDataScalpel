import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { createTraceProximityEventsConfiguration } from '../nodeDefaults';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeTraceProximityEvents } from '../nodeSummaries';
import { createCanvasNodeSpec } from '../specFactory';
import { traceProximityEventsCanvasView } from './canvasView';

export const traceProximityEventsSpec = createCanvasNodeSpec({
  type: CanvasNodeType.TraceProximityEvents,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorSpatial,
  label: '追踪邻近事件',
  description: '从指定实体出发，按时空邻近事件向下游逐层传播',
  searchKeywords: ['空间', '轨迹', '邻近', '接触', '传播', '追踪', 'trace proximity events'],
  iconKey: CanvasNodeIconKey.TraceProximityEvents,
  order: 90,
  canvasView: traceProximityEventsCanvasView,
  supportedModes: ['BATCH'],
  introducedInMinor: 73,
  graph: { minInputs: 1, maxInputs: null, minOutputs: 0, maxOutputs: null },
  createDefaultConfiguration: createTraceProximityEventsConfiguration,
  summarize: summarizeTraceProximityEvents,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
