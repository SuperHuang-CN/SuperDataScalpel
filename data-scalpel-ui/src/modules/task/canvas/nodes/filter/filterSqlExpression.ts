import { CANVAS_FILTER_MAX_SQL_EXPRESSION_LENGTH } from '../../canvasTypes';

const forbiddenKeywords = new Set([
  'WHERE', 'SELECT', 'FROM', 'JOIN', 'UNION', 'WITH',
  'INSERT', 'UPDATE', 'DELETE', 'MERGE', 'CREATE', 'ALTER',
  'DROP', 'TRUNCATE',
]);

export type FilterSqlExpressionViolation =
  | 'REQUIRED'
  | 'TOO_LONG'
  | 'STATEMENT_KEYWORD'
  | 'STATEMENT_SEPARATOR'
  | 'COMMENT';

export const findFilterSqlExpressionViolation = (
  expression: string,
): FilterSqlExpressionViolation | null => {
  if (!expression.trim()) return 'REQUIRED';
  if (expression.length > CANVAS_FILTER_MAX_SQL_EXPRESSION_LENGTH) return 'TOO_LONG';

  const unquoted: string[] = [];
  let quote: "'" | '"' | '`' | null = null;
  for (let index = 0; index < expression.length; index += 1) {
    const current = expression[index];
    if (quote) {
      unquoted.push(' ');
      if (current === '\\' && index + 1 < expression.length) {
        unquoted.push(' ');
        index += 1;
        continue;
      }
      if (current === quote) {
        if (expression[index + 1] === quote) {
          unquoted.push(' ');
          index += 1;
        } else {
          quote = null;
        }
      }
      continue;
    }
    if (current === "'" || current === '"' || current === '`') {
      quote = current;
      unquoted.push(' ');
      continue;
    }
    if (current === ';') return 'STATEMENT_SEPARATOR';
    const next = expression[index + 1];
    if ((current === '-' && next === '-')
      || (current === '/' && next === '*')
      || (current === '*' && next === '/')) {
      return 'COMMENT';
    }
    unquoted.push(current);
  }

  const tokens = unquoted.join('').toUpperCase().match(/[A-Z0-9_]+/g) ?? [];
  return tokens.some((token) => forbiddenKeywords.has(token)) ? 'STATEMENT_KEYWORD' : null;
};

export const validateFilterSqlExpressionDraft = (expression: string): string | null => {
  const violation = findFilterSqlExpressionViolation(expression);
  if (violation === null) return null;
  if (violation === 'REQUIRED') return '请输入 SQL 布尔表达式';
  if (violation === 'TOO_LONG') {
    return `SQL 表达式不能超过 ${CANVAS_FILTER_MAX_SQL_EXPRESSION_LENGTH} 个字符`;
  }
  return '只允许填写布尔谓词，不能包含 WHERE、完整 SQL、注释或分号';
};
