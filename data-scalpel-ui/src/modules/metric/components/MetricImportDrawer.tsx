import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Button, Drawer, Empty, Space, Table, Tag, Typography, Upload } from 'antd';
import { DownloadOutlined, FileExcelOutlined, UploadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { ContextHelp, InlineFeedback } from '../../../shared/components/ContextualFeedback';
import { DetailTableToolbar } from '../../../shared/components/DetailTableToolbar';
import { downloadBlob } from '../../../shared/browser/downloadBlob';
import { downloadMetricImportErrors, downloadMetricTemplate, importMetrics, previewMetricImport } from '../api/metricExcelApi';
import { invalidateMetrics } from '../hooks/useMetrics';
import type { MetricImportRow } from '../model/metricExcel';

const actionLabels = { CREATE: '新增', UPDATE: '更新', UNCHANGED: '无变化', ERROR: '有错误' };
const actionColors = { CREATE: 'success', UPDATE: 'processing', UNCHANGED: 'default', ERROR: 'error' };

export const MetricImportDrawer = ({ onClose, onImported }: { onClose: () => void; onImported: () => void }) => {
  const client = useQueryClient();
  const [file, setFile] = useState<File | null>(null);
  const [fileError, setFileError] = useState<string | null>(null);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const preview = useMutation({ mutationFn: previewMetricImport });
  const template = useMutation({
    mutationFn: downloadMetricTemplate,
    onSuccess: blob => downloadBlob(blob, 'DataScalpel-指标导入模板.xlsx'),
  });
  const errors = useMutation({
    mutationFn: downloadMetricImportErrors,
    onSuccess: blob => downloadBlob(blob, 'DataScalpel-指标导入错误清单.xlsx'),
  });
  const commit = useMutation({
    mutationFn: ({ source, fingerprint }: { source: File; fingerprint: string }) => importMetrics(source, fingerprint),
    onSuccess: async () => { await invalidateMetrics(client); onImported(); },
  });
  const busy = preview.isPending || commit.isPending || errors.isPending;
  const review = () => {
    if (!file) return;
    commit.reset(); errors.reset(); setPage(1); preview.mutate(file);
  };
  const columns: ColumnsType<MetricImportRow> = [
    { title: 'Excel 行', dataIndex: 'rowNumber', width: 80 },
    { title: '指标', key: 'metric', width: 200, render: (_, row) => <><div>{row.name || '未填名称'}</div><Typography.Text type="secondary">{row.code || '未填编码'}</Typography.Text></> },
    { title: '操作', dataIndex: 'action', width: 85, render: (action: MetricImportRow['action']) => <Tag color={actionColors[action]}>{actionLabels[action]}</Tag> },
    { title: '字段变化 / 校验', key: 'issues', render: (_, row) => <>
      <Typography.Text type="secondary">{row.changes.length ? `${row.changes.length} 项内容变化，可展开查看` : '内容无变化'}</Typography.Text>
      {row.issues.filter(issue => issue.blocking).map((issue, i) => <div key={i}><Typography.Text type="danger">{issue.column}：{issue.message}</Typography.Text></div>)}
      {row.issues.some(issue => !issue.blocking) && <div><Typography.Text type="secondary">口径尚有发布前待补充项，可先导入草稿。</Typography.Text></div>}
    </> },
  ];
  return <Drawer
    open width={1000} rootClassName="business-overlay business-drawer-overlay"
    title={<Space><FileExcelOutlined /><span>导入指标<Typography.Text type="secondary" className="metric-import-subtitle">上传 Excel，预览后批量保存草稿</Typography.Text></span></Space>}
    onClose={busy ? undefined : onClose} closable={!busy} maskClosable={!busy}
    footer={<div className="metric-editor-footer">
      <Typography.Text type="secondary">{commit.data ? '导入已完成' : '有错误时整批不导入；不会自动发布或运行任务'}</Typography.Text>
      <Space><Button disabled={busy} onClick={onClose}>{commit.data ? '完成' : '取消'}</Button>
        {!commit.data && <Button type="primary" loading={commit.isPending} disabled={busy || !file || !preview.data?.canImport || preview.isError || commit.isError}
          onClick={() => file && preview.data && commit.mutate({ source: file, fingerprint: preview.data.fingerprint })}>确认导入</Button>}
      </Space>
    </div>}
  >
    {commit.data ? <InlineFeedback tone="success" label={`导入完成：新增 ${commit.data.created} 项，更新 ${commit.data.updated} 项，无变化 ${commit.data.unchanged} 项。`} /> : <>
      <div className="metric-import-upload">
        <Upload
          accept=".xlsx" maxCount={1} disabled={busy} fileList={file ? [{ uid: 'metric-workbook', name: file.name, status: 'done' }] : []}
          beforeUpload={candidate => {
            setFileError(null);
            if (!candidate.name.toLowerCase().endsWith('.xlsx') || candidate.size > 10 * 1024 * 1024) {
              setFile(null); preview.reset(); commit.reset(); errors.reset();
              setFileError('请选择不超过 10 MB 的 .xlsx 文件'); return Upload.LIST_IGNORE;
            }
            setFile(candidate); preview.reset(); commit.reset(); errors.reset(); setPage(1); return false;
          }}
          onRemove={() => { setFile(null); preview.reset(); commit.reset(); errors.reset(); return true; }}
        ><Button icon={<UploadOutlined />}>选择 Excel</Button></Upload>
        <Button icon={<DownloadOutlined />} loading={template.isPending} onClick={() => template.mutate()}>下载模板</Button>
        <Button disabled={!file || busy} loading={preview.isPending} onClick={review}>预览校验</Button>
        <ContextHelp ariaLabel="指标导入填写规则" presentation="popover" content={<>
          <p>一行一个指标，最多 1000 行。新增填写编码、名称、类型，口径可暂不完整。</p>
          <p>更新已有指标请先导出草稿，保留隐藏标识。空白会清空对应可选内容，目录必须预先存在。</p>
          <p>基础资料直接更新，口径只保存草稿；已有结果绑定和参考资料保留。发布口径查阅版不可回导。</p>
        </>} />
      </div>
      {fileError && <InlineFeedback tone="error" label={fileError} />}
      {template.isError && <InlineFeedback tone="error" label={template.error.message} />}
      {preview.isError && <InlineFeedback tone="error" label={preview.error.message} action={<Button onClick={review}>重试预览</Button>} />}
      {errors.isError && <InlineFeedback tone="error" label={errors.error.message} />}
      {commit.isError && <InlineFeedback tone="error" label={commit.error.message} action={<Button onClick={review}>重新预览</Button>} />}
      {preview.data && !preview.isError ? <>
        <Space wrap className="metric-import-summary">
          <Tag color="success">新增 {preview.data.createCount}</Tag><Tag color="processing">更新 {preview.data.updateCount}</Tag>
          <Tag>无变化 {preview.data.unchangedCount}</Tag><Tag color={preview.data.errorCount ? 'error' : 'default'}>错误 {preview.data.errorCount} 行</Tag>
          {preview.data.warningCount > 0 && <Typography.Text type="secondary">发布前待补充 {preview.data.warningCount} 项</Typography.Text>}
        </Space>
        <DetailTableToolbar title="导入预览" total={preview.data.totalRows} current={page} pageSize={size}
          onChange={(p, s) => { setPage(p); setSize(s); }}
          extra={<Button size="small" disabled={busy || !file} loading={errors.isPending} onClick={() => file && errors.mutate(file)}>下载错误清单</Button>} />
        <Table<MetricImportRow>
          className="metric-import-table" tableLayout="fixed"
          rowKey="rowNumber" size="small" pagination={false} loading={preview.isPending} columns={columns}
          dataSource={preview.data.rows.slice((page - 1) * size, page * size)}
          expandable={{ expandedRowRender: row => <div className="metric-import-details">
            {row.changes.length > 0 && <Table size="small" tableLayout="fixed" rowKey="column" pagination={false} dataSource={row.changes} columns={[
              { title: '字段', dataIndex: 'column', width: 140 },
              { title: '系统现有内容', dataIndex: 'before', render: (value: string) => <Typography.Paragraph className="metric-long-text" ellipsis={{ rows: 3, expandable: true }}>{value || '—'}</Typography.Paragraph> },
              { title: '导入后内容', dataIndex: 'after', render: (value: string) => <Typography.Paragraph className="metric-long-text" ellipsis={{ rows: 3, expandable: true }}>{value || '—'}</Typography.Paragraph> },
            ]} />}
            {row.issues.filter(issue => !issue.blocking).map((issue, i) => <div key={i}>{issue.column}：{issue.message}</div>)}
          </div> }}
        />
      </> : !preview.isPending && !preview.isError && <Empty description="选择文件并预览，确认内容后再导入" />}
    </>}
  </Drawer>;
};
