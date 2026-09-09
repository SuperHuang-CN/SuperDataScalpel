import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { Button, Modal, Radio, Space, Typography } from 'antd';
import { DownloadOutlined, UploadOutlined } from '@ant-design/icons';
import { exportMetrics } from '../api/metricExcelApi';
import type { MetricExportMode } from '../model/metricExcel';
import { downloadBlob } from '../../../shared/browser/downloadBlob';
import { InlineFeedback } from '../../../shared/components/ContextualFeedback';
import { useCurrentUser } from '../../system';
import { MetricImportDrawer } from './MetricImportDrawer';

export const MetricExcelActions = ({ search, selectedIds, onImported }: { search?: string; selectedIds: string[]; onImported: () => void }) => {
  const permissions = useCurrentUser().data?.permissions ?? [];
  const canManage = permissions.includes('metric.manage');
  const canExportDraft = canManage || permissions.includes('metric.publish');
  const [importing, setImporting] = useState(false);
  const [mode, setMode] = useState<MetricExportMode | null>(null);
  const exportFile = useMutation({
    mutationFn: (source: MetricExportMode) => exportMetrics(source, selectedIds, search),
    onSuccess: (blob, source) => {
      downloadBlob(blob, `DataScalpel-指标${source === 'DRAFT' ? '草稿' : '发布口径'}-${new Date().toISOString().slice(0, 10)}.xlsx`);
      setMode(null);
    },
  });
  return <>
    {canManage && <Button icon={<UploadOutlined />} onClick={() => setImporting(true)}>导入</Button>}
    <Button icon={<DownloadOutlined />} onClick={() => { exportFile.reset(); setMode(canExportDraft ? 'DRAFT' : 'PUBLISHED'); }}>导出{selectedIds.length ? `（${selectedIds.length}）` : ''}</Button>
    {importing && <MetricImportDrawer onClose={() => setImporting(false)} onImported={onImported} />}
    <Modal open={mode !== null} title="导出指标" rootClassName="business-overlay business-modal-overlay"
      okText="导出 Excel" onCancel={() => !exportFile.isPending && setMode(null)} confirmLoading={exportFile.isPending}
      onOk={() => mode && exportFile.mutate(mode)}>
      <Space orientation="vertical" className="metric-full-width">
        <Typography.Text>{selectedIds.length ? `导出已勾选的 ${selectedIds.length} 项指标` : '导出当前筛选范围内的指标，最多 1000 项'}</Typography.Text>
        <Radio.Group value={mode} onChange={event => setMode(event.target.value as MetricExportMode)} disabled={exportFile.isPending}>
          <Radio value="DRAFT" disabled={!canExportDraft}>草稿（线下编辑）</Radio><Radio value="PUBLISHED">当前发布口径（查阅）</Radio>
        </Radio.Group>
        <Typography.Text type="secondary">{mode === 'DRAFT' ? '包含未发布修改，回导时校验草稿与资料是否变化。已有结果绑定和参考资料继续在系统内维护。' : '导出发布版本的口径及当前基础资料，包含版本和状态。此文件仅供查阅，不可回导；未发布指标不在此范围。'}</Typography.Text>
        {exportFile.isError && <InlineFeedback tone="error" label={exportFile.error.message} />}
      </Space>
    </Modal>
  </>;
};
