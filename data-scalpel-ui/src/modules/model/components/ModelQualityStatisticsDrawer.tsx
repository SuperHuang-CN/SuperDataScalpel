import { Button, Drawer, Table, Tag } from 'antd';
import { Link } from 'react-router-dom';
import { useState } from 'react';
import { InlineFeedback } from '../../../shared/components/ContextualFeedback';
import { useQualityModels } from '../hooks/useQualityStatistics';
import type { QualityResultFilter } from '../model/qualityStatistics';
export const ModelQualityStatisticsDrawer = ({ result, onClose }: { result: QualityResultFilter | null; onClose: () => void }) => {
 const [page, setPage] = useState(0);
 const query = useQualityModels(result ?? 'ALL', page, 20, result !== null);
 const labels = { ALL: '全部已发布模型', PASSED: '最近结果通过', FAILED: '最近结果不通过', NONE: '尚无正式有效结果', EXECUTION_FAILED: '最近正式执行失败' };
 return <Drawer open={result !== null} title={'模型质量 · ' + labels[result ?? 'ALL']} width={920} onClose={onClose} rootClassName="business-overlay business-drawer-overlay">
  <p>最近一次正式有效结果；规则快照与结束时间仅说明历史检查范围，不代表当前持续合格。</p>
  {query.error && <InlineFeedback tone="error" label="质量明细加载失败" detail={query.error.message} action={<Button onClick={() => void query.refetch()}>重试</Button>} />}
  <Table size="small" rowKey="modelId" loading={query.isPending} dataSource={query.data?.content ?? []}
   pagination={{ current: page + 1, pageSize: 20, total: query.data?.totalElements ?? 0, showSizeChanger: false, onChange: next => setPage(next - 1) }}
   columns={[
    { title: '模型', dataIndex: 'modelName', render: (name, row) => <Link to={'/model/' + row.modelId}>{name}</Link> },
    { title: '历史结果', dataIndex: 'conclusion', render: value => value === 'PASSED' ? <Tag color="success">通过</Tag> : value === 'FAILED' ? <Tag color="warning">不通过</Tag> : '无有效结果' },
    { title: '结果结束时间', dataIndex: 'endedAt', render: value => value ? new Date(value).toLocaleString('zh-CN') : '—' },
    { title: '规则快照时间', dataIndex: 'ruleSnapshotAt', render: value => value ? new Date(value).toLocaleString('zh-CN') : '—' },
    { title: '最新执行', dataIndex: 'latestExecutionFailed', render: value => value ? <Tag color="error">失败 / 超时</Tag> : '—' },
   ]} />
 </Drawer>;
};
