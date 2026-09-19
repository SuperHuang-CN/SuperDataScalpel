import { andSearch, orSearch, searchContains, searchEquals, searchComparison } from '../../../shared/search';
import type { AlertRuleType, AlertSeverity, AlertStatus, RuntimeRunFilters } from './operations';
export interface AlertFilters { keyword?: string; status?: AlertStatus | 'ACTIVE' | 'ALL'; ruleType?: AlertRuleType; severity?: AlertSeverity; subjectId?: string; from?: string; to?: string }
export const buildRuntimeRunSearch = (f: RuntimeRunFilters) => andSearch(
  f.status === 'FAILED_OR_TIMED_OUT' ? orSearch(searchEquals('status', 'FAILED'), searchEquals('status', 'TIMED_OUT')) : searchEquals('status', f.status),
  searchEquals('taskType', f.taskType), searchEquals('computeEngineId', f.computeEngineId), searchEquals('triggerType', f.triggerType),
  searchEquals('qualityConclusion', f.qualityConclusion),
);
export const buildAlertSearch = (f: AlertFilters) => andSearch(
  orSearch(searchContains('subjectName', f.keyword), searchContains('summary', f.keyword)),
  f.status === 'ACTIVE' || !f.status ? 'status!"CLOSED"' : f.status === 'ALL' ? undefined : searchEquals('status', f.status),
  searchEquals('ruleType', f.ruleType), searchEquals('severity', f.severity), searchEquals('subjectId', f.subjectId),
  searchComparison('occurredAt', '>=', f.from), searchComparison('occurredAt', '<', f.to),
);
