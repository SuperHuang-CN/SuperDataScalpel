import { useState } from 'react';
import { Button } from 'antd';
import { ContextHelp } from '../../../shared/components/ContextualFeedback';
import type { UseQueryResult } from '@tanstack/react-query';
import { ModelQualityStatisticsDrawer, type QualityStatistics, type QualityResultFilter } from '../../model';
import { DashboardPanel } from './DashboardPanel';
import { number, dateTime } from '../model/dashboardFormat';
export const QualityOverview = ({ query }: { query: UseQueryResult<QualityStatistics> }) => {
 const [result, setResult] = useState<QualityResultFilter | null>(null);
 const data = query.data;
 const entries = data ? [
  { filter: 'PASSED' as const, label: '通过', count: data.passed, tone: 'home-success' },
  { filter: 'FAILED' as const, label: '未通过', count: data.failed, tone: 'home-warning' },
  { filter: 'NONE' as const, label: '无有效结果', count: data.noResult, tone: 'home-neutral' },
 ] : [];
 return <DashboardPanel className="home-quality-panel" title="数据质量" query={query} action={<Button type="link" size="small" onClick={() => setResult('ALL')}>查看模型质量 ↗</Button>}>
  {data && <>
   <p className="home-caption">已发布模型 · 最近一次正式有效结果</p>
   <div className="home-quality-values">{entries.map(entry => <button type="button" key={entry.filter} onClick={() => setResult(entry.filter)}>
    <span>{entry.label}</span><strong className={entry.tone}>{number(entry.count)}</strong>
   </button>)}</div>
   <div className="home-quality-track" role="img" aria-label={`共${data.total}个已发布模型，其中通过${data.passed}、不通过${data.failed}、无结果${data.noResult}`}>
    {entries.map(entry => <span key={entry.filter} className={entry.tone} style={{ width: (data.total ? entry.count / data.total * 100 : 0) + '%' }} />)}
   </div>
   <div className="home-caption">已有结果 {number(data.passed + data.failed)} / {number(data.total)}<ContextHelp ariaLabel="质量统计口径与结果时间" presentation="popover" content={<>统计已发布模型最近一次正式有效结果；历史通过不代表当前数据始终合格。检查结果时间：{dateTime(data.oldestResultAt)} — {dateTime(data.newestResultAt)}</>} /></div>
   {data.latestExecutionFailed > 0 && <Button type="link" size="small" danger onClick={() => setResult('EXECUTION_FAILED')}>最近正式执行失败 {number(data.latestExecutionFailed)} 个模型</Button>}
  </>}
  {result !== null && <ModelQualityStatisticsDrawer key={result} result={result} onClose={() => setResult(null)} />}
 </DashboardPanel>;
};

