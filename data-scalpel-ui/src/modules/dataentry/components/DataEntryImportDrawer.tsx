import { InboxOutlined } from '@ant-design/icons';
import type { TableColumnsType, UploadProps } from 'antd';
import { Alert, Button, Descriptions, Drawer, Space, Table, Tag, Upload, message } from 'antd';
import { useCallback, useMemo, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { useImportDataEntryFile, usePreviewDataEntryImport } from '../hooks/useDataEntry';
import type {
  DataEntryFormDetail,
  DataEntryImportIssue,
  DataEntryImportPreviewRow,
  DataEntryMutationResponse,
} from '../model/dataEntry';

interface Props {
  open: boolean;
  detail: DataEntryFormDetail;
  onClose: () => void;
  onImported: (result: DataEntryMutationResponse) => void;
}

export const DataEntryImportDrawer = ({ open, detail, onClose, onImported }: Props) => {
  const [file, setFile] = useState<File | null>(null);
  const [messageApi, contextHolder] = message.useMessage();
  const previewMutation = usePreviewDataEntryImport();
  const importMutation = useImportDataEntryFile();
  const preview = previewMutation.data;

  const resetAndClose = useCallback(() => {
    setFile(null);
    previewMutation.reset();
    importMutation.reset();
    onClose();
  }, [importMutation, onClose, previewMutation]);

  const uploadProps: UploadProps = {
    accept: '.xlsx,.csv',
    maxCount: 1,
    disabled: previewMutation.isPending || importMutation.isPending,
    beforeUpload: (selected) => {
      setFile(selected);
      previewMutation.reset();
      importMutation.reset();
      void previewMutation.mutateAsync({ id: detail.form.id, file: selected }).catch((error: unknown) => {
        messageApi.error(error instanceof ApiError ? error.message : '解析导入文件失败');
      });
      return Upload.LIST_IGNORE;
    },
    showUploadList: file ? { showRemoveIcon: false } : false,
  };

  const previewColumns = useMemo<TableColumnsType<DataEntryImportPreviewRow>>(() => [
    { title: '文件行', dataIndex: 'rowNumber', key: 'rowNumber', width: 90, fixed: 'left' },
    ...(preview?.fields.map((field) => ({
      title: <span>{field.name}<br /><code>{field.code}</code></span>,
      key: field.code,
      width: 180,
      ellipsis: true,
      render: (_value: unknown, row: DataEntryImportPreviewRow) => {
        const display = row.displayValues[field.code];
        if (display !== undefined && display !== '') return display;
        const raw = row.values[field.code];
        return raw === null || raw === undefined ? '—' : String(raw);
      },
    })) ?? []),
  ], [preview]);

  const submit = async () => {
    if (!file || !preview?.importable) return;
    try {
      const result = await importMutation.mutateAsync({
        id: detail.form.id,
        file,
        previewDigest: preview.previewDigest,
      });
      onImported(result);
      resetAndClose();
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '批量导入失败');
    }
  };

  return (
    <>
      {contextHolder}
      <Drawer
        rootClassName="business-overlay business-drawer-overlay"
        title="Excel / CSV 批量导入"
        open={open}
        width="min(1200px, 92vw)"
        destroyOnHidden
        maskClosable={!importMutation.isPending}
        closable={!importMutation.isPending}
        onClose={resetAndClose}
        footer={(
          <Space>
            <Button disabled={importMutation.isPending} onClick={resetAndClose}>取消</Button>
            <Button
              type="primary"
              disabled={!file || !preview?.importable}
              loading={importMutation.isPending}
              onClick={() => void submit()}
            >
              确认导入
            </Button>
          </Space>
        )}
      >
        <Space direction="vertical" size={12} style={{ width: '100%' }}>
          <Alert
            type="info"
            showIcon
            title="先校验预览，再确认写入"
            description="文件会被完整校验；页面仅展示前 100 条数据和最多 200 条问题。写入按批次生效，中途失败时已成功批次不会回滚。"
          />
          <Upload.Dragger {...uploadProps}>
            <p className="ant-upload-drag-icon"><InboxOutlined /></p>
            <p>上传 .xlsx 或 UTF-8 .csv 文件</p>
            <p className="ant-upload-hint">最大 50 MB、100000 条数据；第一行名称、第二行字段编码、第三行开始填报。</p>
          </Upload.Dragger>
          {previewMutation.isPending && <Alert type="info" showIcon title="正在完整解析并校验文件，请稍候…" />}
          {preview && (
            <>
              <Alert
                type={preview.importable ? 'success' : 'error'}
                showIcon
                title={preview.importable ? '全文件校验通过，可以导入' : '文件存在问题，不能导入'}
                description={preview.importable
                  ? `共 ${preview.totalRowCount} 条有效数据。`
                  : `共发现 ${preview.issueCount} 个问题，涉及 ${preview.errorRowCount} 行；请修改文件后重新上传。`}
              />
              <Descriptions size="small" bordered column={4} items={[
                { key: 'file', label: '文件', children: preview.fileName },
                { key: 'format', label: '格式', children: preview.format },
                { key: 'rows', label: '数据行', children: preview.totalRowCount },
                { key: 'counts', label: '有效 / 错误', children: `${preview.validRowCount} / ${preview.errorRowCount}` },
              ]} />
              {preview.issues.length > 0 && (
                <Table<DataEntryImportIssue>
                  size="small"
                  rowKey={(row, index) => `${row.rowNumber}-${row.fieldCode ?? ''}-${row.code}-${index}`}
                  dataSource={preview.issues}
                  pagination={false}
                  scroll={{ y: 240 }}
                  title={() => <span>校验问题 {preview.issuesTruncated && <Tag color="warning">仅展示前 200 条</Tag>}</span>}
                  columns={[
                    { title: '文件行', dataIndex: 'rowNumber', width: 90 },
                    { title: '字段编码', dataIndex: 'fieldCode', width: 150, render: (value) => value ? <code>{value}</code> : '—' },
                    { title: '问题码', dataIndex: 'code', width: 240, render: (value) => <code>{value}</code> },
                    { title: '说明', dataIndex: 'message', ellipsis: true },
                  ]}
                />
              )}
              <Table<DataEntryImportPreviewRow>
                size="small"
                rowKey="rowNumber"
                dataSource={preview.previewRows}
                columns={previewColumns}
                pagination={false}
                scroll={{ x: Math.max(900, previewColumns.length * 180), y: 360 }}
                title={() => <span>数据预览 <Tag>前 {preview.previewRows.length} 条</Tag></span>}
              />
            </>
          )}
        </Space>
      </Drawer>
    </>
  );
};
