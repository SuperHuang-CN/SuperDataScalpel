import { CompactAlert as Alert } from '../../../shared/components/ContextualFeedback';
import {
  DeleteOutlined,
  DownloadOutlined,
  MoreOutlined,
  ReloadOutlined,
  SwapOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import type { MenuProps, TableProps } from 'antd';
import { Button, Dropdown, Modal, Space, Table, Tag, Tooltip, Upload, message } from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { downloadBlob } from '../../../shared/browser/downloadBlob';
import {
  useDeleteFileDatasetFile,
  useDownloadFileDatasetFile,
  useFileDatasetFiles,
  useUploadFileDatasetFiles,
} from '../hooks/useFileDatasets';
import {
  fileDatasetAccept,
  fileDatasetAllowsAdditionalUpload,
  fileDatasetCompressionLabels,
  fileDatasetFormatLabels,
  formatFileSize,
  type FileDataset,
  type FileDatasetFile,
  type FileDatasetFileStatus,
} from '../model/fileDataset';
import { ReplaceFileDatasetContentDrawer } from './ReplaceFileDatasetContentDrawer';

interface FileDatasetFilesPanelProps {
  dataset: FileDataset;
  canUpdate: boolean;
  onRefreshTables: () => void;
}

const formatDateTime = (value: string) => new Intl.DateTimeFormat('zh-CN', {
  dateStyle: 'medium', timeStyle: 'medium', hour12: false,
}).format(new Date(value));

const fileStatusLabels: Record<FileDatasetFileStatus, string> = {
  PREPARING: '准备中', READY: '已就绪',
};

const fileStatusColors: Record<FileDatasetFileStatus, string> = {
  PREPARING: 'processing', READY: 'success',
};

export const FileDatasetFilesPanel = ({
  dataset,
  canUpdate,
  onRefreshTables,
}: FileDatasetFilesPanelProps) => {
  const [selectedFiles, setSelectedFiles] = useState<File[]>([]);
  const [replacingFile, setReplacingFile] = useState<FileDatasetFile | null>(null);
  const [messageApi, messageContext] = message.useMessage();
  const [modalApi, modalContext] = Modal.useModal();
  const filesQuery = useFileDatasetFiles(dataset.id, true);
  const uploadMutation = useUploadFileDatasetFiles();
  const deleteMutation = useDeleteFileDatasetFile();
  const downloadMutation = useDownloadFileDatasetFile();
  const files = useMemo(() => filesQuery.data?.content ?? [], [filesQuery.data?.content]);
  const observedPreparation = useRef(false);
  const uploadDisabled = !fileDatasetAllowsAdditionalUpload(dataset.type, files.length);

  useEffect(() => {
    if (dataset.type !== 'GDB' && dataset.type !== 'SHP' && dataset.type !== 'GPKG') return;
    const preparing = files.some((file) => file.status === 'PREPARING');
    if (preparing) {
      observedPreparation.current = true;
    } else if (observedPreparation.current) {
      observedPreparation.current = false;
      onRefreshTables();
    }
  }, [dataset.type, files, onRefreshTables]);

  const upload = async () => {
    if (selectedFiles.length === 0) return;
    try {
      await uploadMutation.mutateAsync({ id: dataset.id, files: selectedFiles });
      messageApi.success('文件已上传并提交后台解析');
      setSelectedFiles([]);
      onRefreshTables();
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '上传文件失败');
    }
  };

  const download = async (file: FileDatasetFile) => {
    try {
      const blob = await downloadMutation.mutateAsync({ datasetId: dataset.id, fileId: file.id });
      downloadBlob(blob, file.originalFileName);
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '下载文件失败');
    }
  };

  const remove = (file: FileDatasetFile) => modalApi.confirm({
    rootClassName: 'business-overlay business-modal-overlay',
    title: '删除物理文件',
    content: `确认删除“${file.originalFileName}”及其全部来源表吗？`,
    okText: '删除',
    cancelText: '取消',
    okButtonProps: { danger: true },
    onOk: async () => {
      try {
        await deleteMutation.mutateAsync({ datasetId: dataset.id, fileId: file.id });
        messageApi.success('文件及来源表已删除');
        onRefreshTables();
      } catch (error) {
        messageApi.error(error instanceof ApiError ? error.message : '删除文件失败');
        throw error;
      }
    },
  });

  const columns: TableProps<FileDatasetFile>['columns'] = [
    { title: '文件名', dataIndex: 'originalFileName', width: 280, ellipsis: true },
    { title: '格式', dataIndex: 'format', width: 120, render: (value: FileDatasetFile['format']) => <Tag>{fileDatasetFormatLabels[value]}</Tag> },
    { title: '压缩', dataIndex: 'compression', width: 90, render: (value: FileDatasetFile['compression']) => value !== 'NONE' ? <Tag color="blue">{fileDatasetCompressionLabels[value]}</Tag> : '—' },
    { title: '大小', dataIndex: 'sizeBytes', width: 110, align: 'right', render: formatFileSize },
    {
      title: '文件状态', dataIndex: 'status', width: 105,
      render: (value: FileDatasetFileStatus) => <Tag color={fileStatusColors[value]}>{fileStatusLabels[value]}</Tag>,
    },
    { title: '上传时间', dataIndex: 'createdAt', width: 180, render: formatDateTime },
    {
      title: '操作', key: 'actions', width: 120, fixed: 'right', render: (_value: unknown, file: FileDatasetFile) => {
        const wholeFileReplacementSupported = dataset.type === 'EXCEL' || dataset.type === 'GDB' || dataset.type === 'GPKG';
        const moreItems: MenuProps['items'] = canUpdate ? [
          ...(wholeFileReplacementSupported ? [
            { key: 'replace', icon: <SwapOutlined />, label: '替换文件' },
            { key: 'delete', icon: <DeleteOutlined />, label: '删除文件', danger: true },
          ] : []),
        ] : [];
        return (
          <Space size={2}>
            <Tooltip title="下载原文件">
              <Button type="text" icon={<DownloadOutlined />} aria-label={`下载${file.originalFileName}`} onClick={() => void download(file)} />
            </Tooltip>
            {canUpdate && moreItems.length > 0 && (
              <Dropdown
                trigger={['click']}
                menu={{
                  items: moreItems,
                  onClick: ({ key }) => {
                    if (key === 'replace') setReplacingFile(file);
                    else remove(file);
                  },
                }}
              >
                <Tooltip title="更多操作"><Button type="text" icon={<MoreOutlined />} aria-label={`${file.originalFileName}更多操作`} /></Tooltip>
              </Dropdown>
            )}
          </Space>
        );
      },
    },
  ];

  return (
    <div className="file-dataset-detail-tab-panel file-dataset-files-panel">
      {messageContext}
      {modalContext}
      {uploadDisabled && (
        <Alert type="info" showIcon message={`${dataset.type === 'GDB' ? 'FileGDB' : dataset.type === 'GPKG' ? 'GeoPackage' : 'Excel'} 数据集只允许一个物理文件；需要更新内容时请使用文件行的“替换文件”。`} />
      )}
      {filesQuery.isError && (
        <Alert
          type="error"
          showIcon
          message="物理文件加载失败"
          action={<Button size="small" onClick={() => void filesQuery.refetch()}>重试</Button>}
        />
      )}
      <div className="file-dataset-detail-toolbar">
        <div className="file-dataset-upload-actions">
          {canUpdate && (
            <>
              <Upload
                accept={fileDatasetAccept(dataset.type)}
                multiple={false}
                disabled={uploadDisabled}
                showUploadList={false}
                beforeUpload={(file) => {
                  setSelectedFiles([file]);
                  return Upload.LIST_IGNORE;
                }}
              >
                <Button icon={<UploadOutlined />} disabled={uploadDisabled}>选择文件</Button>
              </Upload>
              {selectedFiles.length > 0 && (
                <>
                  <span className="file-dataset-selected-files" title={selectedFiles.map((file) => file.name).join('、')}>
                    已选择：{selectedFiles[0]?.name}
                  </span>
                  <Button type="link" onClick={() => setSelectedFiles([])}>清空</Button>
                  <Button type="primary" loading={uploadMutation.isPending} onClick={() => void upload()}>上传并解析</Button>
                </>
              )}
            </>
          )}
        </div>
        <Button
          icon={<ReloadOutlined />}
          onClick={() => {
            void filesQuery.refetch();
            onRefreshTables();
          }}
        >
          刷新
        </Button>
      </div>
      <Table<FileDatasetFile>
        size="small"
        className="file-dataset-detail-table"
        rowKey="id"
        columns={columns}
        dataSource={files}
        loading={filesQuery.isFetching}
        pagination={false}
        scroll={{ x: 1100, y: '100%' }}
        locale={{ emptyText: '尚未上传文件' }}
      />
      <ReplaceFileDatasetContentDrawer
        fileDataset={dataset}
        file={replacingFile}
        open={Boolean(replacingFile)}
        onClose={() => {
          setReplacingFile(null);
          onRefreshTables();
        }}
      />
    </div>
  );
};
