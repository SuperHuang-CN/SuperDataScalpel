import { object } from './types';
function decodedResult(value: unknown, depth = 0): Record<string, unknown>[] {
  if (depth > 6) return [];
  if (typeof value === 'string') { try { return decodedResult(JSON.parse(value), depth + 1); } catch { return []; } }
  if (Array.isArray(value)) return value.flatMap(v => decodedResult(v, depth + 1));
  const record = object(value);
  return [record, ...['content','text','structuredContent','_meta'].flatMap(k => decodedResult(record[k], depth + 1))];
}
export function toolStatus(result: unknown) {
  if (!result) return { text: '正在执行', color: 'processing' };
  const values = decodedResult(result);
  const execution = values.find(v => typeof v.executionStatus === 'string');
  if (execution?.executionStatus === 'UNKNOWN') return { text: '结果不确定，请核对业务状态', color: 'warning' };
  if (execution?.executionStatus === 'RESPONDED_INCOMPLETE') return { text: `HTTP ${execution.httpStatus ?? '—'} · 响应未完整读取`, color: 'warning' };
  if (execution?.executionStatus === 'NOT_DISPATCHED') return { text: '请求被拒绝，未执行', color: 'error' };
  const http = values.find(v => typeof v.httpStatus === 'number')?.httpStatus;
  if (typeof http === 'number' && http >= 400) return { text: `业务返回 HTTP ${http}`, color: 'error' };
  if (values.some(v => v.isError === true)) return { text: '工具执行失败', color: 'error' };
  return { text: typeof http === 'number' ? `业务返回 HTTP ${http}` : '工具已返回', color: 'default' };
}
