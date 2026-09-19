import type {
  CanvasColumnSchema,
  CanvasFilterCondition,
  CanvasLiteral,
} from '../../canvasTypes';

export { validateFilterSqlExpressionDraft } from '../../nodes/filter/filterSqlExpression';

const createDefaultLiteral = (
  dataType: CanvasLiteral['dataType'] = 'STRING',
): CanvasLiteral => ({
  dataType: dataType === 'GEOMETRY' ? 'STRING' : dataType,
  value: '',
});

export const createDefaultFilterCondition = (
  column?: CanvasColumnSchema,
): CanvasFilterCondition => ({
  kind: 'GROUP',
  operator: 'AND',
  children: [{
    kind: 'PREDICATE',
    columnName: column?.name ?? '',
    operator: 'EQUALS',
    values: [createDefaultLiteral(column?.fieldType)],
  }],
});

export const validateFilterConditionDraft = (
  condition: CanvasFilterCondition,
): string | null => {
  if (condition.kind === 'GROUP') {
    if (condition.children.length === 0) return '至少添加一个筛选条件';
    for (const child of condition.children) {
      const issue = validateFilterConditionDraft(child);
      if (issue) return issue;
    }
    return null;
  }
  if (!condition.columnName) return '筛选条件中存在未选择字段的项目';
  if (
    condition.operator !== 'IS_NULL'
    && condition.operator !== 'IS_NOT_NULL'
    && (
      condition.values.length === 0
      || condition.values.some((value) => value.value === null || value.value === '')
    )
  ) {
    return '筛选条件中存在未填写的值';
  }
  return null;
};
