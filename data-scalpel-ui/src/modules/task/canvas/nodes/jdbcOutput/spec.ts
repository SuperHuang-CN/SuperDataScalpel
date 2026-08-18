import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createJdbcOutputConfiguration } from '../nodeDefaults';
import { collectJdbcOutputMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeJdbcOutput } from '../nodeSummaries';
import { createCanvasNodeSpec } from '../specFactory';
import { jdbcOutputCanvasView } from './canvasView';

export const jdbcOutputSpec = createCanvasNodeSpec({
  type: CanvasNodeType.JdbcOutput,
  category: CanvasNodeCategory.Output,
  group: CanvasNodeGroup.OutputDatabase,
  label: 'JDBC 输出',
  description: '将处理结果写入 JDBC 目标表',
  searchKeywords: ['jdbc', '数据库', '目标表', '写入'],
  iconKey: CanvasNodeIconKey.JdbcOutput,
  order: 10,
  canvasView: jdbcOutputCanvasView,
  supportedModes: ['BATCH', 'STREAMING'],
  introducedInMinor: 0,
  graph: { minInputs: 1, maxInputs: 1, minOutputs: 0, maxOutputs: 0 },
  createDefaultConfiguration: createJdbcOutputConfiguration,
  summarize: summarizeJdbcOutput,
  collectMetadataReferences: collectJdbcOutputMetadataReferences,
  loadInspector: () => import('./inspector'),
});
