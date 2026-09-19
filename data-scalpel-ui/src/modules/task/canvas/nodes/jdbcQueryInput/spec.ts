import { parseNodeConfiguration } from './parser';
import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createJdbcQueryInputConfiguration } from './defaults';
import { collectJdbcQueryInputMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeJdbcQueryInput } from './summary';
import { createCanvasNodeSpec } from '../specFactory';
import { jdbcQueryInputCanvasView } from './canvasView';

export const jdbcQueryInputSpec = createCanvasNodeSpec({
  parseConfiguration: parseNodeConfiguration,
  type: CanvasNodeType.JdbcQueryInput,
  category: CanvasNodeCategory.Input,
  group: CanvasNodeGroup.InputDatabase,
  label: 'JDBC 查询输入',
  description: '通过只读 SQL 读取自定义查询结果',
  searchKeywords: ['jdbc', 'sql', '数据库', '查询', '维表'],
  iconKey: CanvasNodeIconKey.DatabaseQuery,
  order: 20,
  canvasView: jdbcQueryInputCanvasView,
  supportedModes: ['BATCH', 'STREAMING'],
  introducedInMinor: 0,
  graph: { minInputs: 0, maxInputs: 0, minOutputs: 1, maxOutputs: null },
  createDefaultConfiguration: createJdbcQueryInputConfiguration,
  summarize: summarizeJdbcQueryInput,
  collectMetadataReferences: collectJdbcQueryInputMetadataReferences,
  loadInspector: () => import('./inspector'),
});
