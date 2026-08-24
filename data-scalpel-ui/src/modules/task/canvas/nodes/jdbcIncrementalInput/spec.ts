import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createJdbcIncrementalInputConfiguration } from '../nodeDefaults';
import { collectJdbcIncrementalInputMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeJdbcIncrementalInput } from '../nodeSummaries';
import { createCanvasNodeSpec } from '../specFactory';
import { jdbcIncrementalInputCanvasView } from './canvasView';

export const jdbcIncrementalInputSpec = createCanvasNodeSpec<
  typeof CanvasNodeType.JdbcIncrementalInput
>({
  type: CanvasNodeType.JdbcIncrementalInput,
  category: CanvasNodeCategory.Input,
  group: CanvasNodeGroup.InputStream,
  label: 'JDBC 增量输入',
  description: '按时间字段固定间隔轮询 JDBC 物理表',
  searchKeywords: ['jdbc', '增量', '轮询', '时间字段', 'streaming'],
  iconKey: CanvasNodeIconKey.Database,
  order: 5,
  canvasView: jdbcIncrementalInputCanvasView,
  supportedModes: ['STREAMING'],
  introducedInMinor: 0,
  graph: { minInputs: 0, maxInputs: 0, minOutputs: 1, maxOutputs: 1 },
  createDefaultConfiguration: createJdbcIncrementalInputConfiguration,
  summarize: summarizeJdbcIncrementalInput,
  collectMetadataReferences: collectJdbcIncrementalInputMetadataReferences,
  loadInspector: () => import('./inspector'),
});
