import {
  AuditOutlined,
  FileExcelOutlined,
  InboxOutlined,
  TableOutlined,
} from '@ant-design/icons';
import type { TableColumnsType, UploadProps } from 'antd';
import { Button, Drawer, Space, Steps, Table, Tag, Typography, Upload, message } from 'antd';
import { useCallback, useMemo, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { ContextHelp, InlineFeedback } from '../../../shared/components/ContextualFeedback';
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

const formatFileSize = (sizeBytes: number): string => {
  if (sizeBytes < 1024) return `${sizeBytes} B`;
  if (sizeBytes < 1024 * 1024) return `${(sizeBytes / 1024).toFixed(sizeBytes >= 10 * 1024 ? 1 : 2)} KB`;
  return `${(sizeBytes / 1024 / 1024).toFixed(sizeBytes >= 10 * 1024 * 1024 ? 1 : 2)} MB`;
};

export const DataEntryImportDrawer = ({ open, detail, onClose, onImported }: Props) => {
  const [file, setFile] = useState<File | null>(null);
  const [operationError, setOperationError] = useState<string | null>(null);
  const [messageApi, contextHolder] = message.useMessage();
  const previewMutation = usePreviewDataEntryImport();
  const importMutation = useImportDataEntryFile();
  const preview = previewMutation.data;

  const resetAndClose = useCallback(() => {
    setFile(null);
    setOperationError(null);
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
      setOperationError(null);
      previewMutation.reset();
      importMutation.reset();
      void previewMutation.mutateAsync({ id: detail.form.id, file: selected }).catch((error: unknown) => {
        const errorMessage = error instanceof ApiError ? error.message : '解析导入文件失败';
        setOperationError(errorMessage);
        messageApi.error(errorMessage);
      });
      return Upload.LIST_IGNORE;
    },
    fileList: file ? [{ uid: file.name, name: file.name, size: file.size, type: file.type, status: 'done' }] : [],
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
    setOperationError(null);
    try {
      const result = await importMutation.mutateAsync({
        id: detail.form.id,
        file,
        previewDigest: preview.previewDigest,
      });
      onImported(result);
      resetAndClose();
    } catch (error) {
      const errorMessage = error instanceof ApiError ? error.message : '批量导入失败';
      setOperationError(errorMessage);
      messageApi.error(errorMessage);
    }
  };

  const currentStage = preview ? 2 : file ? 1 : 0;
  const footerStatus = operationError ? (
    <InlineFeedback tone="error" label="导入处理失败" detail={operationError} ariaLabel="查看批量导入失败详情" />
  ) : previewMutation.isPending ? (
    <InlineFeedback tone="info" label="正在完整解析并校验文件…" />
  ) : preview?.importable ? (
    <InlineFeedback tone="success" label={`校验通过 · ${preview.validRowCount} 条可导入`} />
  ) : preview ? (
    <InlineFeedback tone="error" label={`${preview.issueCount} 个问题 · 暂不可导入`} detail="请根据校验问题修改文件后重新上传。" />
  ) : (
    <InlineFeedback tone="info" label="选择文件后自动执行全量校验" />
  );

  return (
    <>
      {contextHolder}
      <Drawer
        rootClassName="business-overlay business-drawer-overlay"
        className="data-entry-import-drawer"
        title={(
          <div className="data-entry-import-drawer-title">
            <span className="data-entry-import-drawer-title-icon" aria-hidden="true"><FileExcelOutlined /></span>
            <span className="data-entry-import-drawer-title-copy">
              <span>批量导入填报数据</span>
              <Typography.Text type="secondary">
                {detail.form.modelName ?? detail.form.modelCode ?? '数据填报'} · 先校验预览，再确认写入
              </Typography.Text>
            </span>
          </div>
        )}
        extra={<Tag className="data-entry-import-header-tag">XLSX / CSV</Tag>}
        open={open}
        width="min(1200px, 92vw)"
        destroyOnHidden
        maskClosable={!importMutation.isPending}
        closable={!importMutation.isPending}
        onClose={resetAndClose}
        footer={(
          <div className="data-entry-import-footer">
            {footerStatus}
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
          </div>
        )}
      >
        <div className="data-entry-import-workspace">
          <div className="data-entry-import-progress" aria-label="批量导入进度">
            <Steps
              current={currentStage}
              responsive={false}
              size="small"
              items={[
                { title: '选择文件' },
                { title: '完整校验' },
                { title: '确认数据' },
              ]}
            />
          </div>

          <section className="data-entry-import-section data-entry-import-upload-section">
            <header className="data-entry-import-section-header">
              <span className="data-entry-import-section-icon" aria-hidden="true"><InboxOutlined /></span>
              <span className="data-entry-import-section-copy">
                <span className="data-entry-import-section-title-row">
                  <strong>导入文件</strong>
                  <ContextHelp
                    ariaLabel="查看批量导入文件规范"
                    content="最大 50 MB、100000 条数据；第一行是字段名称，第二行是字段编码，第三行开始填报。文件会完整校验，但页面仅展示前 100 条数据和最多 200 条问题。"
                    presentation="popover"
                    placement="bottomLeft"
                  />
                </span>
                <Typography.Text type="secondary">上传 .xlsx 或 UTF-8 .csv，选择后立即执行全量校验</Typography.Text>
              </span>
              {file && <span className="data-entry-import-selected-file-size">{formatFileSize(file.size)}</span>}
            </header>
            <div className="data-entry-import-section-body">
              <Upload.Dragger className="data-entry-import-uploader" {...uploadProps}>
                <p className="ant-upload-drag-icon"><InboxOutlined /></p>
                <p className="data-entry-import-upload-title">点击或拖入 Excel / CSV 文件</p>
                <p className="ant-upload-hint">支持 .xlsx、UTF-8 .csv · 选择新文件会替换当前校验结果</p>
              </Upload.Dragger>
              {previewMutation.isPending && (
                <InlineFeedback className="data-entry-import-progress-feedback" tone="info" label="正在完整解析并校验文件，请稍候…" />
              )}
              {operationError && !preview && (
                <InlineFeedback className="data-entry-import-progress-feedback" tone="error" label="文件解析失败" detail={operationError} />
              )}
            </div>
          </section>

          {preview && (
            <>
              <section className="data-entry-import-section data-entry-import-validation-section">
                <header className="data-entry-import-section-header">
                  <span className="data-entry-import-section-icon" aria-hidden="true"><AuditOutlined /></span>
                  <span className="data-entry-import-section-copy">
                    <strong>校验结果</strong>
                    <Typography.Text type="secondary">完整文件的字段匹配、数据类型和业务约束检查</Typography.Text>
                  </span>
                  <InlineFeedback
                    tone={preview.importable ? 'success' : 'error'}
                    label={preview.importable ? '全文件校验通过' : '文件存在阻断问题'}
                    detail={preview.importable
                      ? `共 ${preview.totalRowCount} 条有效数据，可以确认导入。`
                      : `共发现 ${preview.issueCount} 个问题，涉及 ${preview.errorRowCount} 行；请修改文件后重新上传。`}
                  />
                </header>
                <div className="data-entry-import-section-body">
                  <div className="data-entry-import-summary-grid">
                    <div><span>文件</span><strong title={preview.fileName}>{preview.fileName}</strong></div>
                    <div><span>格式</span><strong>{preview.format}</strong></div>
                    <div><span>数据行</span><strong>{preview.totalRowCount}</strong></div>
                    <div><span>有效 / 错误</span><strong>{preview.validRowCount} / <em>{preview.errorRowCount}</em></strong></div>
                  </div>

                  {preview.issues.length > 0 && (
                    <div className="data-entry-import-table-block data-entry-import-issues-block">
                      <div className="data-entry-import-table-heading">
                        <span>
                          <strong>校验问题</strong>
                          <Typography.Text type="secondary">定位无法导入的文件行和字段</Typography.Text>
                        </span>
                        {preview.issuesTruncated && <Tag color="warning">仅展示前 200 条</Tag>}
                      </div>
                      <Table<DataEntryImportIssue>
                        size="small"
                        className="management-table data-entry-import-issues-table"
                        rowKey={(row, index) => `${row.rowNumber}-${row.fieldCode ?? ''}-${row.code}-${index}`}
                        dataSource={preview.issues}
                        pagination={false}
                        scroll={{ y: 240 }}
                        columns={[
                          { title: '文件行', dataIndex: 'rowNumber', width: 86 },
                          { title: '字段编码', dataIndex: 'fieldCode', width: 150, render: (value) => value ? <code>{value}</code> : '—' },
                          { title: '问题码', dataIndex: 'code', width: 220, render: (value) => <code>{value}</code> },
                          { title: '说明', dataIndex: 'message', ellipsis: { showTitle: true } },
                        ]}
                      />
                    </div>
                  )}
                </div>
              </section>

              <section className="data-entry-import-section data-entry-import-preview-section">
                <header className="data-entry-import-section-header">
                  <span className="data-entry-import-section-icon" aria-hidden="true"><TableOutlined /></span>
                  <span className="data-entry-import-section-copy">
                    <strong>数据预览</strong>
                    <Typography.Text type="secondary">确认字段映射和展示值，实际写入仍以完整文件为准</Typography.Text>
                  </span>
                  <Tag className="data-entry-import-preview-count">前 {preview.previewRows.length} 条</Tag>
                </header>
                <div className="data-entry-import-preview-table-wrap">
                  <Table<DataEntryImportPreviewRow>
                    size="small"
                    className="management-table data-entry-import-preview-table"
                    rowKey="rowNumber"
                    dataSource={preview.previewRows}
                    columns={previewColumns}
                    pagination={false}
                    scroll={{ x: 'max-content', y: 360 }}
                  />
                </div>
              </section>
            </>
          )}
        </div>
      </Drawer>
    </>
  );
};
