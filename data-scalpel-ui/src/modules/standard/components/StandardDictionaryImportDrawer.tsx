import { AuditOutlined, DownloadOutlined, ImportOutlined, InboxOutlined } from '@ant-design/icons';
import type { UploadProps } from 'antd';
import { Button, Drawer, Space, Steps, Table, Tag, Typography, Upload, message } from 'antd';
import { useCallback, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { downloadBlob } from '../../../shared/browser/downloadBlob';
import { ContextHelp, InlineFeedback } from '../../../shared/components/ContextualFeedback';
import {
  useDownloadStandardDictionaryTemplate,
  useImportStandardDictionaryMetadata,
  usePreviewStandardDictionaryImport,
} from '../hooks/useStandardDictionaries';
import { useStandardFormLeaveGuard } from '../hooks/useStandardFormLeaveGuard';
import {
  standardDictionaryImportActionLabels,
  type StandardDictionaryImportAction,
  type StandardDictionaryImportDictionaryPreview,
} from '../model/standardDictionary';

interface Props {
  open: boolean;
  onClose: () => void;
}

const actionColor = {
  CREATE: 'success',
  UPDATE: 'processing',
  UNCHANGED: 'default',
  ERROR: 'error',
} as const;

export const StandardDictionaryImportDrawer = ({ open, onClose }: Props) => {
  const [file, setFile] = useState<File | null>(null);
  const [operationError, setOperationError] = useState<string | null>(null);
  const [messageApi, contextHolder] = message.useMessage();
  const previewMutation = usePreviewStandardDictionaryImport();
  const importMutation = useImportStandardDictionaryMetadata();
  const templateMutation = useDownloadStandardDictionaryTemplate();
  const preview = previewMutation.data;

  const resetAndClose = useCallback(() => {
    setFile(null);
    setOperationError(null);
    previewMutation.reset();
    importMutation.reset();
    onClose();
  }, [importMutation, onClose, previewMutation]);
  const requestClose = useStandardFormLeaveGuard({
    dirty: open && Boolean(file),
    content: '已上传并校对的码表 Excel 尚未导入。',
    onDiscard: resetAndClose,
  });

  const uploadProps: UploadProps = {
    accept: '.xlsx',
    maxCount: 1,
    beforeUpload: (selected) => {
      setFile(selected);
      setOperationError(null);
      previewMutation.reset();
      void previewMutation.mutateAsync(selected).catch((error: unknown) => {
        const errorMessage = error instanceof ApiError ? error.message : '解析码表 Excel 失败';
        setOperationError(errorMessage);
        messageApi.error(errorMessage);
      });
      return Upload.LIST_IGNORE;
    },
    fileList: file ? [{ uid: file.name, name: file.name, size: file.size, type: file.type, status: 'done' }] : [],
    showUploadList: file ? { showRemoveIcon: false } : false,
  };

  const downloadTemplate = async () => {
    setOperationError(null);
    try {
      downloadBlob(await templateMutation.mutateAsync(), 'DataScalpel-码表导入模板.xlsx');
    } catch (error) {
      const errorMessage = error instanceof ApiError ? error.message : '下载模板失败';
      setOperationError(errorMessage);
      messageApi.error(errorMessage);
    }
  };

  const submit = async () => {
    if (!file || !preview?.importable) return;
    setOperationError(null);
    try {
      const result = await importMutation.mutateAsync({
        file,
        previewDigest: preview.previewDigest,
      });
      messageApi.success(
        `导入完成：新增 ${result.createdDictionaryCount} 张码表、更新 ${result.updatedDictionaryCount} 张码表`,
      );
      resetAndClose();
    } catch (error) {
      const errorMessage = error instanceof ApiError ? error.message : '导入码表失败';
      setOperationError(errorMessage);
      messageApi.error(errorMessage);
    }
  };

  const currentStage = preview ? 2 : file ? 1 : 0;
  const footerStatus = operationError ? (
    <InlineFeedback tone="error" label="码表导入处理失败" detail={operationError} ariaLabel="查看码表导入失败详情" />
  ) : previewMutation.isPending ? (
    <InlineFeedback tone="info" label="正在解析并校验码表…" />
  ) : preview?.importable ? (
    <InlineFeedback tone="success" label={`校验通过 · ${preview.dictionaries.length} 张码表`} />
  ) : preview ? (
    <InlineFeedback tone="error" label="存在未解决问题" detail={preview.issues.join('；') || '请根据表格问题修改 Excel 后重新上传。'} />
  ) : (
    <InlineFeedback tone="info" label="使用固定模板可避免字段结构不一致" />
  );

  return (
    <>
      {contextHolder}
      <Drawer
        rootClassName="business-overlay business-drawer-overlay"
        className="standard-dictionary-import-drawer"
        title={(
          <div className="standard-dictionary-import-title">
            <span className="standard-dictionary-import-title-icon" aria-hidden="true"><ImportOutlined /></span>
            <span className="standard-dictionary-import-title-copy">
              <span>导入树形码表</span>
              <Typography.Text type="secondary">校验码表身份、节点层级与现有数据变更</Typography.Text>
            </span>
          </div>
        )}
        extra={<Tag className="standard-dictionary-import-header-tag">Excel 模板</Tag>}
        open={open}
        width={760}
        closable={!importMutation.isPending}
        maskClosable={!importMutation.isPending}
        destroyOnHidden
        onClose={requestClose}
        footer={(
          <div className="standard-dictionary-import-footer">
            {footerStatus}
            <Space>
              <Button disabled={importMutation.isPending} onClick={requestClose}>取消</Button>
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
        <div className="standard-dictionary-import-workspace">
          <div className="standard-dictionary-import-progress" aria-label="树形码表导入进度">
            <Steps
              current={currentStage}
              responsive={false}
              size="small"
              items={[{ title: '准备模板' }, { title: '解析校验' }, { title: '确认变更' }]}
            />
          </div>

          <section className="standard-dictionary-import-section standard-dictionary-import-upload-section">
            <header className="standard-dictionary-import-section-header">
              <span className="standard-dictionary-import-section-icon" aria-hidden="true"><InboxOutlined /></span>
              <span className="standard-dictionary-import-section-copy">
                <span className="standard-dictionary-import-section-title-row">
                  <strong>导入文件</strong>
                  <ContextHelp
                    ariaLabel="查看树形码表导入规则"
                    content="父节点通过同一码表内的节点编码识别，行顺序不影响树结构；上传后系统会比较现有码表并标记新增、更新、无变化和错误。"
                    presentation="popover"
                    placement="bottomLeft"
                  />
                </span>
                <Typography.Text type="secondary">使用系统固定模板，避免字段和层级表达不一致</Typography.Text>
              </span>
              <Button
                icon={<DownloadOutlined />}
                loading={templateMutation.isPending}
                onClick={() => void downloadTemplate()}
              >
                下载模板
              </Button>
            </header>
            <div className="standard-dictionary-import-section-body">
              <Upload.Dragger className="standard-dictionary-import-uploader" {...uploadProps}>
                <p className="ant-upload-drag-icon"><InboxOutlined /></p>
                <p className="standard-dictionary-import-upload-title">点击或拖入系统模板格式的 .xlsx 文件</p>
                <p className="ant-upload-hint">选择新文件会替换当前校验结果，不会立即写入码表</p>
              </Upload.Dragger>
              {previewMutation.isPending && (
                <InlineFeedback className="standard-dictionary-import-progress-feedback" tone="info" label="正在解析并校验码表…" />
              )}
              {operationError && !preview && (
                <InlineFeedback className="standard-dictionary-import-progress-feedback" tone="error" label="文件处理失败" detail={operationError} />
              )}
            </div>
          </section>

          {preview && (
            <section className="standard-dictionary-import-section standard-dictionary-import-preview-section">
              <header className="standard-dictionary-import-section-header">
                <span className="standard-dictionary-import-section-icon" aria-hidden="true"><AuditOutlined /></span>
                <span className="standard-dictionary-import-section-copy">
                  <strong>变更预览</strong>
                  <Typography.Text type="secondary">逐张确认码表动作、节点数量和阻断问题</Typography.Text>
                </span>
                <InlineFeedback
                  tone={preview.importable ? 'success' : 'error'}
                  label={preview.importable ? '校验通过，可以导入' : '存在未解决问题'}
                  detail={preview.issues.join('；') || (preview.importable ? `共 ${preview.dictionaries.length} 张码表。` : '请根据问题列修改 Excel 后重新上传。')}
                />
              </header>
              <div className="standard-dictionary-import-table-wrap">
                <Table<StandardDictionaryImportDictionaryPreview>
                  size="small"
                  className="management-table standard-dictionary-import-table"
                  rowKey={(row) => `${row.rowNumber}-${row.code}`}
                  dataSource={preview.dictionaries}
                  pagination={false}
                  scroll={{ y: 360 }}
                  columns={[
                    { title: '码表编码', dataIndex: 'code', width: 150, render: (value: string) => <code>{value}</code> },
                    { title: '码表名称', dataIndex: 'name', width: 160, ellipsis: { showTitle: true } },
                    { title: '节点数', dataIndex: 'items', width: 72, align: 'right', render: (items) => items.length },
                    {
                      title: '动作',
                      dataIndex: 'action',
                      width: 92,
                      render: (action: StandardDictionaryImportAction) => (
                        <Tag color={actionColor[action]}>{standardDictionaryImportActionLabels[action]}</Tag>
                      ),
                    },
                    {
                      title: '问题',
                      key: 'issues',
                      ellipsis: { showTitle: true },
                      render: (_value, row) => [...row.issues, ...row.items.flatMap((item) => item.issues)].join('；') || '—',
                    },
                  ]}
                />
              </div>
            </section>
          )}
        </div>
      </Drawer>
    </>
  );
};
