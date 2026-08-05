import { DownloadOutlined, InboxOutlined } from '@ant-design/icons';
import type { UploadProps } from 'antd';
import { Alert, Button, Drawer, Space, Table, Tag, Upload, message } from 'antd';
import { useCallback, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { downloadBlob } from '../../../shared/browser/downloadBlob';
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
  const [messageApi, contextHolder] = message.useMessage();
  const previewMutation = usePreviewStandardDictionaryImport();
  const importMutation = useImportStandardDictionaryMetadata();
  const templateMutation = useDownloadStandardDictionaryTemplate();
  const preview = previewMutation.data;

  const resetAndClose = useCallback(() => {
    setFile(null);
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
      previewMutation.reset();
      void previewMutation.mutateAsync(selected).catch((error: unknown) => {
        messageApi.error(error instanceof ApiError ? error.message : '解析码表 Excel 失败');
      });
      return Upload.LIST_IGNORE;
    },
    showUploadList: file ? { showRemoveIcon: false } : false,
  };

  const downloadTemplate = async () => {
    try {
      downloadBlob(await templateMutation.mutateAsync(), 'DataScalpel-码表导入模板.xlsx');
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '下载模板失败');
    }
  };

  const submit = async () => {
    if (!file || !preview?.importable) return;
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
      messageApi.error(error instanceof ApiError ? error.message : '导入码表失败');
    }
  };

  return (
    <>
      {contextHolder}
      <Drawer
        title="导入树形码表"
        open={open}
        width={760}
        destroyOnHidden
        onClose={requestClose}
        footer={(
          <Space>
            <Button onClick={requestClose}>取消</Button>
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
          <Button
            icon={<DownloadOutlined />}
            loading={templateMutation.isPending}
            onClick={() => void downloadTemplate()}
          >
            下载固定模板
          </Button>
          <Upload.Dragger {...uploadProps}>
            <p className="ant-upload-drag-icon"><InboxOutlined /></p>
            <p>上传系统模板格式的 .xlsx 文件</p>
            <p className="ant-upload-hint">父节点通过同一码表内的节点编码识别，行顺序不影响树结构。</p>
          </Upload.Dragger>
          {previewMutation.isPending && <Alert showIcon type="info" title="正在解析并校验码表…" />}
          {preview && (
            <>
              <Alert
                showIcon
                type={preview.importable ? 'success' : 'error'}
                title={preview.importable ? '校验通过，可以导入' : '存在未解决问题，请修改 Excel 后重新上传'}
                description={preview.issues.join('；') || undefined}
              />
              <Table<StandardDictionaryImportDictionaryPreview>
                size="small"
                rowKey={(row) => `${row.rowNumber}-${row.code}`}
                dataSource={preview.dictionaries}
                pagination={false}
                columns={[
                  { title: '码表编码', dataIndex: 'code', width: 170, render: (value: string) => <code>{value}</code> },
                  { title: '码表名称', dataIndex: 'name', width: 180 },
                  { title: '节点数', dataIndex: 'items', width: 80, render: (items) => items.length },
                  {
                    title: '动作',
                    dataIndex: 'action',
                    width: 100,
                    render: (action: StandardDictionaryImportAction) => (
                      <Tag color={actionColor[action]}>{standardDictionaryImportActionLabels[action]}</Tag>
                    ),
                  },
                  {
                    title: '问题',
                    key: 'issues',
                    ellipsis: true,
                    render: (_value, row) => [...row.issues, ...row.items.flatMap((item) => item.issues)].join('；') || '—',
                  },
                ]}
              />
            </>
          )}
        </Space>
      </Drawer>
    </>
  );
};
