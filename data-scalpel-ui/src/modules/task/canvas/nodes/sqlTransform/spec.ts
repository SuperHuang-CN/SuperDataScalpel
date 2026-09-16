import { parseNodeConfiguration } from './parser';
import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createSqlTransformConfiguration } from './defaults';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeSqlTransform } from './summary';
import { createCanvasNodeSpec } from '../specFactory';
import { sqlTransformCanvasView } from './canvasView';

export const sqlTransformSpec = createCanvasNodeSpec({
  parseConfiguration: parseNodeConfiguration,
  type: CanvasNodeType.SqlTransform,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorRelational,
  label: 'SQL 处理',
  description: '使用一条 Spark SQL SELECT 查询处理完整上游表集合',
  searchKeywords: ['sql', 'select', 'cte', '查询', '处理', 'spark sql'],
  iconKey: CanvasNodeIconKey.SqlTransform,
  order: 25,
  canvasView: sqlTransformCanvasView,
  supportedModes: ['BATCH'],
  introducedInMinor: 1,
  graph: { minInputs: 1, maxInputs: null, minOutputs: 0, maxOutputs: null },
  createDefaultConfiguration: createSqlTransformConfiguration,
  summarize: summarizeSqlTransform,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
